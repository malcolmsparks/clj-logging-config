;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-logging-config.example
  (:import (org.apache.log4j PatternLayout FileAppender))
  (:use clojure.contrib.logging
        [clj-logging-config.log4j :only [set-logger! set-loggers! reset-logging]]))

;; If you get stuck you can reset the logging system with this
(reset-logging)

;; Try these examples in a REPL.

(set-logger!)
(info "Just a plain logging message, you should see the level at the beginning")

(set-logger! :pattern "%m%n")
(info "A logging message, just the message this time")

(set-logger! :pattern "%d - %m%n")
(info "A logging message with the date in front")
