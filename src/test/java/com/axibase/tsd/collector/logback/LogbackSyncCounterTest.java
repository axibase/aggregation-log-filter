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

package com.axibase.tsd.collector.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.axibase.tsd.collector.EventCounter;
import com.axibase.tsd.collector.SimpleCounter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Nikolay Malevanny.
 */
public class LogbackSyncCounterTest {
    @Test
    public void testUpdateAndCreateDiff() throws Exception {
        LogbackSyncCounter<ILoggingEvent> counter = new LogbackSyncCounter<ILoggingEvent>();
        int cnt = 15;
        for (int i = 0; i < cnt; i++) {
            counter.increment(LogbackUtils.createLoggingEvent());
        }
        SimpleCounter<Level> simpleCounter = new SimpleCounter<Level>();
        EventCounter<Level> diff = counter.updateAndCreateDiff(simpleCounter);
        assertEquals(1, simpleCounter.values().size());
        assertEquals(15, (long) simpleCounter.values().iterator().next().getValue());
        assertEquals(1, diff.values().size());
        assertEquals(15, (long) diff.values().iterator().next().getValue());
        counter.increment(LogbackUtils.createLoggingEvent());
        diff = counter.updateAndCreateDiff(simpleCounter);
        assertEquals(1, simpleCounter.values().size());
        assertEquals(16, (long) simpleCounter.values().iterator().next().getValue());
        assertEquals(1, diff.values().size());
        assertEquals(1, (long) diff.values().iterator().next().getValue());
    }
}