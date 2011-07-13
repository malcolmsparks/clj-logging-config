(ns clj-logging-config.example
  (:use clojure.contrib.logging
        [clj-logging-config.log4j :only [logger]]))

(logger)

(warn "foo")

