;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-logging-config.jul
  (:import (java.util.logging
            Logger Level LogManager Handler LogRecord
            Formatter SimpleFormatter StreamHandler)
           (java.io OutputStream)))

(set! *warn-on-reflection* true)

(defn reset-logging []
  (.reset (LogManager/getLogManager)))

(defn as-map [^LogRecord rec]
  (assoc (bean rec) :log-record rec))

(defn create-handler [f]
  (proxy [Handler] []
    (.publish [^LogRecord rec] (f (as-map rec)))
    (close [] nil)))

(defn create-formatter [formatfn]
  (proxy [Formatter] []
    (format [ev] (formatfn ev))))

(defn create-repl-handler
  [^Formatter formatter]
  (proxy [Handler] []
    (publish [^LogRecord rec] (print (.format formatter rec)))
    (close [] nil)))

(defn wrap-handler-with-filter
  [^Handler delegate filterfn]
  (proxy [Handler] []
    (publish [^LogRecord rec]
             (when (filterfn (as-map rec)) (.publish delegate rec)))
    (close [] (.close delegate))))

(defn as-level [level]
  (cond
   (keyword? level) (get {:all Level/ALL
                          :config Level/CONFIG
                          :fine Level/FINE
                          :finer Level/FINER
                          :finest Level/FINEST
                          :info Level/INFO
                          :off Level/OFF
                          :severe Level/SEVERE
                          :warning Level/WARNING} level)
   (instance? Level level) level))

(defn wrap-for-filter [filterfn]
  (fn [handler]
    (if filterfn
      (wrap-handler-with-filter handler filterfn)
      handler)))

(defn ensure-handler [^Logger logger]
  (if (empty? (seq (.getHandlers logger)))
    (if (or (= "" (.getName logger))
            (false? (.getUseParentHandlers logger)))
      (.addHandler logger (create-repl-handler (SimpleFormatter.)))
      (recur (.getParent logger)))))

(defn ^{:private true}
  set-logger
  [[logger {:keys [level out formatter filter use-parent-handlers]}]]

  (let [logger (Logger/getLogger ^String logger)

        ^Formatter actual-formatter
        (cond
         (fn? formatter) (create-formatter formatter)
         formatter formatter)

        ^Handler handler
        (cond
         (instance? Handler out) out
         (fn? out) (create-handler out)
         (instance? OutputStream out) (StreamHandler. out actual-formatter)

         out
         (throw (IllegalStateException.
                 (format "Wrong type of handler: %s" (type out))))

         (= out :repl)
         (create-repl-handler
          (if actual-formatter actual-formatter (SimpleFormatter.))))]

    (if handler
      (doto logger
        (.addHandler ((wrap-for-filter filter) handler))
        (.setLevel (or (as-level level) Level/INFO))))

    (if use-parent-handlers (.setUseParentHandlers logger use-parent-handlers))
    (if level (.setLevel logger level))
    (ensure-handler logger)))

;; To grok this, see http://briancarper.net/blog/579/keyword-arguments-ruby-clojure-common-lisp
(defn set-loggers! [& {:as args}]
  (doall (map set-logger args)))

(defmacro set-logger! [& args]
  (cond (or (keyword? (first args)) (empty? args))
        `(set-loggers! (name (ns-name *ns*)) ~(apply hash-map args))
        (string? (first args))
        `(set-loggers! ~(first args) ~(apply hash-map (if args (rest args) {})))
        :otherwise (throw (IllegalArgumentException.))))
