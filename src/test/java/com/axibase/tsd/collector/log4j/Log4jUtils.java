package com.axibase.tsd.collector.log4j;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;

/**
 * @author Nikolay Malevanny.
 */
public class Log4jUtils {
    public static LoggingEvent createLoggingEvent(Level level, String loggerName, String message, String threadName) {
        LoggingEvent le = new LoggingEvent();
        le.setLevel(level);
        le.setLoggerName(loggerName);
        le.setMessage(message);
        le.setThreadName(threadName);
        le.setTimeStamp(System.currentTimeMillis());
        return le;
    }

    public static LoggingEvent createLoggingEvent(Level level,
                                                  String loggerName,
                                                  String message,
                                                  String threadName,
                                                  Throwable e) {
        final LoggerContext ctx = new LoggerContext();
        Logger logger = ctx.getLogger(loggerName);
        LoggingEvent loggingEvent = new LoggingEvent(null, logger, level, message, e, null);
        loggingEvent.setLoggerName(loggerName);
        return loggingEvent;
    }

    public static ILoggingEvent createLoggingEvent() {
        return createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread");
    }
}
