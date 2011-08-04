;; clj-logging-config - Logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.

;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;; be found in the file epl-v10.html at the root of this distribution.  By using
;; this software in any fashion, you are agreeing to be bound by the terms of
;; this license.  You must not remove this notice, or any other, from this
;; software.

(ns examples.log4j-agent-example
  (:require [clojure.java.io :as io])
  (:use clojure.tools.logging
        clj-logging-config.log4j
        clojure.contrib.pprint)
  (:import (org.apache.log4j LogManager Hierarchy Level Logger)
           (org.apache.log4j.spi RepositorySelector DefaultRepositorySelector RootLogger)))



(comment
(set-internal-logging-level! :debug)

(enable-thread-local-logging!)

(with-logging-config {:root {:level :debug :out "/tmp/foo1.log"}
                      "clj-logging-config.log4j" {:level :debug}}
  (set-internal-logging-level! :debug)
  (info "Here's some logging"))


  (enable-thread-local-logging!)

  (with-logging-config {:root {:level :debug :out "/tmp/foo.log"}}
    (debug "Here's some logging"))


  (enable-thread-local-logging!)

  (with-logging-config {:root {}})


  (warn "Just a plain logging message, you should see the level at the beginning")

  (set-thread-local-logging! true)



  (Logger/getLogger "examples.log4j-agent-example")

  (macroexpand '(with-logging-config {:root {} "fooh" {}}
                  (identity :foo)
                  (identity :foo)))

  (warn "Just a plain logging message, you should see the level at the beginning")

  (log :warn "mess")

  (.getConversionPattern (.getLayout (first (enumeration-seq (.getAllAppenders (Logger/getLogger "examples.log4j-agent-example"))))))

  (.warn (Logger/getLogger "examples.log4j-agent-example") "foo")
  (log :warn "mess")

  (set-thread-local-logging! false)

  (pprint (get-logging-config))

  (set-thread-local-logging! false)

  (set-internal-logging-level! :debug)

  (set-logger! :level :debug :pattern "C: %m%n")
  (warn "Just a plain logging message, you should see the level at the beginning")

  (reset-logging!)

  (pprint (get-logging-config)))

;; TODO: Why is the pattern not being set again?
;; TODO: First try getting get-logging-config to show the pattern layout
