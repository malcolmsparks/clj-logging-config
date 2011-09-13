;; clj-logging-config - Logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;; be found in the file epl-v10.html at the root of this distribution.  By using
;; this software in any fashion, you are agreeing to be bound by the terms of
;; this license.  You must not remove this notice, or any other, from this
;; software.

(ns clj-logging-config.log4j
  (:use clojure.contrib.pprint
        clojure.tools.logging)
  (:require [clojure.java.io :as io])
  (:import (org.apache.log4j
            Logger ConsoleAppender EnhancedPatternLayout Level
            LogManager AppenderSkeleton Appender Layout Hierarchy
            SimpleLayout WriterAppender FileAppender NDC MDC)
           (org.apache.log4j.spi
            RepositorySelector DefaultRepositorySelector RootLogger)
           (java.io OutputStream Writer File)))

(defn ^Logger get-config-logger []
  (Logger/getLogger (name (ns-name 'clj-logging-config.log4j))))

(defn- no-internal-appenders? []
  (empty?
   (enumeration-seq (.getAllAppenders (get-config-logger)))))

(defn- init-logging! []
  (let [logger (get-config-logger)
        appender (proxy [WriterAppender] [(SimpleLayout.) System/out]
                   (close [] nil))]
    (.removeAllAppenders logger)
    (doto appender
      (.setImmediateFlush true))
    (.setAdditivity logger false)
    (.addAppender logger appender)
    (.setLevel logger Level/INFO)))

(defn- ensure-config-logging! []
  (when (no-internal-appenders?) (init-logging!)))

(defn reset-logging! []
  (LogManager/resetConfiguration))

(defn as-map [^LoggingEvent ev]
  (assoc (bean ev) :event ev))

(defn create-appender-adapter
  ([f ^String name]
     (ensure-config-logging!)
     (logf :debug "Creating appender named %s" name)
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (f (as-map ev)))
       (close [] nil)))
  ([f] (create-appender-adapter f nil)))

(defn create-console-appender
  ([^Layout layout ^String name]
     (ensure-config-logging!)
     (logf :debug "Creating console appender named %s and layout %s" name layout)
     (proxy [WriterAppender] [layout System/out]
       (close [] nil)))
  ([^Layout layout]
     (create-console-appender layout "_default")))

(defn wrap-appender-with-filter
  ([^Appender delegate filterfn]
     (ensure-config-logging!)
     (debug "Creating filter appender")
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev]
               (when (filterfn (as-map ev)) (.doAppend ^Appender delegate ^LoggingEvent ev)))
       (getName [] (.getName delegate))
       (close [] (.close delegate)))))

(defn wrap-appender-with-header-and-footer
  ([^Appender delegate header footer]
     (ensure-config-logging!)
     (debug "Creating header/footer appender")
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (.doAppend delegate ev))
       (getName [] (.getName delegate))
       (getHeader [] (if (fn? header) (header) (str header)))
       (getFooter [] (if (fn? footer) (footer) (str footer)))
       (close [] (.close delegate)))))

(defn create-layout-adaptor [f]
  (proxy [Layout] []
    (format [ev] (f (as-map ev)))))

(defn as-level [level]
  (cond
   (nil? level) nil
   (= :inherit level) nil
   (keyword? level) (get {:all Level/ALL
                          :debug Level/DEBUG
                          :error Level/ERROR
                          :fatal Level/FATAL
                          :info Level/INFO
                          :off Level/OFF
                          :trace Level/TRACE
                          :warn Level/WARN} level)
   (instance? Level level) level))

(defn wrap-for-filter [filterfn]
  (fn [appender]
    (if filterfn
      (wrap-appender-with-filter appender filterfn)
      appender)))

(defn wrap-for-header-or-footer [header footer]
  (fn [appender]
    (if (or header footer)
      (wrap-appender-with-header-and-footer appender header footer)
      appender)))

