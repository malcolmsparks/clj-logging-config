;; clj-logging-config - Logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;; be found in the file epl-v10.html at the root of this distribution.  By using
;; this software in any fashion, you are agreeing to be bound by the terms of
;; this license.  You must not remove this notice, or any other, from this
;; software.

(ns clj-logging-config.log4j.test-log4j
  (:use clojure.test
        clojure.tools.logging
        clj-logging-config.log4j
        clojure.contrib.with-ns)
  (:require [clojure.java.io :as io]))

(defmacro capture-stdout [& body]
  `(let [out# System/out
         baos# (java.io.ByteArrayOutputStream.)
         tempout# (java.io.PrintStream. baos#)]
     (try
       (System/setOut tempout#)
       ~@body
       (String. (.toByteArray baos#))
       (finally
        (System/setOut out#)))))

(defmacro dolog [& body]
  `(do (reset-logging!)
       (let [ns# (create-ns (symbol "test"))]
         (with-ns ns#
           (clojure.core/refer-clojure)
           (use 'clojure.tools.logging 'clj-logging-config.log4j)
           ~@body))))

(defmacro expect [expected & body]
  `(is (= ~expected (capture-stdout (dolog ~@body)))))

(use-fixtures :each (fn [f]
                      (reset-logging!)
                      (f)))

(deftest test-logging

  (testing "Default logging"

    (expect "INFO - Here is a log message\n"
            (set-logger!)
            (info "Here is a log message"))

    (expect "WARN - Here is a warning\n"
            (set-logger!)
            (warn "Here is a warning"))

    (expect ""
            (set-logger!)
            (debug "Debug messages are hidden by default")))

  (testing "Logging at the DEBUG level"

    (expect "DEBUG - Debug level messages are now shown\n"
            (set-logger! "test" :level org.apache.log4j.Level/DEBUG)
            (debug "Debug level messages are now shown")))

  (testing "Levels can also be specified with keywords"

    (expect ""
            (set-logger! "test" :level :warn)
            (debug "Debug messages are hidden"))

    (expect ""
            (set-logger! "test" :level :warn)
            (info "So are log messages"))

    (expect "WARN - Only warnings\n"
            (set-logger! "test" :level :warn)
            (warn "Only warnings"))

    (expect "ERROR - And errors\n"
            (set-logger! "test" :level :warn)
            (error "And errors")))

  (testing "Setting a pattern for the PatternLayout"

    (expect "Here is a log message\n"
            (set-logger! "test"
                         :pattern org.apache.log4j.PatternLayout/DEFAULT_CONVERSION_PATTERN)
            (info "Here is a log message")))

  (testing "Setting a custom pattern for the PatternLayout"
    (expect "[INFO] - Here is a log message"
            (set-logger! "test" :pattern "[%p] - %m")
            (info "Here is a log message")))

  (testing "Setting a custom layout"
    (expect "INFO - Here is a log message\n"
            (set-logger! "test" :layout (org.apache.log4j.SimpleLayout.))
            (info "Here is a log message")))

  (comment (testing "We can even use a Clojure function as a layout"
             (expect "INFO: Try doing this in log4j.properties!"
                     (set-logger! "test" :layout (fn [ev]
                                                   (format "%s: %s" (:level ev) (:message ev))))
                     (info "Try doing this in log4j.properties!"))))

  (testing "But we can't set a :layout and a :pattern (because a :pattern implies a org.apache.log4j.PatternLayout)"
    (is (thrown? Exception (set-logger! "test" :pattern "%m" :layout (org.apache.log4j.SimpleLayout.)))))

  ;; One of the advantages of hosting Clojure on the JVM is that you can (and
  ;; should) make use of functionality that already exists rather than
  ;; re-implementing it in Clojure.
  (testing "Setting an appender"
    (expect ""
            (set-logger! "test" :out (org.apache.log4j.RollingFileAppender.))))

  ;; But sometimes we want to quickly implement our own custom appender in Clojure
  ;; which is painful to do in Java. This example uses println for testing
  ;; purposes but there's no reason it couldn't do something more complex (like
  ;; send a tweet).
  (testing "Set a custom appender in Clojure"
    (is (= ">>> WARN - Alert!"
           (dolog
            (let [out (java.io.StringWriter.)]
              (binding [*out* out]
                (set-logger! "test"
                             :out (fn [ev]
                                    (println (format ">>> %s - %s" (:level ev) (:message ev)))))
                (warn "Alert!"))
              (.readLine ^java.io.BufferedReader (clojure.java.io/reader (java.io.StringReader. (str out)))))))))

  ;; Filtering logging messages based on some complex criteria is something
  ;; that's much easier in a functional language.
  (testing "Filter out messages that contain 'password'"

    (expect "The user is billy\nThe name is fred\n"
            (set-logger! "test"
                         :pattern "%m%n"
                         :filter (fn [ev] (not (.contains ^String (:message ev) "password"))))

            (info "The user is billy")
            (info "The password is nighthawk")
            (info "The name is fred"))))
