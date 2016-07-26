Troubleshooting Coppersmith
===========================

### Missing imports
Symptom: Code fails to comile with one of the following messages:
```log
value featureSetBuilder is not a member of commbank.coppersmith.From
value featureSetBuilder is not a member of commbank.coppersmith.Joined
value featureSetBuilder is not a member of commbank.coppersmith.CompleteJoinHlFeatureSource
```
Cause: Missing `Coppersmith._` import. Either:
```scala
import commbank.coppersmith.api.Coppersmith._
```
or
```scala
import commbank.coppersmith.api._, Coppersmith._
```

### Serialisation issues

The functions that make up feature definitions must only refer to fully
serialisable types. Unfortunately it can be easy to accidentally close
over some non-serialisable instance indirectly, especially if using
classes that are nested within another class. Doing so will usually
manifest itself at runtime in the form of an exception with a stacktrace
along the lines of:

```
...
Caused by: cascading.flow.planner.PlannerException: could not build flow from assembly: [Neither Java nor Kyro works for class: class com.twitter.scalding.typed.MapFn instance: <function1>
export CHILL_EXTERNALIZER_DEBUG=true to see both stack traces]
...
Caused by: java.lang.RuntimeException: Neither Java nor Kyro works for class: class com.twitter.scalding.typed.MapFn instance: <function1>
export CHILL_EXTERNALIZER_DEBUG=true to see both stack traces
...
```

As indicated by the message, if you set the `CHILL_EXTERNALIZER_DEBUG` environment variable to `true` and run the job again, you will receive more information about the unserialisable instance, eg:

```
com.esotericsoftware.kryo.KryoException: java.util.ConcurrentModificationException
Serialization trace:
classes (sun.misc.Launcher$AppClassLoader)
classloader (java.security.ProtectionDomain)
context (java.security.AccessControlContext)
acc (java.net.URLClassLoader)
classloader (java.security.ProtectionDomain)
context (java.security.AccessControlContext)
acc (java.net.URLClassLoader)
classLoader (scala.reflect.runtime.JavaMirrors$JavaMirror)
$outer (scala.reflect.runtime.JavaMirrors$JavaMirror$$anon$1)
currentOwner (scala.reflect.internal.Trees$TreeTypeSubstituter)
EmptyTreeTypeSubstituter (scala.reflect.runtime.JavaUniverse)
$outer (scala.reflect.api.TypeTags$TypeTagImpl)
commbank$coppersmith$AggregationFeature$$evidence$8 (commbank.coppersmith.AggregationFeature)
collectF (commbank.coppersmith.scalding.ScaldingJobSpec$ScaldingJobSpec$AggregationFeatures$)
features$2 (commbank.coppersmith.scalding.SimpleFeatureJobOps$$anonfun$2)
g$1 (com.twitter.scalding.typed.TypedPipe$$anonfun$groupBy$1)
fn (com.twitter.scalding.typed.MapFn)
fmap (com.twitter.scalding.typed.MapFn)
```

Working from the bottom of the serialisation trace up should reveal the
type at the edge of the feature definition code that is causing the
problem (`ScaldingJobSpec.AggregationFeatures` in the above example).

In the case of a nested class that is implicitly carrying a reference to
its outer instance, it is usually sufficient to make the nested class a
top level class instead. In the case of classes that have
non-serialisable fields, making them lazy if possible or marking
them as transient can also fix the problem. More information can be
found in [this scalding FAQ](https://github.com/twitter/scalding/wiki/Frequently-asked-questions#q-im-getting-a-notserializableexception-on-hadoop-job-submission).


### Empty source data

If the expected features from a job are not being generated, it can be
helpful to confirm that the `FeatureSource` bindings are configured
correctly. Both the `HiveTextSource` and `HiveParquetSource`
implementations will log the absolute path from which data is loaded.
Enabling logging and searching for the following text in the output
will reveal where data is being read from:
> `INFO  commbank.coppersmith.scalding.HiveParquetSource  - Loading from /` ...

(or `HiveTextSource` for data in text format).

The following is a simple logging configuration that will enable logging:

```properties
log4j.rootLogger=ERROR,stdout

log4j.logger.commbank.coppersmith.scalding.HiveTextSource=INFO
log4j.logger.commbank.coppersmith.scalding.HiveParquetSource=INFO
log4j.logger.commbank.coppersmith.scalding.CoppersmithStats=INFO

log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=%-5p %c %x - %m%n
```
