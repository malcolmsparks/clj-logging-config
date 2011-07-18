;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-logging-config.log4j
  (:import (org.apache.log4j Logger ConsoleAppender EnhancedPatternLayout Level
                             LogManager AppenderSkeleton Appender Layout
                             SimpleLayout WriterAppender)))

(defn reset-logging []
  (LogManager/resetConfiguration))

(defn as-map [^LoggingEvent ev]
  (assoc (bean ev) :event ev))

(defn create-formatter-from-layout
  ([^Layout layout ^String name]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (println (.format layout ev)))
       (getName [] name)
       (close [] nil)))
  ([^Layout layout]
     (create-formatter-from-layout layout nil)))

(defn create-appender
  ([f ^String name]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (f (as-map ev)))
       (getName [] name)
       (close [] nil)))
  ([f] (create-appender f nil)))

(defn create-repl-appender
  ([^Layout layout ^String name]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (print (.format layout ev)))
       (getName [] name)
       (close [] nil)))
  ([^Layout layout]
     (create-repl-appender layout nil)))

(defn wrap-appender-with-filter
  ([^Appender delegate filterfn]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev]
               (when (filterfn (as-map ev)) (.append delegate ev)))
       (getName [] (.getName delegate))
       (close [] (.close delegate)))))

(defn wrap-appender-with-header-and-footer
  ([^Appender delegate header footer]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (.append delegate ev))
       (getName [] (.getName delegate))
       (getHeader [] (if (fn? header) (header) (str header)))
       (getFooter [] (if (fn? footer) (footer) (str footer)))
       (close [] (.close delegate)))))

(defn create-layout [formatter]
  (proxy [Layout] []
    (format [ev] (formatter (as-map ev)))))

(defn as-level [level]
  (cond
   (keyword? level) (get {:debug Level/DEBUG
                          :info Level/INFO
                          :warn Level/WARN
                          :error Level/ERROR
                          :fatal Level/FATAL
                          :off Level/OFF
                          :trace Level/TRACE
                          :all Level/ALL} level)
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

(defn ^{:private true}
  set-logger
  [[logger {:keys [name level writer pattern layout filter additivity header footer]
            :or {name "_default" additivity true}}]]
  (when (and layout pattern)
    (throw (IllegalStateException. "Cannot specify both :pattern and :layout")))

  (let [logger (if (string? logger) (Logger/getLogger ^String logger) logger) 

        ^Layout actual-layout
        (cond
         (fn? layout) (create-layout layout)
         pattern (EnhancedPatternLayout. pattern)
         layout layout
         :otherwise nil)

        ^Appender appender
        (cond
         (instance? Appender writer) writer
         (fn? writer) (create-appender writer name)
         (instance? java.io.Writer writer) (WriterAppender. actual-layout writer)
         writer (throw (IllegalStateException. (format "Wrong type of appender: %s" (type writer))))
         actual-layout (create-repl-appender actual-layout name)
         :otherwise (create-repl-appender (SimpleLayout.) name))]

    (if (nil? (.getName appender))
      (.setName appender name))

    (doto logger
      (.removeAppender name)
      (.setAdditivity additivity)
      (.addAppender ((comp (wrap-for-filter filter) (wrap-for-header-or-footer header footer)) appender))
      (.setLevel (or (as-level level) Level/INFO)))))

;; To grok this, see http://briancarper.net/blog/579/keyword-arguments-ruby-clojure-common-lisp
(defn set-loggers! [& {:as args}]
  (doall (map set-logger args)))

(defmacro set-logger! [& args]
  (cond (or (keyword? (first args)) (empty? args))
        `(set-loggers! (name (ns-name *ns*)) ~(apply hash-map args))
        (string? (first args))
        `(set-loggers! ~(first args) ~(apply hash-map (if args (rest args) {})))
        :otherwise (throw (IllegalArgumentException.))))
