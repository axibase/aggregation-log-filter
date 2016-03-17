# Aggregation Logger

Implemented as a filter, the aggregation logger plugs into a log appender and counts log events raised by the application as well as by individual loggers with break-down by level: TRACE, DEBUG, INFO, WARN, and ERROR. The counters are periodically persisted to a time series database for monitoring and alerting on abnormal error levels.

The following metrics are collected:

- log_event_total_counter
- log_event_counter

The logger also sends a small subset of log events to the database for root-cause analysis. The index of events sent within each 10-minute period is determined using exponential backoff multipliers. The index is reset at the end of the period.

```
- INFO.  Multiplier 5. Events sent: 1, 5, 25, 125 ... 5^(n-1)
- WARN.  Multiplier 3. Events sent: 1, 3, 9, 27 ...   3^(n-1)
- ERROR. Multiplier 2. Events sent: 1, 2, 4, 8 ...    2^(n-1)
```

The logger consists of the core library and adapters for supported logging frameworks.

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

## Portal Examples

- Standalone Java Application: https://apps.axibase.com/chartlab/2f607d1b/7
- Distributed Java Application: https://apps.axibase.com/chartlab/007721aa. Multiple Java applications on the same or different hosts.

## Installation

### Option 1: Maven

Add Maven dependency to one of supported logging adapters (logback, log4j, log4j2). Dependency to aggregator core will be imported automatically:

```xml
<dependency>
            <groupId>com.axibase</groupId>
            <artifactId>aggregation-log-filter-logback</artifactId>
            <version>1.0.4</version>
</dependency>
```

### Option 2: Classpath

Add core and adapter libraries to classpath:

- Download aggregation-log-filter-1.0.x.jar from [Maven Central](http://search.maven.org/#search|gav|1|g%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter%22)
- Download aggregation-log-filter-logback-1.0.x.jar from [Maven Central](http://search.maven.org/#search|gav|1|g%3A%22com.axibase%22%20AND%20a%3A%22aggregation-log-filter-logback%22)
- Adds jar files to classpath

```
java -classpath lib/app.jar:lib/aggregation-log-filter-1.0.4.jar:lib/aggregation-log-filter-logback-1.0.4.jar Main
```

### Option 3: lib directory 

Cope core and adapter libraries to application lib directory.

Apache ActiveMQ example:

```
wget --content-disposition -P /opt/apache-activemq-5.9.1/lib/ "https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.axibase&a=aggregation-log-filter&v=LATEST"
wget --content-disposition -P /opt/apache-activemq-5.9.1/lib/ "https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.axibase&a=aggregation-log-filter-log4j&v=LATEST"
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
            <writer class="com.axibase.tsd.collector.writer.TcpAtsdWriter">
                <host>database_host</host>
                <port>8081</port>
            </writer>
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
log4j.appender.logfile.filter.COLLECTOR.writer=tcp
log4j.appender.logfile.filter.COLLECTOR.writerHost=database_hostname
log4j.appender.logfile.filter.COLLECTOR.writerPort=8081
```

  - [View log4j.properties example.](https://github.com/axibase/aggregation-log-filter-log4j/blob/master/src/test/resources/log4j-test.properties)
 
## Log4j XML Example

```xml
    <appender name="APPENDER" class="org.apache.log4j.ConsoleAppender">
        <layout class="org.apache.log4j.PatternLayout">
            <param name="ConversionPattern" value="%d [%t] %-5p %c - %m%n"/>
        </layout>
        <filter class="com.axibase.tsd.collector.log4j.Log4jCollector">
            <param name="writer" value="tcp"/>
            <param name="writerHost" value="database_hostname"/>
            <param name="writerPort" value="8081"/>
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
                <Collector writer="tcp" writerHost="database_host" writerPort="8081" />
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
| writer | yes | - | see `writer` config |
| level | no | TRACE | minimum level to process events |
| entity | no | machine hostname | entity name for series and messages, usually hostname of the machine where the application is running |
| tag | no | - | user-defined tag(s) to be included in series and message commands, MULTIPLE |
| sendSeries | no | - | see `sendSeries` config |
| sendMessage | no | - | see `sendMessage` config, MULTIPLE |
| pattern | no | %m | pattern to format logging events sent to the database |

## writer

Configures a TCP, UDP or HTTP writer to send statistics and messages to a supported time series database.

### TCP writer

```xml
<writer class="com.axibase.tsd.collector.writer.TcpAtsdWriter">
    <host>database_hostname</host>
    <port>8081</port>
</writer>
```

| Name | Required | Default | Description |
|---|---|---|---| 
| host | yes | - | database hostname or IP address, string |
| port | no | 8081 | database TCP port, integer |

### UDP writer

```xml
<writer class="com.axibase.tsd.collector.writer.UdpAtsdWriter">
    <host>database_hostname</host>
    <port>8082</port>
</writer>
```

| Name | Required | Default | Description |
|---|---|---|---|
| host | yes | - | database hostname or IP address, string |
| port | no | 8082 | database UDP port, integer |

### HTTP writer

```xml
<writer class="com.axibase.tsd.collector.writer.HttpAtsdWriter">
    <url>http://database_hostname:8088/api/v1/commands/batch</url>
    <username>USERNAME</username>
    <password>PASSWORD</password>
</writer>
```

| Name | Required | Default | Description |
|---|---|---|---|
| url | yes | - | API command URL 'http://database_hostname:8088/api/v1/commands/batch', string |
| username | yes | - | username, string |
| password | yes | - | password, string |

## sendSeries

Configures how often counter statistics are sent to the database.

```xml
<sendSeries>
    <intervalSeconds>60</intervalSeconds>
</sendSeries>
```

| Name | Required | Default Value | Description |
|---|---|---|---|
| intervalSeconds | no | 60 | Interval in seconds for sending collected log statistics |

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

## Log Counter Portal Example

![Log Counter Portal](https://axibase.com/wp-content/uploads/2015/11/log_filter.png)

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
                        writer="tcp"
                        writerHost="database_host"
                        writerPort="8081"
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
