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

import com.axibase.tsd.collector.EventCounter;
import com.axibase.tsd.collector.EventProcessor;
import com.axibase.tsd.collector.SimpleCounter;
import com.axibase.tsd.collector.SyncEventCounter;
import org.apache.log4j.spi.LoggingEvent;

/**
 * @author Nikolay Malevanny.
 */
public class Log4jEventProcessor
        implements EventProcessor <LoggingEvent, String, String> {
    @Override
    public SyncEventCounter<LoggingEvent, String> createSyncCounter() {
        return new Log4jSyncCounter();
    }

    @Override
    public EventCounter<String> createCounter() {
        return new SimpleCounter<String>();
    }

    @Override
    public String extractKey(LoggingEvent event) {
        return event.getLoggerName();
    }
}
