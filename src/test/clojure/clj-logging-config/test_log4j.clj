;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-logging-config.test-log4j
  (:import (org.apache.log4j PatternLayout Logger Level))
  (:use clojure.test
        clojure.contrib.logging
        clj-logging-config.log4j
        clojure.contrib.with-ns))

(defmacro actual [& body]
  `(with-out-str
     (with-ns (create-ns (symbol "test"))
       (clojure.core/refer-clojure)
       (use 'clojure.contrib.logging)
       ~@body)))

;; TODO: Investigate why logging causes two LFs
(defn expected [s] (if (= s "") "" (str s "\n\n")))

(use-fixtures :each (fn [f] (reset-logging)
                      (f)
                      (reset-logging)))

(deftest test-logging
  (logger "test")
  (are [e a] (= (expected e) (actual a))

       "INFO - Here is a log message"
       (info "Here is a log message")

       "WARN - Here is a warning"
       (warn "Here is a warning")

       ""
       (debug "Debug messages are hidden by default")))

(deftest test-logging-debug
  (logger "test" :level Level/DEBUG)
  (are [e a] (= (expected e) (actual a))

       "INFO - Here is a log message"
       (info "Here is a log message")

       "DEBUG - Debug level messages are now shown"
       (debug "Debug level messages are now shown")))

