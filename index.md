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