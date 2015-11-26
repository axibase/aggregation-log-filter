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

package com.axibase.tsd.collector.log4j;

import com.axibase.tsd.collector.EventCounter;
import com.axibase.tsd.collector.SimpleCounter;
import com.axibase.tsd.collector.SyncEventCounter;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nikolay Malevanny.
 */
class Log4jSyncCounter implements SyncEventCounter<LoggingEvent, Level> {
    private ConcurrentMap<Level, AtomicLong> values = new ConcurrentHashMap<Level, AtomicLong>();

    @Override
    public EventCounter<Level> updateAndCreateDiff(EventCounter<Level> lastCount) {
        EventCounter<Level> result = null;
        for (Map.Entry<Level, AtomicLong> entry : values.entrySet()) {
            Level key = entry.getKey();
            AtomicLong cnt = entry.getValue();
            long diff = lastCount.updateAndGetDiff(key, cnt.get());
            if (diff > 0) {
                if (result == null) {
                    result = new SimpleCounter<Level>();
                }
                result.updateAndGetDiff(key, diff);
            }
        }
        return result;
    }

    @Override
    public void increment(LoggingEvent event) {
        Level level = event.getLevel();
        AtomicLong count = values.get(level);
        if (count == null) {
            count = new AtomicLong(0);
            AtomicLong old = values.putIfAbsent(level, count);
            count = old == null ? count : old;
        }
        count.incrementAndGet();
    }
}
