;; clj-logging-config - Logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;; be found in the file epl-v10.html at the root of this distribution.  By using
;; this software in any fashion, you are agreeing to be bound by the terms of
;; this license.  You must not remove this notice, or any other, from this
;; software.

(ns clj-logging-config.jul
  (:use clojure.tools.logging)
  (:import (java.util.logging
            Logger Level LogManager Handler LogRecord
            Formatter SimpleFormatter ConsoleHandler FileHandler StreamHandler)
           (java.io OutputStream)))

(set! *warn-on-reflection* true)

(defn ^Logger get-internal-logger []
  (Logger/getLogger (name (ns-name 'clj-logging-config.jul))))

(defn- no-internal-handlers? []
  (empty?
   (seq (.getHandlers (get-internal-logger)))))

(defn- init-logging! []
  (let [logger (get-internal-logger)
        handler (ConsoleHandler.)]
    (.setLevel handler Level/ALL)
    (for [h (.getHandlers logger)]
      (.removeHandler logger h))
    (.addHandler logger handler)
    (.setLevel logger Level/FINE)))

(defn- ensure-internal-logging! []
  (when (no-internal-handlers?) (init-logging!)))

(defn reset-logging! []
  (.reset (LogManager/getLogManager)))

(defn as-map [^LogRecord rec]
  (assoc (bean rec) :log-record rec))

(defn create-handler [f]
  (proxy [Handler] []
    (publish [^LogRecord rec] (f (as-map rec)))
    (close [] nil)))

(defn create-formatter [formatfn]
  (proxy [Formatter] []
    (format [ev] (formatfn ev))))

(defn create-console-handler
  [^Formatter formatter]
  (doto
      (ConsoleHandler.)
    (.setFormatter formatter)
    (.setLevel Level/ALL)))

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
                          ;; this debug mapping is added by clojure.tools.logging
                          :debug Level/FINE
                          :finer Level/FINER
                          :finest Level/FINEST
                          :info Level/INFO
                          :off Level/OFF
                          :severe Level/SEVERE
                          :warning Level/WARNING
                          :warn Level/WARNING} level)
   (instance? Level level) level))

(defn wrap-for-filter [filterfn]
  (fn [handler]
    (if filterfn
      (wrap-handler-with-filter handler filterfn)
      handler)))

(defn ^Logger as-logger [logger]
  (assert (not (nil? logger)))
  (if (string? logger) (Logger/getLogger ^String logger) logger))

(defn ^{:private true}
  set-logger
  [[logger {:keys [level out encoding formatter filter use-parent-handlers test]
            :or {level :info encoding "UTF-8" test true}
            :as args}]]

  (ensure-internal-logging!)
  (debug (format "Set logger: logger is %s, args is %s" logger args))

  (let [^Logger logger (as-logger logger)

        ^Formatter actual-formatter
        (cond
         (fn? formatter) (create-formatter formatter)
         formatter formatter
         :otherwise (proxy [Formatter] []
                      (format [^LogRecord lr] (format "[%s] %s\n" (str (.getLevel lr)) (.getMessage lr)))))

        ^Handler handler
        (cond
         (string? out) (doto (FileHandler. out)
                         (.setFormatter actual-formatter)) ; j.u.l pattern
         (instance? Handler out) out
         (fn? out) (create-handler out)
         (instance? OutputStream out) (proxy [StreamHandler] [^OutputStream out ^Formatter actual-formatter]
                                        (publish [^LogRecord lr]
                                                 (.publish ^StreamHandler this lr)
                                                 (.flush ^StreamHandler this)))

         out
         (throw (IllegalStateException.
                 (format "Wrong type of handler: %s" (type out))))

         :otherwise
         (create-console-handler
          (if actual-formatter actual-formatter (SimpleFormatter.))))]

    (doseq [h (.getHandlers logger)]
      (debug (format "Removing handler from logger %s" (.getName logger)))
      (.removeHandler logger h))

    (debug (format "Adding handler to logger %s" (.getName logger)))
    (.addHandler logger ((wrap-for-filter filter) handler))

    ;; By default, the level is set explicitly, to ensure logging works.
    (when (not (or (nil? level) (= level :inherit)))
      (.setLevel logger (as-level level)))

    ;; Whether events should propagate up the hierarchy.
    (if use-parent-handlers (.setUseParentHandlers logger use-parent-handlers))

    ;; Test the logger
    (when (true? test)
      (. logger log Level/ALL (format "clj-logging-config: Testing logger %s... 1..2..3.." (.getName logger))))))

(defn set-loggers! [& {:as args}]
  (doall (map set-logger args)))

(defmacro set-logger! [& args]
  (cond (or (keyword? (first args)) (empty? args))
        `(set-loggers! (name (ns-name *ns*)) ~(apply hash-map args))
        (string? (first args))
        `(set-loggers! ~(first args) ~(apply hash-map (if args (rest args) {})))
        :otherwise (throw (IllegalArgumentException.))))

(defn _set-logger-level! [logger level]
  (ensure-internal-logging!)
  (debug (format "Set level for %s to %s" logger level))
  (.setLevel (as-logger logger) (as-level level)))

(defmacro set-logger-level!
  ([level]
     `(_set-logger-level! (name (ns-name *ns*)) ~level))
  ([logger level]
     `(do
        (_set-logger-level! ~logger ~level))))

(defn _set-logger-use-parent-handlers! [logger value]
  (ensure-internal-logging!)
  (debug (format "Set use-parent-handlers for %s to %s" logger value))
  (.setUseParentHandlers (as-logger logger) value))

(defmacro set-logger-use-parent-handlers!
  ([value]
     `(_set-logger-use-parent-handlers! (name (ns-name *ns*)) ~value))
  ([logger value]
     `(do
        (_set-logger-use-parent-handlers! ~logger ~value))))

(defn get-logging-config []
  (letfn [(as-map [o] (if o (bean o) nil))]
    (let [lm (LogManager/getLogManager)]
      (for [name (enumeration-seq (. lm getLoggerNames))]
        (if-let [logger (Logger/getLogger name)]
          {:name name
           :logger logger
           :level (str (. logger getLevel))
           :filter (when-let [ft (. logger getFilter)] (as-map ft))
           :parent (when-let [p (. logger getParent)] (.getName p))
           :parent-logger (. logger getParent)
           :resource-bundle (. logger getResourceBundle)
           :resource-bundle-name (. logger getResourceBundleName)
           :use-parent-handlers (. logger getUseParentHandlers)
           :handlers (->> (map as-map (. logger getHandlers))
                          (map #(update-in % [:formatter] as-map))
                          (map #(update-in % [:errorManager] as-map))
                          (map #(update-in % [:level] str)))}
          {:name name})))))

(defn set-internal-logging-level! [level]
  (ensure-internal-logging!)
  (.setLevel (get-internal-logger) (as-level level))
  (debug (format "Set internal logging level to %s" level)))
