# Aggregation Logger

The aggregation logger tracks the total number of log events raised by a Java application as well as by active loggers with  breakdown by level (severity). An asynchronous sender thread transmits the counters to a time series database every 60 seconds via TCP/UDP/HTTP(s) protocols for alerting and long-term retention.

Collecting aggregate error counts is particularly relevant for monitoring large-scale distributed applications where individual errors are too numerous to analyze. See LogInfo/./LogFatal metrics in Hadoop as an example  https://hadoop.apache.org/docs/r2.7.2/hadoop-project-dist/hadoop-common/Metrics.html.

The following counters are collected:

```
log_event_total_counter     #Total number of log events raised by the application. Tags: level
log_event_counter           #Number of log events for active loggers. Tags: level, logger (full class name)
```

Counter values are continuously incremented to protect against accidental data loss and to minimize dependency on sampling interval.

> Supported Levels: TRACE, DEBUG, INFO, WARN, ERROR
 
In addition to counters, the logger can send a small subset of raw events to the database for L2/L3 triage. The index of events sent within a 10-minute period is determined using exponential backoff multipliers. The index is reset at the end of the period.

* INFO.  Multiplier 5. Events sent: 1, 5, 25, 125 ... 5^(n-1)
* WARN.  Multiplier 3. Events sent: 1, 3, 9, 27 ...   3^(n-1)
* ERROR. Multiplier 2. Events sent: 1, 2, 4, 8 ...    2^(n-1)

> ERROR events that inherit from java.lang.Error are sent to the database instantly, regardless of the event index.

The aggregation logger sends a small subset of events to the database and as such is not a replacement for specialized log search and indexing tools. Instead, it attempts to strike a balance between the volume of collected data and response time.

The logger consists of the core library and adapters for Logback and Log4j logging frameworks.

## Heartbeat

Since counters are flushed to the database every 60 seconds, the incoming event stream can be used for heartbeat monitoring as an early warning of network outages, garbage collection freezes, and application crashes.

![Heartbeat rule example](log_writer_heartbeat.png)

![Heartbeat rule in XML](rule_java_log_writer_heartbeat_stopped.xml)

## Sample Portal

![Log Counter Portal](log_errors_sm.png)

## Live Examples

