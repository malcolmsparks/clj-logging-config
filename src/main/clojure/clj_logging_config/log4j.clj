;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-logging-config.log4j
  (:use clojure.contrib.pprint
        clojure.contrib.logging)
  (:import (org.apache.log4j
            Logger ConsoleAppender EnhancedPatternLayout Level
            LogManager AppenderSkeleton Appender Layout
            SimpleLayout WriterAppender)
           (java.io OutputStream)))

(defn ^Logger get-internal-logger []
  (Logger/getLogger (name (ns-name 'clj-logging-config.log4j))))

(defn- no-internal-appenders? []
  (empty?
   (enumeration-seq (.getAllAppenders (get-internal-logger)))))

(defn- init-logging! []
  (let [logger (get-internal-logger)
        appender (proxy [WriterAppender] [(SimpleLayout.) System/out]
                   (close [] nil))]
    (.removeAllAppenders logger)
    (doto appender
      (.setImmediateFlush true))
    (.addAppender logger appender)
    (.setLevel logger Level/INFO)))

(defn- ensure-internal-logging! []
  (when (no-internal-appenders?) (init-logging!)))

(defn reset-logging! []
  (LogManager/resetConfiguration))

(defn as-map [^LoggingEvent ev]
  (assoc (bean ev) :event ev))

(defn create-appender-from-layout
  ([^Layout layout ^String name]
     (ensure-internal-logging!)
     (debug (format "Creating appender named %s from layout: %s" name layout))
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (.print System/out (.format layout ev)))
       (close [] nil)))
  ([^Layout layout]
     (create-appender-from-layout layout nil)))

(defn create-appender
  ([f ^String name]
     (ensure-internal-logging!)
     (debug (format "Creating appender named %s" name))
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (f (as-map ev)))
       (close [] nil)))
  ([f] (create-appender f nil)))

(defn create-console-appender
  ([^Layout layout ^String name]
     (ensure-internal-logging!)
     (debug (format "Creating console appender named %s" name))
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (.print System/out (.format layout ev)))
       (close [] nil)))
  ([^Layout layout]
     (create-console-appender layout nil)))

(defn wrap-appender-with-filter
  ([^Appender delegate filterfn]
     (ensure-internal-logging!)
     (debug "Creating filter appender")
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev]
               (when (filterfn (as-map ev)) (.doAppend ^Appender delegate ^LoggingEvent ev)))
       (getName [] (.getName delegate))
       (close [] (.close delegate)))))

(defn wrap-appender-with-header-and-footer
  ([^Appender delegate header footer]
     (ensure-internal-logging!)
     (debug "Creating header/footer appender")
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (.doAppend delegate ev))
       (getName [] (.getName delegate))
       (getHeader [] (if (fn? header) (header) (str header)))
       (getFooter [] (if (fn? footer) (footer) (str footer)))
       (close [] (.close delegate)))))

(defn create-layout [formatter]
  (proxy [Layout] []
    (format [ev] (formatter (as-map ev)))))

(defn as-level [level]
  (cond
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

(defn ^Logger as-logger [logger]
  (if (string? logger) (Logger/getLogger ^String logger) logger))

(defn ^{:private true}
  set-logger
  [[logger {:keys [name level out encoding pattern layout filter additivity header footer no-test]
            :or {name "_default" level :info encoding "UTF-8" test :none}
            :as args}]]
  (ensure-internal-logging!)
  (debug (format "Set logger: logger is %s, args is %s" logger args))
  (when (and layout pattern)
    (throw (IllegalStateException. "Cannot specify both :pattern and :layout")))

  (let [^Logger logger (as-logger logger)

        ^Layout actual-layout
        (cond
         (fn? layout) (create-layout layout)
         pattern (EnhancedPatternLayout. pattern)
         layout layout)

        ^Appender appender
        (cond
         (instance? Appender out) out
         (fn? out) (create-appender out name)

         (instance? OutputStream out)
         (doto (WriterAppender. actual-layout ^OutputStream out)
           (.setEncoding encoding))

         out
         (throw (IllegalStateException.
                 (format "Wrong type of appender: %s" (type out))))

         actual-layout
         (create-appender-from-layout actual-layout name)

         :otherwise
         (create-console-appender
          (if actual-layout actual-layout (SimpleLayout.))
          name))]

    (when (nil? (.getName appender))
      (debug (format "Setting name %s against appender" name))
      (.setName appender name))

    (.removeAppender logger ^String name)
    (debug (format "Adding appender named %s to logger %s" name (.getName logger)))
    (.addAppender logger ((comp (wrap-for-filter filter)
                                (wrap-for-header-or-footer header footer))
                          appender))

    ;; By default, the level is set explicitly, to ensure logging works.
    (when (not (or (nil? level) (= level :inherit)))
      (.setLevel logger (as-level level)))

    ;; Whether events should propagate up the hierarchy.
    (if additivity (.setAdditivity logger additivity))

    ;; Test the logger
    (when (not= test :none)
      (. logger log Level/ALL (format "clj-logging-config: Testing logger %s... 1..2..3.." (.getName logger))))))

(defn set-loggers! [& {:as args}]
  (doall (map set-logger args)))

(defmacro set-logger! [& [logger & args :as allargs]]
  (cond (or (empty? allargs) (keyword? logger))
        `(set-loggers! (name (ns-name ~*ns*)) ~(apply hash-map allargs))
        (string? logger)
        `(set-loggers! ~logger ~(apply hash-map args))
        :otherwise (throw (IllegalArgumentException.))))

(defn _set-logger-level! [logger level]
  (ensure-internal-logging!)
  (debug (format "Set level for %s to %s" logger level))
  (.setLevel (as-logger logger) (as-level level)))

(defmacro set-logger-level!
  ([level]
     `(set-logger-level! (name (ns-name ~*ns*)) ~level))
  ([logger level]

     `(do
        (_set-logger-level! ~logger ~level))))

(defn _set-logger-additivity! [logger value]
  (ensure-internal-logging!)
  (debug (format "Set additivity for %s to %s" logger value))
  (.setAdditivity (as-logger logger) value))

(defmacro set-logger-additivity!
  ([value]
     `(set-logger-additivity! (name (ns-name ~*ns*)) ~value))
  ([logger value]
     `(do
        (_set-logger-additivity! ~logger ~value))))

(defn get-logging-config []
  (map (fn [logger]
         (-> logger
             (update-in [:parent] (fn [^Logger parent] (.getName parent)))
             (dissoc :loggerRepository)
             (dissoc :hierarchy)
             (update-in [:chainedPriority] str)
             (update-in [:level] str)
             (update-in [:effectiveLevel] str)
             (update-in [:allAppenders] enumeration-seq)
             (update-in [:priority] str))) (sort-by :name (map bean (enumeration-seq (LogManager/getCurrentLoggers))))))

(defn set-internal-logging-level! [level]
  (ensure-internal-logging!)
  (.setLevel (get-internal-logger) (as-level level))
  (debug (format "Set internal logging level to %s" level)))

