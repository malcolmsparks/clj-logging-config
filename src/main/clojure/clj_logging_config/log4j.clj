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

(defn wrap-appender-with-filter [^Appender delegate filterfn]
  (proxy [AppenderSkeleton] []
    (append [^LoggingEvent ev]
            (when (filterfn ev) (.append delegate ev)))
    (close [] (.close delegate))))

(defn create-formatter-from-layout [^Layout layout]
  (proxy [AppenderSkeleton] []
    (append [^LoggingEvent ev]
            (println (.format layout ev)))
    (close [] nil)))

(defn create-repl-appender [^Layout layout]
  (proxy [AppenderSkeleton] []
    (append [^LoggingEvent ev]
            (println (.format layout ev)))
    (close [] nil)))

(defn create-appender [f]
  (proxy [AppenderSkeleton] []
    (append [^LoggingEvent ev]
            (f ev))
    (close [] nil)))

(defn create-layout [formatter]
  (proxy [Layout] []
    (format [ev] (formatter ev))))

;; To grok this, see http://briancarper.net/blog/579/keyword-arguments-ruby-clojure-common-lisp
(defn loggers [& {:as args}]
  (doall
   (map
    (fn [[name {:keys [level appender pattern layout filter]}]]
      (let [logger (Logger/getLogger ^String name)
            ^Layout actual-layout (cond
                                   (and appender (or layout pattern))
                                   (throw (Exception. "Cannot specify an :appender and one of :pattern and :layout"))

                                   (and layout pattern)
                                   (throw (Exception. "Cannot specify both :pattern and :layout"))

                                   (fn? layout) (create-layout layout)
                                   pattern (PatternLayout. pattern)
                                   layout layout)
            actual-appender (cond
                             (fn? appender) (create-appender appender)
                             appender appender
                             actual-layout (create-repl-appender actual-layout)
                             :otherwise (create-repl-appender (SimpleLayout.)))]
        (doto logger
          (.addAppender (if filter (wrap-appender-with-filter actual-appender filter) actual-appender))
          (.setLevel (or (cond
                          (keyword? level) (get {:debug Level/DEBUG
                                                 :info Level/INFO
                                                 :warn Level/WARN
                                                 :error Level/ERROR
                                                 :fatal Level/FATAL
                                                 :off Level/OFF
                                                 :trace Level/TRACE
                                                 :all Level/ALL} level)
                          (instance? Level level) level) Level/INFO)))))
    args)))

(defmacro logger [& args]
  (cond (or (keyword? (first args)) (empty? args))
        `(loggers (name (ns-name *ns*)) ~(apply hash-map args))
        (string? (first args))
        `(loggers ~(first args) ~(apply hash-map (if args (rest args) {})))
        :otherwise (throw (IllegalArgumentException.))))



