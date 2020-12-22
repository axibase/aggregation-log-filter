[![Travis](https://api.travis-ci.org/axibase/aggregation-log-filter.svg)](https://travis-ci.org/axibase/aggregation-log-filter)
[![Build](https://github.com/axibase/aggregation-log-filter/workflows/Build%20Aggregation%20Log%20Filter/badge.svg)](https://github.com/axibase/aggregation-log-filter/actions)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.axibase/aggregation-log-filter/badge.svg)](https://mvnrepository.com/artifact/com.axibase/aggregation-log-filter/)

# Aggregation Logger

## Overview

The aggregation logger plugs into a log appender and tracks the total number of log events raised by a Java application with breakdown by level: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`.

The counters are sent via the TCP/UDP/HTTP/HTTPS protocol to a time series database every 60 seconds for alerting and long-term retention.

Collecting aggregate error counts is particularly relevant for applications where individual errors are too numerous to analyze. See **LogInfo/.../LogFatal** metrics in [Hadoop](https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/Metrics.html) as an example.

The logger consists of the core library and adapters for **`Logback`**, **`Log4j`**, and **`Log4j2`** logging frameworks.

## Sample Portal

![](./readme_4.png)

## Live Examples

* [Standalone](https://apps.axibase.com/chartlab/2f607d1b/7)
* [Distributed](https://apps.axibase.com/chartlab/007721aa)

## Reference

* [Collected Data](#collected-data)
* [Heartbeat](#heartbeat)
* [Sample Portal](#sample-portal)
* [Live Examples](#live-examples)
* [Requirements](#requirements)
* [Supported Logging Frameworks](#supported-logging-frameworks)
* [Supported Time Series Databases](#supported-time-series-databases)
* [Configuration Examples](#configuration-examples)
* [Performance](#performance)
* [Installation](#installation)
* [MDC Context Parameters in Messages](#mdc-context-parameters-in-messages)
* [Configuration Settings](#configuration-settings)
* [Database Address](#database-address)
* [Troubleshooting](#troubleshooting)

## Collected Data

### Counters

Aggregation Logger collects the following metrics:

| **Metric** | **Tags** | **Type** | **Description** |
|:---|:---|---|:---|
| `log_event_total_counter`| level | counter | Total number of log events raised by the application. |
| `log_event_counter` | level<br>logger | counter | Number of log events raised by each logger.<br>Controlled with the `sendLoggerCounter` [setting](#configuration-settings). |

Counter values are continuously incremented to protect against accidental data loss and to minimize dependency on the sampling interval.

### Messages

The logger can send a small subset of raw events to the database for triage. The index of events sent within a 10-minute period is determined using exponential backoff multipliers. The index is reset at the end of the period.

* `INFO.  Multiplier 5. Events sent: 1, 5, 25, 125, ..., 5^(n-1)`
* `WARN.  Multiplier 3. Events sent: 1, 3, 9, 27, ..., 3^(n-1)`
* `ERROR. Multiplier 2. Events sent: 1, 2, 4, 8, ..., 2^(n-1)`

> `ERROR` events that inherit from `java.lang.Error` are sent to the database instantly, regardless of the event index.

The aggregation logger sends only a small subset of events to the database and is not a replacement for specialized log search tools. Instead, logger attempts to strike a balance between the volume of collected data and response time.

### Properties

The following `java.log_aggregator.*` properties are stored by logger:

| **Type** | **Description** |
|:---|:---|
|`environment`|Current system environment.|
|`runtime`|Current system properties.|
|`settings`|Aggregation logger [settings](#configuration-settings).|
|`operating_system`|General information about the operating system.|

## Heartbeat

Since counters are flushed to the database every 60 seconds, the incoming event stream can be used for heartbeat monitoring as an early warning of network outages, garbage collection freezes, and application crashes.

![](./readme_1.png)
![](./readme_2.png)
![](./readme_3.png)

Heartbeat rule [configuration](./rule.xml) in XML.

## Requirements

* Java `1.7` and later

## Supported Logging Frameworks

* [`Logback`](https://logback.qos.ch/documentation.html) `0.9.21+`, `1.0.x`, `1.1.x` (`slf4j 1.6.0+`) - [`aggregation-log-filter-logback`](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-logback%22).
* [`Log4j`](https://logging.apache.org/log4j) `1.2.13+` - [`aggregation-log-filter-log4j`](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-log4j%22).
* [`Log4j2`](https://logging.apache.org/log4j/2.0/) `2.q5+` - [`aggregation-log-filter-log4j2`](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-log4j2%22).

## Supported Time Series Databases

* [Axibase Time Series Database](https://axibase.com/docs/atsd/)

## Configuration Examples

* [`Logback` XML](#logback-xml-configuration-example)
* [`log4j` Properties](#log4j-properties-example)
* [`log4j` XML](#log4j-xml-example)
* [`log4j2` XML](#log4j2-xml-example)

## Performance

```java
long start = System.currentTimeMillis();
for (int i = 1; i <= 1000000; i++) {
    logger.error("msg " + new Date() + " : index=" + i);
}
long end = System.currentTimeMillis();
```

### Filter Disabled

```sh
#log4j.appender.APPENDER.filter.COLLECTOR=com.axibase.tsd.collector.log4j.Log4jCollector
#log4j.appender.APPENDER.filter.COLLECTOR.url=tcp://localhost
```

> `DONE in 5589 ms`

#### Filter Enabled

```sh
log4j.appender.APPENDER.filter.COLLECTOR=com.axibase.tsd.collector.log4j.Log4jCollector
log4j.appender.APPENDER.filter.COLLECTOR.url=tcp://localhost
```

> `DONE in 6002 ms`

## Installation

### Option 1: Maven

Add the Maven dependency to one of supported logging adapters: `logback`, `log4j`, or `log4j2`.

Dependency to the aggregator core is imported automatically:

```xml
<dependency>
    <groupId>com.axibase</groupId>
    <artifactId>aggregation-log-filter-{adapter}</artifactId>
    <version>2.x.y</version>
</dependency>
```

### Option 2: Classpath

Add core and adapter libraries to `classpath`:

* Download `aggregation-log-filter-1.2.x.jar` from [Maven Central](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter%22)
* Download `aggregation-log-filter-{adapter}-1.2.x.jar` from Maven Central
  * [`logback`](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-logback%22)
  * [`log4j`](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-log4j%22)
  * [`log4j2`](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-log4j2%22)
* Add `.jar` files to `classpath`, replace `x` with appropriate version:

```sh
java -classpath lib/app.jar:lib/aggregation-log-filter-2.x.y.jar:lib/aggregation-log-filter-{adapter}-2.x.y.jar Main
```

### Option 3: lib directory

Copy core and adapter libraries to the application `/lib` directory.

Apache ActiveMQ example:

```sh
wget --content-disposition -P /opt/apache-activemq-5.9.1/lib/ \
"https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.axibase&a=aggregation-log-filter&v=LATEST"
wget --content-disposition -P /opt/apache-activemq-5.9.1/lib/ \
"https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.axibase&a=aggregation-log-filter-{adapter}&v=LATEST"
```

## Logback XML Configuration Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>../logs/app.log</file>
        <append>true</append>

        <encoder>
            <pattern>%date{ISO8601};%level;%thread;%logger;%message%n</pattern>
        </encoder>

        <!-- attach log aggregator to 'FILE' appender -->
        <filter class="com.axibase.tsd.collector.logback.Collector">
            <url>tcp://atsd_hostname:tcp_port</url>
        </filter>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

* [View `logback.xml` example with `RollingFileAppender`](./aggregation-log-filter-logback/src/test/resources/logback-atsd-example.xml).

## Log4j Properties Example

```properties
#attach log aggregator to 'logfile' appender -->
log4j.appender.logfile.filter.COLLECTOR=com.axibase.tsd.collector.log4j.Log4jCollector
log4j.appender.logfile.filter.COLLECTOR.url=tcp://atsd_hostname:tcp_port
```

* [View `log4j.properties` example](./aggregation-log-filter-log4j/src/test/resources/log4j-test.properties).

## Log4j XML Example

```xml
<appender name="APPENDER" class="org.apache.log4j.ConsoleAppender">
    <layout class="org.apache.log4j.PatternLayout">
        <param name="ConversionPattern" value="%d [%t] %-5p %c - %m%n"/>
    </layout>
    <filter class="com.axibase.tsd.collector.log4j.Log4jCollector">
        <param name="url" value="tcp://atsd_hostname:tcp_port"/>
    </filter>
</appender>
```

* [View complete `log4j.xml` example](./aggregation-log-filter-log4j/src/test/resources/log4j-test.xml).

## Log4j2 XML Example

```xml
<Configuration>
    <Appenders>
        <Console name="APPENDER">
            <Filters>
                <Collector url="tcp://atsd_hostname:tcp_port"/>
            </Filters>
        </Console>
    </Appenders>
    <Loggers>
        <Logger name="com.axibase"/>
        <Root level="INFO">
            <AppenderRef ref="APPENDER"/>
        </Root>
    </Loggers>
</Configuration>
```

## MDC Context Parameters in Messages

MDC context parameters can be included in message command as tags such as `job_name`, `task_id`, etc.

```ls
message e:spbswgvml008 t:command=AxibaseCollector t:type=logger m:"Fetching error java.io.IOException:
   No files found: file:///opt/files" t:severity=ERROR t:level=ERROR t:source=com.collector.FileService
   t:job_name=snmp-prd-router t:task_id=2 t:thread=taskExecutor-1
```

### Java Example

```java
#MDC.put("job_name", job.getName());
MDC.put("job_name", "snmp-prd-router");
MDC.put("task_id", "2");
```

### Log4j

```sh
log4j.appender.APPENDER.filter.COLLECTOR.mdcTags=job_name;task_id
```

### Logback

```xml
<mdcTag>job_name</mdcTag>
<mdcTag>task_id</mdcTag>
```

* See also [Logback: Mapped Diagnostic Context](https://logback.qos.ch/manual/mdc.html)

## Configuration Settings

| Name | Required | Default | Description |
|---|---|---|---|
| `url` | yes | - | Database address specified with URI syntax: `scheme:[//[user:password@]host[:port]]`.<br>Supported schemes: [TCP](#tcp), [UDP](#udp), [HTTP](#http), [HTTPS](#https). |
| `entity` | no | machine hostname | Entity name for series and messages, usually the hostname of the machine where the application is running. |
| `tag` | no | - | User-defined tags to be included in series and message commands, MULTIPLE. |
| `level` | no | TRACE | Minimum level for processed events. |
| `intervalSeconds` | no | 60 | Interval in seconds for sending collected counters. |
| `sendMessage` | no | - | See the [`sendMessage`](#sendmessage) config, MULTIPLE. |
| `pattern` | no | `%m` | Pattern to format logging events sent to the database. <br>The pattern cannot include fields that are already included as tags such as logger name, level, etc. |
| `sendLoggerCounter` | no | `true` | When disabled, event counts by logger are not tracked and the [`log_event_counter`](#counters) metric is not sent. |
| `mdcTags` | no | - | User-defined tags to be included in message commands, value extracted from [`MDC context`](#mdc-context-parameters-in-messages), MULTIPLE. |
| `debug` | no | `false` | Enable logging to `stdout` debug information, see [**Troubleshooting**](#troubleshooting). |
| `messageLength` | no | `-1` | Allow to control event message size, default value to show the whole message. |
| `ignoreSslErrors` | no | `true` | Ignore errors if the SSL certificated presented by the endpoint is self-signed, expired, or otherwise invalid. |

## Database Address

Configures a TCP, UDP, HTTP, or HTTPS writer to send statistics and messages to a supported time series database.

### TCP

```xml
<url>tcp://atsd_hostname:tcp_port</url>
```

| Name | Required | Default | Description |
|---|---|---|---|
| `host` | yes | - | database hostname or IP address, string |
| `port` | no | `8081` | database TCP port, integer |

### UDP

```xml
<url>udp://atsd_hostname:udp_port</url>
```

| Name | Required | Default | Description |
|---|---|---|---|
| `host` | yes | - | database hostname or IP address, string |
| `port` | no | `8082` | database UDP port, integer |

### HTTP

```xml
<url>http://username:password@atsd_hostname:8088</url>
```

| Name | Required | Default | Description |
|---|---|---|---|
| `username` | yes | - | username, string |
| `password` | yes | - | password, string |
| `host` | yes | - | database hostname or IP address, string |
| `port` | no | `80/443` | database HTTP/HTTPS port, integer<br>Note that ATSD is listening on ports `8088`/HTTP and `8443`/HTTPS by default. |

### HTTPS

```xml
<url>https://username:password@atsd_hostname:8443</url>
```

Same settings as [HTTP](#http) scheme.

## sendMessage

Configures which log events are sent to the database.

```xml
<sendMessage>
    <level>WARN</level>
</sendMessage>
<sendMessage>
    <level>ERROR</level>
    <stackTraceLines>15</stackTraceLines>
    <sendMultiplier>3</sendMultiplier>
</sendMessage>
```

| Name | Required | Default Value | Description |
|---|---|---|---|
| `level` | no | `WARN` | Trace level to which this configuration applies. Note, that lower level settings do not apply to upper levels. Each level is configured separately. |
| `stackTraceLines` | no | `0; ERROR: -1` | Number of `stacktrace` lines included in the message, `-1 -- all lines`. |
| `sendMultiplier` | no | `INFO: 5`<br>`WARN: 3`<br>`ERROR: 2`   | Determines index of events sent each period (10 minutes). Determined as `sendMultiplier^(n-1)`. |

### Location Fields

If the appender pattern contains location fields, such as `%L` (line) and `%M` (method), these fields are added to messages as tags.

```xml
<layout class="org.apache.log4j.PatternLayout">
   <param name="ConversionPattern" value="%d [%t] %-5p %c - %m%n %L"/>
</layout>
```

Message command example with location fields:

```ls
message e:nurswgvml007 t:command=com.axibase.tsd.Server t:type=logger m:"Initialization complete"
    t:severity=INFO t:level=INFO t:source=com.axibase.tsd.InitLogger t:thread=main
    t:line=145 t:method=initBase
```

## Troubleshooting

Add the `debug = true` parameter to display logger errors and commands.

`Logback`: add under `<filter>`.

```xml
<debug>true</debug>
```

`Log4j`: add the JVM setting `-Dlog4j.debug` and add the `DEBUG` setting to the `log4j.properties` file.

```sh
log4j.appender.APPENDER.filter.COLLECTOR.debug=true
```

`Log4j2`: add debug under `<Collector>` and set `status="DEBUG"` under `Configuration`:

```xml
<Configuration status="DEBUG">
    <Appenders>
        <Console name="APPENDER">
            <PatternLayout pattern="%d [%t] %-5p %c - %m%n"/>
            <Filters>
                <Collector url="tcp://atsd_hostname:tcp_port" debug="true" />
            </Filters>
        </Console>
    </Appenders>
    <Loggers>
        <Logger name="com.axibase"/>
        <Root level="INFO">
            <AppenderRef ref="APPENDER"/>
        </Root>
    </Loggers>
</Configuration>
```
