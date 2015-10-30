# Aggregation Log Filter

The filter plugs into the logging pipeline and computes log event counters and interval rates for each trace level. These statistics can be used to monitor error levels for the entire application and for individual Java classes. In addition to measuring logging activity, the filter can be configured to send a small subset of log events into a backend storage system such as Axibase Time-Series Database to facilitate root-cause analysis.


```xml 
       <filter class="com.axibase.tsd.collector.logback.Collector">
            <writer class="com.axibase.tsd.collector.writer.HttpStreamingAtsdWriter">
                <url>http://atsd_server:8088/api/v1/command/</url>
                <username>USERNAME</username>
                <password>PASSWORD</password>
            </writer>
            <level>INFO</level>
            <sendSeries>
                <repeatCount>3</repeatCount>
                <metricPrefix>log_event</metricPrefix>
                <intervalSeconds>60</intervalSeconds>
                <minIntervalThreshold>100</minIntervalThreshold>
                <minIntervalSeconds>1</minIntervalSeconds>
            </sendSeries>
            <sendMessage>
                <every>100</every>
            </sendMessage>
            <sendMessage>
                <level>ERROR</level>
                <every>30</every>
                <stackTraceLines>-1</stackTraceLines>
            </sendMessage>
            <tag>
                <name>CUSTOM_TAG</name>
                <value>TAG_VALUE</value>
            </tag>
        </filter>
```

| Name | Required | Default | Description |
|---|---|---|---|
| writer | yes | - | see `writer` config |
| level | no | TRACE | minimum level to process event |
| entity | no | current hostname | entity name for series and messages, for example application name or hostname |
| tag | no | - | user-defined tag(s) to be included in series and message commands, MULTIPLE |
| sendSeries | yes | - | see `sendSeries` config |
| sendMessage | no | - | see `sendMessage` config, MULTIPLE |


## writer

Configures a TCP, UDP or HTTP writer to send statistics and messages to a backend storage system such as Axibase Time-Series Database.

### TCP writer

```xml
<writer class="com.axibase.tsd.collector.writer.TcpAtsdWriter">
    <host>atsd_server</host>
    <port>8081</port>
</writer>
```

| Name | Required | Default | Description |
|---|---|---|---| 
| host | yes | - | ATSD host, string |
| port | yes | - | ATSD TCP port, integer |

### UDP writer

```xml
<writer class="com.axibase.tsd.collector.writer.UdpAtsdWriter">
    <host>atsd_server</host>
    <port>8082</port>
</writer>
```

| Name | Required | Default | Description |
|---|---|---|---|
| host | yes | - | ATSD host, string |
| port | yes | - | ATSD UDP port, integer |

### HTTP writer

```xml
<writer class="com.axibase.tsd.collector.writer.HttpStreamingAtsdWriter">
    <url>http://atsd_server:8088/api/v1/command/</url>
    <username>axibase</username>
    <password>*****</password>
</writer>
```

| Name | Required | Default | Description |
|---|---|---|---|
| url | yes | - | ATSD API command URL like 'http://atsd_server:8088/api/v1/command/', string |
| username | yes | - | user name, string |
| password | yes | - | password, string |

## sendSeries

Configures how often counter and rate statistics are sent to the storage system.

```xml
<sendSeries>
    <!-- default: 60 -->
    <intervalSeconds>60</intervalSeconds>
    <!-- 0+ default:1 -->
    <repeatCount>5</repeatCount>    
    <!-- default: 0 -->
    <minIntervalThreshold>0</minIntervalThreshold>
    <!-- default: 5 -->
    <minIntervalSeconds>5</minIntervalSeconds>
</sendSeries>
```

| Name | Required | Default Value | Description |
|---|---|---|---|
| intervalSeconds | no | 60 | Interval in seconds for sending collected log statistics |
| minIntervalSeconds | no | 5 | Minimum interval between sending of statistics (seconds), in case `minIntervalThreshold` is triggered|
| minIntervalThreshold | no | 0 | Initiates sending of statistics ahead of schedule if number of messages exceeds minIntervalThreshold |
| repeatCount | no | 1 | Maximum number of repeat values for each counter to send |
| metricPrefix | no | log_event  | Metric name prefix  |
| rateIntervalSeconds | no | 60 | Interval for rate calculation |

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
    <resetIntervalSeconds>600</resetIntervalSeconds>
</sendMessage>
```

| Name | Required | Default Value | Description |
|---|---|---|---|
| level | no | WARN | Minimum trace level to which this configuration applies. For example, WARN applies to WARN, ERROR, and FATAL events. |
| stackTraceLines | no | 0 | number of stacktrace lines that will be included in the message, -1 -- all lines |
| sendMultiplier | no | ERROR: 2.0; WARN: 3.0; INFO: 5.0 | Determines how many events are sent within each interval, determined with resetIntervalSeconds; sendMultiplier = 2 : send events 1, 2, 4, 8, 16, … ; sendMultiplier = 3 : send events 1, 3, 9, 27, 81, …; sendMultiplier = M : send events 1, M, M*M,…,M^n,… |
| resetIntervalSeconds | no | 600 | Interval after which the event count is reset. |
