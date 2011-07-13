# Log configuration for Clojure

A logging facility in Clojure is provided by the clojure.contrib.logging module. This searches the JVM classpath for one of the various logging frameworks to delegate logging statements to.

While this is very useful, logging in Java has always been complicated to configure correctly. 

Fortunately, the two major logging 'back-ends' in Java (log4j and 'java.util.logging') support programmatic configuration so it is easy to replace the configuration file mechanisms with something that is easier and more flexible for Clojure programmers.

Right now, only log4j is supported (since that it by far the most popular framework in use today). Hopefully support for java.util.logging will be added soon - if you need it now, then fork me!

# Log4j

This library supports easy configuration of log4j via Clojure rather than Java properties files and XML. It is the author's belief that these clunky configuration formats reflect the static nature of the Java language itself and that Clojure should not inherit such complexity.

## Examples

Start by using <code>:use</code> (or <code>:require</code>) in your namespace declaration (or similar).

    (ns my-ns
      (:use clojure.contrib.logging
            [clj-logging-config.log4j :only logger]))

Configure the logging system declaring a logger.

    (logger)



