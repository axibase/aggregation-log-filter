# Aggregation Log Filter

For developers of major systems a typical situation is that with the system's growth, new functionality and new system logs are added. Under certain circumstances, there are failures in the systems, which are written into the logs. When applications run on multiple servers, the task of monitoring service status is complicated.

Log statistics collector is used to gather statistics on errors and warnings in log files and selectively send messages from the logs for fast browsing (no need to go to the server and examine the logs in detail), without any modifications to the application's source code.

Using the collected statistics you can monitor the stability of applications on different servers in the long term. You can configure rules to be notified of application errors, which will reduce the time it takes to eliminate the errors.

```xml 
       <filter class="com.axibase.tsd.collector.logback.Collector">
            <writer class="com.axibase.tsd.collector.writer.HttpStreamingAtsdWriter">
                <url>http://localhost:8088/api/v1/command/</url>
                <username>USERNAME</username>
                <password>PASSWORD</password>
            </writer>
            <level>INFO</level>
            <sendSeries>
                <zeroRepeatCount>3</zeroRepeatCount>
                <metric>log_event</metric>
                <periodSeconds>60</periodSeconds>
                <sendThreshold>100</sendThreshold>
                <minPeriodSeconds>1</minPeriodSeconds>
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
| level | no | TRACE | minimum level to process event |
| entity | no | current hostname | entity name for series and messages |
| tag | no | - | custom tag(s) to attach to series and messages, MULTIPLE |
| writer | yes | - | see `writer` config |
| sendSeries | yes | - | see `sendSeries` config |
| sendMessage | no | - | see `sendMessage` config, MULTIPLE |


## writer

Data transmission configuration that will be used to send statistics and messages to ATSD.

### TCP writer

```xml
<writer class="com.axibase.tsd.collector.writer.TcpAtsdWriter">
    <host>localhost</host>
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
    <host>localhost</host>
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
    <url>http://localhost:8088/api/v1/command/</url>
    <username>axibase</username>
    <password>*****</password>
</writer>
```

| Name | Required | Default | Description |
|---|---|---|---|
| url | yes | - | ATSD API command URL like 'http://localhost:8088/api/v1/command/', string |
| username | yes | - | user name, string |
| password | yes | - | password, string |


## sendSeries

Log aggregation configuration to generate statistics and send them to ATSD.

```xml
<sendSeries>
    <!-- 0+ default:1 -->
    <zeroRepeatCount>5</zeroRepeatCount>
    <!-- default: log_event -->
    <metric>log_event</metric>
    <!-- default: _total -->
    <totalSuffix>_sum</totalSuffix>
    <!-- default: 60 -->
    <periodSeconds>1</periodSeconds>
    <!-- default: 0 -->
    <sendThreshold>10</sendThreshold>
    <!-- default: 5 -->
    <minPeriodSeconds>0</minPeriodSeconds>
</sendSeries>
```

| Name | Required | Default Value | Description |
|---|---|---|---|
| metric | no | log_event  | metric names prefix  |
| rateSuffix | no | _rate  | `rate` metric name suffix  |
| totalSuffix | no | _total  | `total` metric name suffix  |
| counterSuffix | no | _counter  | `counter` metric suffix  |
| zeroRepeatCount | no | 1 | count of zero values after the last significant events |
| periodSeconds | no | 60 | the period of sending collected log statistics (seconds) |
| ratePeriodSeconds | no | 60 | period to calculate rate (seconds)|
| minPeriodSeconds | no | 5 | minimum period between sending of statistics (seconds), in case `sendThreshold` is triggered|
| sendThreshold | no | 0 | initiates sending of statistics before `periodSeconds` is completed, useful to decrease latency |
| messageSkipThreshold | no | 100 | remove oldest message from the internal queue if queue size more than `messageSkipThreshold` |


## sendMessage

Log example selection configuration to send them as messages to ATSD.

```xml
<sendMessage>
    <every>1000</every>
</sendMessage>
<sendMessage>
    <level>ERROR</level>
    <every>10</every>
    <stackTraceLines>15</stackTraceLines>
</sendMessage>
```

| Name | Required | Default Value | Description |
|---|---|---|---|
| level | no | WARN | minimum level to send message |
| every | yes | - | one of every n log events will be sent to ATSD as message |
| stackTraceLines | no | 0 | count of stack trace line that will be included in the message, -1 -- all lines |
