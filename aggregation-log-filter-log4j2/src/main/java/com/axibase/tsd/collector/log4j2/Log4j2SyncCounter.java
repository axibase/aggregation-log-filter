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

import com.axibase.tsd.collector.EventCounter;
import com.axibase.tsd.collector.SimpleCounter;
import com.axibase.tsd.collector.SyncEventCounter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

class Log4j2SyncCounter implements SyncEventCounter<LogEvent, String> {
    private ConcurrentMap<String, AtomicLong> values = new ConcurrentHashMap<>();

    @Override
    public EventCounter<String> updateAndCreateDiff(EventCounter<String> lastCount) {
        EventCounter<String> result = null;
        for (Map.Entry<String, AtomicLong> entry : values.entrySet()) {
            String key = entry.getKey();
            AtomicLong cnt = entry.getValue();
            long diff = lastCount.updateAndGetDiff(key, cnt.get());
            if (diff > 0) {
                if (result == null) {
                    result = new SimpleCounter<>();
                }
                result.updateAndGetDiff(key, diff);
            }
        }
        return result;
    }

    @Override
    public void increment(LogEvent event) {
        Level level = event.getLevel();
        AtomicLong count = values.get(level.toString());
        if (count == null) {
            count = new AtomicLong(0);
            AtomicLong old = values.putIfAbsent(level.toString(), count);
            count = old == null ? count : old;
        }
        count.incrementAndGet();
    }
}
