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
  (:import (org.apache.log4j PatternLayout Logger Level SimpleLayout RollingFileAppender))
  (:use clojure.test
        clojure.contrib.logging
        clj-logging-config.log4j
        clojure.contrib.with-ns)
  (:require [clojure.java.io :as io]))

(defmacro dolog [& body]
  `(with-ns (create-ns (symbol "test"))
     (clojure.core/refer-clojure)
     (use 'clojure.contrib.logging)
     ~@body))

(defmacro dotest [& body]
  `(are [actual expected]
        (= expected (with-out-str (dolog actual)))
        ~@body))

(deftest test-logging
  (testing "Default logging"
    (set-logger! "test")
    (dotest
     (info "Here is a log message") "INFO - Here is a log message\n"
     (warn "Here is a warning") "WARN - Here is a warning\n"
     (debug "Debug messages are hidden by default") ""))

  (testing "Logging at the DEBUG level"
    (set-logger! "test" :level Level/DEBUG)
    (dotest
     (info "Here is a log message") "INFO - Here is a log message\n"
     (debug "Debug level messages are now shown") "DEBUG - Debug level messages are now shown\n"))

  (testing "Levels can also be specified with keywords"
    (set-logger! "test" :level :warn)
    (dotest
     (debug "Debug messages are hidden") ""
     (info "So are log messages") ""
     (warn "Only warnings") "WARN - Only warnings\n"
     (error "And errors") "ERROR - And errors\n"))

  (testing "Setting a pattern for the PatternLayout"
    (set-logger! "test" :pattern PatternLayout/DEFAULT_CONVERSION_PATTERN)
    (dotest
     (info "Here is a log message") "Here is a log message\n"))

  (testing "Setting a custom pattern for the PatternLayout"
    (set-logger! "test" :pattern "[%p] - %m")
    (dotest
     (info "Here is a log message") "[INFO] - Here is a log message"))

  (testing "Setting a custom layout"
    (set-logger! "test" :layout (SimpleLayout.))
    (dotest
     (info "Here is a log message") "INFO - Here is a log message\n"))

  (testing "We can even use a Clojure function as a layout"
    (set-logger! "test" :layout (fn [^org.apache.log4j.spi.LoggingEvent ev]
                             (format "%s: %s" (.getLevel ev) (.getMessage ev))))
    (dotest
     (info "Try doing this in log4j.properties!") "INFO: Try doing this in log4j.properties!"))

  (testing "But we can't set a :layout and a :pattern (because a :pattern implies a org.apache.log4j.PatternLayout)"
    (is (thrown? Exception (set-logger! "test" :pattern "%m" :layout (SimpleLayout.)))))

  ;; One of the advantages of hosting Clojure on the JVM is that you can (and
  ;; should) make use of functionality that already exists rather than
  ;; re-implementing it in Clojure.
  (testing "Setting an appender"
    (set-logger! "test" :appender (RollingFileAppender.)))

  ;; But sometimes we want to quickly implement our own custom appender in Clojure
  ;; which is painful to do in Java. This example uses println for testing
  ;; purposes but there's no reason it couldn't do something more complex (like
  ;; send a tweet).
  (testing "Set a custom appender in Clojure"
    (let [out (java.io.StringWriter.)]
      (binding [*out* out]
        (set-logger! "test"
                :appender (fn [^org.apache.log4j.spi.LoggingEvent ev]
                            (println (format ">>> %s - %s" (.getLevel ev) (.getMessage ev)))))
        (dolog (warn "Alert")))
      (is (= ">>> WARN - Alert" (.readLine (io/reader (java.io.StringReader. (str out))))))))

  ;; Filtering logging messages based on some complex criteria is something
  ;; that's much easier in a functional language.
  (testing "Filter out messages that contain 'password'"
    (set-logger! "test"
            :pattern "%m"
            :filter (fn [^org.apache.log4j.spi.LoggingEvent ev] (not (.contains (.getMessage ev) "password"))))
    (dotest
     (info "The user is billy") "The user is billy"
     (info "The password is nighthawk") "")))
