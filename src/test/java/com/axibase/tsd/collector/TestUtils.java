/*
 * Copyright 2015 Axibase Corporation or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * https://www.axibase.com/atsd/axibase-apache-2.0.pdf
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.axibase.tsd.collector;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;import java.lang.String;import java.lang.System;import java.lang.Throwable;

/**
 * @author Nikolay Malevanny.
 */
public class TestUtils {
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
