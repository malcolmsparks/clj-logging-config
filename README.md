# Log configuration for Clojure

A logging facility in Clojure is provided by the clojure.contrib.logging module. This searches the JVM classpath for one of the various logging frameworks to delegate logging statements to.

While this is very useful, logging in Java has always been complicated to configure correctly. Fortunately, the two major logging 'back-ends' in Java (log4j and 'java.util.logging') support programmatic configuration so it is easy to replace the configuration file mechanisms with something that is easier and more flexible for Clojure programmers.

This library supports easy configuration via Clojure rather than Java properties files and XML. It is the author's belief that these confusing configuration formats reflect the static nature of the Java language itself and that Clojure should not inherit such complexity.

Right now, only log4j is (properly) supported (since that it by far the most popular framework in use today). Support for java.util.logging is being added and will be fully supported soon.

## Examples

Start by using <code>:use</code> (or <code>:require</code>) in your namespace declaration (or similar).

    (ns my-ns
      (:use clojure.contrib.logging
            [clj-logging-config.log4j :only logger]))

Configure the logging system declaring a logger with <code>set-logger!</code> and just start using it.

    (set-logger!)
    (info "Just a plain logging message, you should see the level at the beginning")

In Log4J it is common to set a pattern against the logger. Full syntax can be found in org.apache.log4j.PatternLayout. For example, to set a pattern which just displays the message, do this:

    (set-logger! :pattern "%m%n")
    (info "A logging message, just the message this time")

Or this.

    (set-logger! :pattern "%d - %m%n")
    (info "A logging message with the date in front")

In the same way you can set the level of the logger. For example, so that the logger only prints warnings, you can do this :-

    (set-logger! :level :warn) 

Or if you want to use the Log4J Java class, you can do that as well :-

    (set-logger! :level org.apache.log4j.Level/WARN) 

You can also set the appender to one of the appenders provided by the logging package you are using. For example, it it's Log4J you can do this :-

    (set-logger! :appender (org.apache.log4j.DailyRollingFileAppender.))

Or even provide your own appender function.

    (set-logger! :appender (fn [ev] (println (:message ev))))

You can also set filters in the same way. Filters are useful but rarely used in Log4J because they're a pain to configure - not now :-

    (set-logger! :filter (fn [ev] (not (re-seq #"password" (:message ev)))))

For filters and appenders the underlying logging event is converted to a Clojure map (with the original event bound to the <code>:event</code> key, should you need it)

For completeness you can also set the additivity of the logger - the default is true but you can set it to false. Addivity is described in the Log4J user-guide - so we won't repeat it here.

    (set-logger! :additivity false)

It (almost) goes without saying that you can combine all these options together :-

    (set-logger! :level :warn 
                 :additivity false
                 :pattern "%p - %m%n"
                 :filter (constantly true))

There are some combinations that doesn't make sense (such as using <code>:pattern</code> and <code>:layout</code> together). In these cases an exception will be thrown.

## Logger names

Java logging packages conventionally organise a hierarchy of loggers that mirrors the Java package hierarchy. The <code>clojure.contrib.logging</code> module follows this convention and so does <code>clj-logging-config<code>. If you don't specify a logger name it defaults to the current namespace when you use <code>set-logger!</code>. But you can override this if you need to.

    (set-logger! "my-loggger" :level :info)

## Setting multiple loggers

A <code>log4j.properties</code> (or <code>log4j.xml</code>) file configures multiple loggers as the same time. You can too with <code>set-loggers!</code> :-

    (set-loggers! 

        "com.malcolmsparks.foo" 
        {:level :info :pattern "%m"}

        "com.malcolmsparks.bar" 
        {:level :debug})

## Why the bang?

Remember that <code>set-logger!</code> mutates the configuration of logging in the JVM. That's why there's a '!' in the function name. It would be very nice if logging configuration could be set on a per-thread basis - that's just not how the Java logging packages are designed with everything as statics, something we must live with.

## Appender names

By default, appenders are added. The problem is that in some Clojure programming environments you recompile namespace frequently, which would cause a new appender to get added on each occurance. The library addresses this problem by giving every appender a name- if you don't specify it defaults to <code>_default</code>. When you call <code>set-logger!</code> it replaces any existing logger with the same name. If you want more control over this (for example, to add appenders to a prior logging configuration non-destructivly) you can specify the appender name explicitly :-

    (set-logger! :name "access-log")

## Thread-local logging

(Coming soon)





