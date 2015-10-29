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
    <repeatCount>5</repeatCount>
    <!-- default: 60 -->
    <intervalSeconds>1</intervalSeconds>
    <!-- default: 0 -->
    <minIntervalThreshold>10</minIntervalThreshold>
    <!-- default: 5 -->
    <minIntervalSeconds>0</minIntervalSeconds>
</sendSeries>
```

| Name | Required | Default Value | Description |
|---|---|---|---|
| metricPrefix | no | log_event  | metric names prefix  |
| repeatCount | no | 1 | count of zero values after the last significant events |
| intervalSeconds | no | 60 | the interval of sending collected log statistics (seconds) |
| rateIntervalSeconds | no | 60 | interval to calculate rate (seconds)|
| minIntervalSeconds | no | 5 | minimum interval between sending of statistics (seconds), in case `minIntervalThreshold` is triggered|
| minIntervalThreshold | no | 0 | initiates sending of statistics before `intervalSeconds` is completed, useful to decrease latency |


## sendMessage

Log example selection configuration to send them as messages to ATSD.

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
| level | no | WARN | minimum level to send message |
| stackTraceLines | no | 0 | count of stack trace line that will be included in the message, -1 -- all lines |
| sendMultiplier | no | 2.0 for ERROR; 3.0 for WARN; 5.0 for INFO | determines which messages are sent within the period; skipMultiplier = 2 : send the following messages 1, 2, 4, 8, …, n, n*2 within each period; skipMultiplier = 3 : send the following messages 1, 3, 9, 27, …, n, n*3 within each period; skipMultiplier = M : send the following messages 1, 1*M, …, n, n*M within each period |
| resetIntervalSeconds | no | 600 | interval when message count is reset |
