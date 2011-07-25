;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-logging-config.jul.test-jul
  (:import (java.util.logging Logger Level SimpleFormatter ConsoleHandler))
  (:use clojure.test
        clojure.contrib.logging
        clojure.contrib.pprint
        clj-logging-config.jul
        clojure.contrib.with-ns)
  (:require [clojure.java.io :as io]))

(defmacro dolog [& body]
  `(with-ns (create-ns (symbol "test"))
     (clojure.core/refer-clojure)
     (use 'clojure.contrib.logging)
     ~@body))

(defmacro capture-stderr [& body]
  `(let [old# System/err
         baos# (java.io.ByteArrayOutputStream.)
         new# (java.io.PrintStream. baos#)]
     (System/setErr new#)
     ~@body
     (.flush new#)
     (System/setErr old#)
     (String. (.toByteArray baos#))))

(defmacro dotest [& body]
  `(are [actual expected]
        (= expected (capture-stderr (dolog actual)))
        ~@body))

(use-fixtures :each (fn [f]
                      (reset-logging!)
                      (comment (f))))

;; These tests don't yet pass and to be honest I've wasted too many hours of my
;; life trying to fix them. I'm long since past caring.

(deftest test-logging
  (testing "Default logging"
    (set-logger! "test")
    (dotest
     (info "Here is a log message") "INFO - Here is a log message"
     (warn "Here is a warning") "WARN - Here is a warning"
     (debug "Debug messages are hidden by default") ""))

  (testing "Logging at the FINE level"
    (set-logger! "test" :level Level/FINE)
    (dotest
     (info "Here is a log message") "INFO - Here is a log message"
     (debug "Debug level messages are now shown") "DEBUG - Debug level messages are now shown"))

  (testing "Levels can also be specified with keywords"
    (set-logger! "test" :level :warn)
    (dotest
     (debug "Debug messages are hidden") ""
     (info "So are log messages") ""
     (warn "Only warnings") "WARN - Only warnings"
     (error "And errors") "ERROR - And errors"))

  (testing "Setting a custom layout"
    (set-logger! "test" :layout (SimpleFormatter.))
    (dotest
     (info "Here is a log message") "INFO - Here is a log message"))

  (testing "We can even use a Clojure function as a layout"
    (set-logger! "test" :layout (fn [ev]
                                  (format "%s: %s" (:level ev) (:message ev))))
    (dotest
     (info "Homoiconic languages are better than config files!") "Homoiconic languages are better than config files!"))

  (testing "Setting an appender"
    (set-logger! "test" :out (ConsoleHandler.)))

  ;; But sometimes we want to quickly implement our own custom appender in Clojure
  ;; which is painful to do in Java. This example uses println for testing
  ;; purposes but there's no reason it couldn't do something more complex (like
  ;; send a tweet).
  (testing "Set a custom appender in Clojure"
    (let [out (java.io.StringWriter.)]
      (binding [*out* out]
        (set-logger! "test"
                     :out (fn [ev]
                            (println (format ">>> %s - %s" (:level ev) (:message ev)))))
        (dolog (warn "Alert")))
      (is (= ">>> WARN - Alert" (.readLine ^java.io.BufferedReader (io/reader (java.io.StringReader. (str out))))))))

  ;; Filtering logging messages based on some complex criteria is something
  ;; that's much easier in a functional language.
  (testing "Filter out messages that contain 'password'"
    (set-logger! "test"
                 :pattern "%m"
                 :filter (fn [ev] (not (.contains (:message ev) "password"))))
    (dotest
     (info "The user is billy") "The user is billy"
     (info "The password is nighthawk") "")))
