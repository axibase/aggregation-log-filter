package com.axibase.tsd.collector.log4j;


import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * @author Nikolay Malevanny.
 */
public class Log4jUtils {
    public static LoggingEvent createLoggingEvent(Level level,
                                                  String loggerName,
                                                  String message,
                                                  String threadName) {
        LoggingEvent le = new LoggingEvent(loggerName,
                Logger.getLogger(loggerName),
                System.currentTimeMillis(),
                level,
                message,
                threadName,
                null,
                null,
                null,
                null);
        return le;
    }

    public static LoggingEvent createLoggingEvent(Level level,
                                                  String loggerName,
                                                  String message,
                                                  String threadName,
                                                  Throwable e) {
        LoggingEvent le = new LoggingEvent(loggerName,
                Logger.getLogger(loggerName),
                System.currentTimeMillis(),
                level,
                message,
                threadName,
                new ThrowableInformation(e),
                null,
                null,
                null);
        return le;
    }

    public static LoggingEvent createLoggingEvent() {
        return createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread");
    }
}
