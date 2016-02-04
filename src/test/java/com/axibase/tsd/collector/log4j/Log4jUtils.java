/*
 * Copyright 2016 Axibase Corporation or its affiliates. All Rights Reserved.
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

package com.axibase.tsd.collector.log4j;


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
