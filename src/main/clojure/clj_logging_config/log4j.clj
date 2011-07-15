;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-logging-config.log4j
  (:import (org.apache.log4j Logger ConsoleAppender PatternLayout Level
                             LogManager AppenderSkeleton Appender Layout
                             SimpleLayout)))

(defn reset-logging []
  (LogManager/resetConfiguration))

(defn as-map [^LoggingEvent ev]
  (assoc (bean ev) :event ev))

(defn wrap-appender-with-filter
  ([^Appender delegate filterfn ^String name]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev]
               (when (filterfn (as-map ev)) (.append delegate ev)))
       (getName [] name)
       (close [] (.close delegate))))
  ([^Appender delegate filterfn]
     (wrap-appender-with-filter delegate filterfn nil)))

(defn create-formatter-from-layout
  ([^Layout layout ^String name]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (println (.format layout ev)))
       (getName [] name)
       (close [] nil)))
  ([^Layout layout]
     (create-formatter-from-layout layout nil)))

(defn create-repl-appender
  ([^Layout layout ^String name]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (print (.format layout ev)))
       (getName [] name)
       (close [] nil)))
  ([^Layout layout]
     (create-repl-appender layout nil)))

(defn create-appender
  ([f ^String name]
     (proxy [AppenderSkeleton] []
       (append [^LoggingEvent ev] (f (as-map ev)))
       (getName [] name)
       (close [] nil)))
  ([f] (create-appender f nil)))

(defn create-layout [formatter]
  (proxy [Layout] []
    (format [ev] (formatter ev))))

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

(defn ^{:private true}
  set-logger
  [[logger {:keys [appender-name level appender pattern layout filter additivity]
            :or {appender-name "_default" additivity true}}]]
  (cond (and appender (or layout pattern))
        (throw (IllegalStateException. "Cannot specify an :appender and one of :pattern and :layout"))
        (and layout pattern)
        (throw (IllegalStateException. "Cannot specify both :pattern and :layout")))

  (let [logger (Logger/getLogger ^String logger)

        ^Layout actual-layout (cond
                               (fn? layout) (create-layout layout)
                               pattern (PatternLayout. pattern)
                               layout layout)
        ^Appender appender-delegate (cond
                                     (fn? appender) (create-appender appender appender-name)
                                     appender appender
                                     actual-layout (create-repl-appender actual-layout appender-name)
                                     :otherwise (create-repl-appender (SimpleLayout.) appender-name))
        ^Appender final-appender (cond
                                  filter (wrap-appender-with-filter appender-delegate filter appender-name)
                                  :otherwise appender-delegate)]
    (if (nil? (.getName final-appender))
      (.setName final-appender appender-name))
    (doto logger
      (.removeAppender appender-name)
      (.setAdditivity additivity)
      (.addAppender final-appender)
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