(defn ^Logger as-logger
  "If a string is given, lookup the Logger, else return the parameter as-is."
  [logger]
  (cond (string? logger) (Logger/getLogger ^String logger)
        (contains? #{:root ""} logger) (Logger/getRootLogger)
        (= :config logger) (get-config-logger)
        (instance? clojure.lang.Namespace logger) (str logger)
        (instance? org.apache.log4j.Logger logger) logger
        :otherwise (throw (Exception. (format "Cannot be coerced into a logger: %s" logger)))))

(defn- as-loggers
  "Functions that accept a logger name (or logger) can also take multiples as a
list. This returns a list of actual loggers. A single logger will result in a
list with one entry."
  [logger]
  (if (coll? logger)
    (map as-logger logger)
    (list (as-logger logger))))

(defn as-layout [layout]
  (if (nil? layout) (SimpleLayout.) layout))

(defn ensure-appender
  ([logger leaf-logger]
     (when (empty? (enumeration-seq (.getAllAppenders logger)))
       (let [parent (.getParent logger)]
         (if (and parent (.getAdditivity logger))
           (ensure-appender parent leaf-logger)
           (do
             (ensure-config-logging!)
             (logf :debug "Must create an appender at %s because otherwise no logging would be emitted for %s" (.getName logger) (.getName leaf-logger))
             (.addAppender logger (create-console-appender (SimpleLayout.))))))))
  ([logger] (ensure-appender logger logger)))

(defn
  set-logger
  "Sets logging configuration for a logger, or list of loggers. Returns nil."
  [[logger {:keys [name level out encoding pattern layout filter additivity header footer test]
            :or {name "_default" level :info encoding "UTF-8" test true}
            :as args}]]

  (ensure-config-logging!)
  (logf :debug "Set logger: logger is %s, args is %s" logger args)

  (when (and layout pattern)
    (throw (IllegalStateException. "Cannot specify both :pattern and :layout")))

  (let [^Layout actual-layout
        (cond
         (fn? layout) (create-layout-adaptor layout)
         pattern (do
                   (logf :debug "Creating enhanced pattern layout with pattern: %s" pattern)
                   (EnhancedPatternLayout. pattern))
         layout layout)

        ;; Try to infer whether an appender is required for this logger
        ;; If the out parameter is given, or a layout is given, then an appender
        ;; needs to be added.
        ^Appender appender
        (cond
         (instance? Appender out) out
         (fn? out) (create-appender-adapter out name)

         (instance? OutputStream out)
         (doto (WriterAppender. (as-layout actual-layout) ^OutputStream out)
           (.setEncoding encoding))

         (instance? Writer out)
         (doto (WriterAppender. (as-layout actual-layout) ^Writer out)
           (.setEncoding encoding))

         (instance? File out)
         (doto (WriterAppender. (as-layout actual-layout) ^Writer (java.io.FileWriter. out))
           (.setEncoding encoding))

         (instance? String out)
         (doto (WriterAppender. (as-layout actual-layout) ^Writer (java.io.FileWriter. (io/file out)))
           (.setEncoding encoding))

         (or actual-layout (= out :console))
         (create-console-appender (as-layout actual-layout) name)

         out
         (throw (IllegalStateException.
                 (format "Wrong type of appender: %s" (type out)))))]

    (when appender
      (when (nil? (.getName appender))
        (logf :debug "Setting name %s against appender" name)
        (.setName appender name)))

    (doall (for [^Logger logger (as-loggers logger)]
             (do
               (when appender
                 (.removeAppender logger ^String name)
                 (logf :debug "Adding appender named %s to logger %s" name (.getName logger))
                 (.addAppender logger ((comp (wrap-for-filter filter)
                                             (wrap-for-header-or-footer header footer))
                                       appender)))

               (logf :debug "Setting level to %s" level)
               (.setLevel logger (as-level level))

               ;; Whether events should propagate up the hierarchy.
               (let [actual-additivity (cond (not (nil? additivity)) additivity
                                             (not (nil? appender)) false)]
                 (when (not (nil? actual-additivity))
                   (logf :debug "Setting additivity to %s" actual-additivity)
                   (.setAdditivity logger actual-additivity)))

               ;; Ensure an appender exists for this logger
               (ensure-appender logger)

               ;; Test the logger
               (when (true? test)
                 (. logger log Level/ALL (format "clj-logging-config: Testing logger %s... 1..2..3.." (.getName logger)))))))))

(defn set-loggers! [& args]
  (assert (even? (count args)))
  (doall (map set-logger (partition 2 args))))

(defmacro set-logger! [& [logger & args :as allargs]]
  (cond (or (empty? allargs) (keyword? logger))
        `(set-loggers! (name (ns-name ~*ns*)) ~(apply hash-map allargs))
        :otherwise
        `(set-loggers! ~logger ~(apply hash-map args))))

(defn _set-logger-level! [logger level]
  (ensure-config-logging!)
  (for [logger (as-loggers logger)]
    (do
      (logf :debug "Set level for %s to %s" logger level)
      (.setLevel logger (as-level level)))))

(defmacro set-logger-level!
  ([level]
     `(set-logger-level! (name (ns-name ~*ns*)) ~level))
  ([logger level]
     `(do
        (_set-logger-level! ~logger ~level))))

(defn _set-logger-additivity! [logger value]
  (ensure-config-logging!)
  (for [logger (as-loggers logger)]
    (do
      (logf :debug "Set additivity for %s to %s" logger value)
      (.setAdditivity logger value))))

(defmacro set-logger-additivity!
  ([value]
     `(set-logger-additivity! (name (ns-name ~*ns*)) ~value))
  ([logger value]
     `(do
        (_set-logger-additivity! ~logger ~value))))

(defn safe-bean [x]
  (if (not (nil? x)) (bean x) x))

(defn- stringify-appenders [appenders]
  (map (fn [appender]
         (-> appender
             (update-in [:layout] safe-bean)
             (update-in [:errorHandler] safe-bean)
             (update-in [:filter] safe-bean)))
       (->> appenders
            enumeration-seq
            (map bean))))

(defn get-logging-config []
  {:repository (bean (LogManager/getLoggerRepository))
   :loggers
   (map (fn [logger]
          (-> logger
              (update-in [:parent] (fn [^Logger parent] (.getName parent)))
              (dissoc :loggerRepository)
              (dissoc :hierarchy)
              (update-in [:chainedPriority] str)
              (update-in [:level] str)
              (update-in [:effectiveLevel] str)
              (update-in [:allAppenders] stringify-appenders)
              (update-in [:priority] str))) (sort-by :name (map bean (enumeration-seq (LogManager/getCurrentLoggers)))))})

(defn set-config-logging-level! [level]
  (ensure-config-logging!)
  (.setLevel (get-config-logger) (as-level level))
  (logf :debug "Set config logging level to %s" level))

;; Thread-local logging support

(defn ^Logger as-logger*
  "If a string is given, lookup the Logger, else return the parameter as-is."
  [h [logger m]]
  [(cond (string? logger) (.getLogger h ^String logger)
         (contains? #{:root ""} logger) (.getRootLogger h)
         (= :config logger) (.getLogger h (name (ns-name 'clj-logging-config.log4j)))
         (instance? clojure.lang.Namespace logger) (str logger)
         :otherwise (throw (Exception. (format "Cannot be coerced into a logger: %s" logger)))) m])

(defmacro with-logging-config [config & body]
  `(let [old-log-factory# *log-factory*
         thread-root-logger# (org.apache.log4j.spi.RootLogger. org.apache.log4j.Level/DEBUG)
         thread-repo# (org.apache.log4j.Hierarchy. thread-root-logger#)]
     (doall (map set-logger (map (partial as-logger* thread-repo#) (partition 2 ~config))))
     (binding [*log-factory*
               (reify LogFactory
                      (impl-name [_] "clj-logging-config.thread-local-logging")
                      (impl-get-log [_ log-ns#]
                                    (let [logger# (.getLogger thread-repo# ^String (str log-ns#))
                                          levels# {:trace org.apache.log4j.Level/TRACE
                                                   :debug org.apache.log4j.Level/DEBUG
                                                   :info  org.apache.log4j.Level/INFO
                                                   :warn  org.apache.log4j.Level/WARN
                                                   :error org.apache.log4j.Level/ERROR
                                                   :fatal org.apache.log4j.Level/FATAL}]
                                      (reify Log
                                             (impl-enabled? [log# level#]
                                                            (let [l# (or (levels# level#)
                                                                         (throw (IllegalArgumentException. (str level#))))]
                                                              (or
                                                               (.isEnabledFor (impl-get-log old-log-factory# log-ns#) l#)
                                                               (.isEnabledFor logger# l#))))
                                             (impl-write! [log# level# throwable# message#]
                                                          ;; Write the message to the original logger on the thread
                                                          (when-let [orig-logger# (impl-get-log old-log-factory# log-ns#)]
                                                            (impl-write! orig-logger# level# throwable# message#))
                                                          ;; Write the message to our new logger
                                                          (let [l# (or
                                                                    (levels# level#)
                                                                    (throw (IllegalArgumentException. (str level#))))]
                                                            (if-not throwable#
                                                              (.log logger# l# message#)
                                                              (.log logger# l# message# throwable#))))))))]
       ~@body)))

(defmacro with-logging-context [x & body]
  `(let [x# ~x]
     (try
       (if (map? x#)
         (doall (map (fn [[k# v#]] (. ~MDC put (name k#) v#)) x#))
         (. ~NDC push (str x#)))
       ~@body
       (finally
        (if (map? x#)
          (doall (map (fn [[k# v#]] (. ~MDC remove (name k#))) x#))
          (. ~NDC pop))))))