- [Standalone](https://apps.axibase.com/chartlab/2f607d1b/7)
- [Distributed](https://apps.axibase.com/chartlab/007721aa)

## Requirements

- Java 1.7 and later

## Supported Logging Frameworks

- [Logback](http://logback.qos.ch/documentation.html) 0.9.21+, 1.0.x, 1.1.x (slf4j 1.6.0+) - [aggregation-log-filter-logback](https://github.com/axibase/aggregation-log-filter-logback).
- [Log4j](http://logging.apache.org/log4j) 1.2.13+ - [aggregation-log-filter-log4j](https://github.com/axibase/aggregation-log-filter-log4j). 
- [Log4j2](http://logging.apache.org/log4j/2.0/) 2.5+ - [aggregation-log-filter-log4j2](https://github.com/axibase/aggregation-log-filter-log4j2). 

## Supported Time Series Databases

- [Axibase Time Series Database][atsd]

## Configuration Examples

- [Logback XML](#logback-xml-configuration-example)
- [log4j Properties](#log4j-properties-example)
- [log4j XML](#log4j-xml-example)
- [log4j2 XML](#log4j2-xml-example)

## Performance

```java
        long start = System.currentTimeMillis();
        for (int i = 1; i <= 1000000; i++) {
            logger.error("msg " + new Date() + " : index=" + i);
        }
        long end = System.currentTimeMillis();
```

#### Filter Disabled

```
#log4j.appender.APPENDER.filter.COLLECTOR=com.axibase.tsd.collector.log4j.Log4jCollector
#log4j.appender.APPENDER.filter.COLLECTOR.url=tcp://localhost
```

**DONE in 5589**

#### Filter Enabled

```
log4j.appender.APPENDER.filter.COLLECTOR=com.axibase.tsd.collector.log4j.Log4jCollector
log4j.appender.APPENDER.filter.COLLECTOR.url=tcp://localhost
```

**DONE in 6002**

## Installation

### Option 1: Maven

Add Maven dependency to one of supported logging adapters (logback, log4j, log4j2). Dependency to aggregator core will be imported automatically:

```xml
<dependency>
            <groupId>com.axibase</groupId>
            <artifactId>aggregation-log-filter-logback</artifactId>
            <version>1.0.5</version>
</dependency>
```

### Option 2: Classpath

Add core and adapter libraries to classpath:

- Download aggregation-log-filter-1.0.x.jar from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter%22)
- Download aggregation-log-filter-logback-1.0.x.jar from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-logback%22)
- Adds jar files to classpath

```
java -classpath lib/app.jar:lib/aggregation-log-filter-1.0.5.jar:lib/aggregation-log-filter-logback-1.0.5.jar Main
```

### Option 3: lib directory 

Cope core and adapter libraries to application lib directory.

Apache ActiveMQ example:

```
wget --content-disposition -P /opt/apache-activemq-5.9.1/lib/ \
"https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.axibase&a=aggregation-log-filter&v=LATEST"
wget --content-disposition -P /opt/apache-activemq-5.9.1/lib/ \
"https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.axibase&a=aggregation-log-filter-log4j&v=LATEST"
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
            <url>tcp://atsd_host:tcp_port</url>
        </filter>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

  - [View logback.xml example with RollingFileAppender.](https://github.com/axibase/aggregation-log-filter-logback/blob/master/src/test/resources/logback-atsd-example.xml)

## Log4j Properties Example 

```properties
#attach log aggregator to 'logfile' appender --> 
log4j.appender.logfile.filter.COLLECTOR=com.axibase.tsd.collector.log4j.Log4jCollector
log4j.appender.logfile.filter.COLLECTOR.url=tcp://atsd_host:tcp_port
```

  - [View log4j.properties example.](https://github.com/axibase/aggregation-log-filter-log4j/blob/master/src/test/resources/log4j-test.properties)
 
## Log4j XML Example

```xml
    <appender name="APPENDER" class="org.apache.log4j.ConsoleAppender">
        <layout class="org.apache.log4j.PatternLayout">
            <param name="ConversionPattern" value="%d [%t] %-5p %c - %m%n"/>
        </layout>
        <filter class="com.axibase.tsd.collector.log4j.Log4jCollector">
            <param name="url" value="tcp://atsd_host:tcp_port"/>
        </filter>
    </appender>
```

  - [View complete log4j.xml example.](https://github.com/axibase/aggregation-log-filter-log4j/blob/master/src/test/resources/log4j-test.xml)

## Log4j2 XML Example

```xml
<Configuration>
    <Appenders>
        <Console name="APPENDER">
            <Filters>
                <Collector url="tcp://atsd_host:tcp_port"/>
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

## Adding MDC Context Parameters to Messages

### Java Example

```java
   #MDC.put("job_name", job.getName());
   MDC.put("job_name", "snmp-prd-router");
```

```
%X{key} placeholder is replaced in message pattern based on MDC context parameters
%m [%X{job_name}] is replaced to Job failed [snmp-prd-router]
```

### Log4j

```
   log4j.appender.APPENDER.filter.COLLECTOR.pattern=%m [%X{job_name}]%n
```

### Logback

```xml
 <pattern>%m [%X{job_name}]%n</pattern>
```

  - See also [Logback:Mapped Diagnostic Context](http://logback.qos.ch/manual/mdc.html)

## Configuration Settings

| Name | Required | Default | Description |
|---|---|---|---|
| url | yes | - | scheme, host and port |
| level | no | TRACE | minimum level to process events |
| entity | no | machine hostname | entity name for series and messages, usually hostname of the machine where the application is running |
| tag | no | - | user-defined tag(s) to be included in series and message commands, MULTIPLE |
| intervalSeconds | no | 60 | interval in seconds for sending collected counters |
| sendMessage | no | - | see `sendMessage` config, MULTIPLE |
| pattern | no | %m | pattern to format logging events sent to the database |

## scheme

Configures a TCP, UDP or HTTP writer to send statistics and messages to a supported time series database.

### TCP

```xml
<url>tcp://atsd_host:tcp_port</url>
```

| Name | Required | Default | Description |
|---|---|---|---| 
| host | yes | - | database hostname or IP address, string |
| port | no | 8081 | database TCP port, integer |

### UDP

```xml
<url>udp://atsd_host:udp_port</url>
```

| Name | Required | Default | Description |
|---|---|---|---|
| host | yes | - | database hostname or IP address, string |
| port | no | 8082 | database UDP port, integer |

### HTTP

```xml
<url>http://username:password@atsd_host:http_port</url>
```

| Name | Required | Default | Description |
|---|---|---|---|
| username | yes | - | username, string |
| password | yes | - | password, string |
| host | yes | - | database hostname or IP address, string |
| port | no | 80 | database HTTP port, integer |
| path | no | /api/v1/commands/batch | API command, string |

## sendMessage

Configures which log events should be sent to the storage system.

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
| level | no | WARN | Trace level to which this configuration applies. Note, that lower level settings do not apply to upper levels. Each level is configured separately. |
| stackTraceLines | no | 0; ERROR: -1 | number of stacktrace lines included in the message, -1 -- all lines |
| sendMultiplier | no | INFO-: 5; WARN: 3; ERROR: 2   | Determines index of events sent each period (10 minutes) determined as sendMultiplier^(n-1). |


[atsd]:https://axibase.com/products/axibase-time-series-database/

## Troubleshooting

Add `debug = true` parameter to display logger errors and commands.

Logback: add under `<filter>`

```xml 
<debug>true</debug>
```

Log4j: add JVM setting -Dlog4j.debug and add DEBUG setting to log4j.properties file

```
log4j.logger.com.axibase=DEBUG
```

Log4j2: add debug under `<Collector>` and set status="DEBUG" under `Configuration`

```xml 
<Configuration status="DEBUG">
    <Appenders>
        <Console name="APPENDER">
            <PatternLayout pattern="%d [%t] %-5p %c - %m%n"/>
            <Filters>
                <Collector
                        url="tcp://atsd_host:tcp_port"
                        debug="true"
                        />
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
