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

package com.axibase.tsd.collector.log4j2;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

/**
 * @author Nikolay Malevanny.
 */
public class Log4j2Utils {
    public static LogEvent createLogEvent(Level level,
                                                  String loggerName,
                                                  String message,
                                                  String threadName) {
        final Log4jLogEvent.Builder builder = createBuilder(level, loggerName, message, threadName);
        return builder.build();
    }

    public static LogEvent createLogEvent(Level level,
                                                  String loggerName,
                                                  String message,
                                                  String threadName,
                                                  Throwable e) {
        final Log4jLogEvent.Builder builder = createBuilder(level, loggerName, message, threadName);
        builder.setThrown(e);
        return builder.build();
    }

    public static LogEvent createLogEvent() {
        return createLogEvent(Level.ERROR, "test-logger", "test-message", "test-thread");
    }

    private static Log4jLogEvent.Builder createBuilder(Level level,
                                                       String loggerName,
                                                       String message,
                                                       String threadName) {
        final Log4jLogEvent.Builder builder = Log4jLogEvent.newBuilder();
        builder.setLevel(level);
        builder.setThreadName(threadName);
        builder.setMessage(new SimpleMessage(message));
        builder.setLoggerName(loggerName);
        return builder;
    }
}
