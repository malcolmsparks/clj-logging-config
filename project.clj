;; clj-logging-config - Easy logging configuration for Clojure.

;; by Malcolm Sparks

;; Copyright (c) Malcolm Sparks. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(defproject clj-logging-config "1.7.0"
  :description "Easy logging configuration for Clojure."

  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.1.2"]
                 [org.clojure.contrib/with-ns "1.3.0-alpha4"]
                 ]
  :dev-dependencies [[swank-clojure "1.2.0"]
                     [log4j/log4j "1.2.16"]]
  :source-path "src/main/clojure"
  :test-path "src/test/clojure"
  :target-dir "target/"
)
