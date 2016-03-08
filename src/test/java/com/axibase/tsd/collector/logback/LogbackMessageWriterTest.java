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

package com.axibase.tsd.collector.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import com.axibase.tsd.collector.*;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogbackMessageWriterTest {

    @Test
    public void testBuildSingleStatMessage() throws Exception {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = createMessageBuilder();
        Map<String, EventCounter<Level>> events = new HashMap<String, EventCounter<Level>>();
        events.put("test-logger", createCounter(100, Level.ERROR));
        StringsCatcher catcher = new StringsCatcher();
        messageBuilder.writeStatMessages(catcher, events, 60000);
        String result = catcher.sb.toString();
        System.out.println("result = " + result);
        assertTrue(result.substring(0, result.length()).contains(
                "t:ttt1=vvv1 t:ttt2=vvv2 m:log_event_counter=100 t:level=ERROR t:logger=test-logger"));
    }

    @Test
    public void testBuildMultipleStatMessage() throws Exception {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = createMessageBuilder();
        messageBuilder.setSeriesSenderConfig(new SeriesSenderConfig(1, 30, -1));

        Map<String, EventCounter<Level>> events = new HashMap<String, EventCounter<Level>>();
        events.put("test-logger", createCounter(100, Level.ERROR, Level.WARN, Level.DEBUG));

        StringsCatcher catcher;
        {
            catcher = new StringsCatcher();
            messageBuilder.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            System.out.println("result0 = " + result);
            assertTrue(
                    result.contains("series e:test-entity"));
            assertTrue(
                    result.contains("t:ttt1=vvv1 t:ttt2=vvv2 m:log_event_counter=100 t:level="));
            assertTrue(result.contains("ERROR"));
            assertTrue(result.contains("WARN"));
            assertTrue(result.contains("DEBUG"));
            assertTrue(result.contains("m:log_event_counter=100 "));
            assertTrue(result.contains("m:log_event_total_rate=100.0 "));
            assertTrue(result.contains("m:log_event_total_counter=100 "));
        }

        {
            catcher.clear();
            events.clear();
            events.put("test-logger", createCounter(1, Level.ERROR));
            messageBuilder.writeStatMessages(catcher, events, 60000 );
            String result = catcher.sb.toString();
            System.out.println("result1 = " + result);
            assertTrue(result.contains("ERROR"));
            assertTrue(result.contains("WARN"));
            assertTrue(result.contains("DEBUG"));
            assertTrue(result.contains("m:log_event_counter=100"));
            assertTrue(result.contains("m:log_event_counter=101"));
            assertTrue(result.contains("m:log_event_total_rate=0.0"));
            assertTrue(result.contains("m:log_event_total_rate=1.0"));
            assertTrue(result.contains("m:log_event_total_counter=100"));
            assertTrue(result.contains("m:log_event_total_counter=101"));
        }
        {
            catcher.clear();
            events.clear();
            messageBuilder.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            System.out.println("result2 = " + result);
            assertTrue(result.contains("ERROR"));
            assertTrue(result.contains("WARN"));
            assertTrue(result.contains("DEBUG"));
            assertTrue(result.contains("m:log_event_counter=101"));
            assertTrue(result.contains("m:log_event_total_rate=0"));
            assertTrue(result.contains("m:log_event_total_counter=100"));
            assertTrue(result.contains("m:log_event_total_counter=101"));
        }
        {
            catcher.clear();
            events.clear();
            messageBuilder.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            System.out.println("result3 = " + result);
            assertTrue(result.contains("ERROR"));
            assertTrue(result.contains("WARN"));
            assertTrue(result.contains("DEBUG"));
            assertFalse(result.contains("m:log_event_rate=0"));
            assertTrue(result.contains("m:log_event_total_rate=0"));
            assertTrue(result.contains("m:log_event_total_counter=100"));
            assertTrue(result.contains("m:log_event_total_counter=101"));
        }
    }

    @Test
    public void testBuildSingleMessage() throws Exception {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = createMessageBuilder();
        LoggingEvent event = LogbackUtils.createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread");
        StringsCatcher catcher = new StringsCatcher();
        messageBuilder.writeSingles(catcher, createSingles(event, 0));
        String result = catcher.sb.toString();
        System.out.println("result = " + result);
        assertTrue(result.substring(0, result.length()).contains(
                "t:ttt1=vvv1 t:ttt2=vvv2 t:type=logger m:test-message t:severity=ERROR t:level=ERROR t:source=test-logger"));
    }

    @Test
    public void testBuildSingleMessageWithLines() throws Exception {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = createMessageBuilder();
        LoggingEvent event = LogbackUtils.createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread",
                new NullPointerException("test"));
        StringsCatcher catcher = new StringsCatcher();
        messageBuilder.writeSingles(catcher, createSingles(event, 10));
        String result = catcher.sb.toString();
        System.out.println("result = " + result);
        final String text = "t:ttt1=vvv1 t:ttt2=vvv2 t:type=logger m:\"test-message\n" +
                "java.lang.NullPointerException: test\n" +
                "\tat com.axibase.tsd.collector.logback.LogbackMessageWriterTest.testBuildSingleMessageWithLines(LogbackMessageWriterTest.java";
        assertTrue(result, result.contains(text));
    }

    private CountedQueue<EventWrapper<ILoggingEvent>> createSingles(LoggingEvent event, int lines) {
        CountedQueue<EventWrapper<ILoggingEvent>> singles = new CountedQueue<EventWrapper<ILoggingEvent>>();
        singles.add(new EventWrapper<ILoggingEvent>(event, lines, event.getMessage()));
        return singles;
    }

    private LogbackMessageWriter<ILoggingEvent> createMessageBuilder() {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = new LogbackMessageWriter<ILoggingEvent>();
        messageBuilder.setEntity("test-entity");
        SeriesSenderConfig seriesSenderConfig = new SeriesSenderConfig();
        messageBuilder.setSeriesSenderConfig(seriesSenderConfig);
        messageBuilder.addTag(new Tag("ttt1", "vvv1"));
        messageBuilder.addTag(new Tag("ttt2", "vvv2"));
        messageBuilder.start();
        return messageBuilder;
    }

    private SimpleCounter<Level> createCounter(int cnt, Level... levels) {
        SimpleCounter<Level> simpleCounter = new SimpleCounter<Level>();
        for (Level level : levels) {
            simpleCounter.updateAndGetDiff(level, cnt);
        }
        return simpleCounter;
    }

    private static class StringsCatcher implements WritableByteChannel {
        private StringBuilder sb = new StringBuilder();

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (src != null) {
                CharBuffer cb = AtsdUtil.UTF_8.decode(src);
                String string = cb.toString();
                sb.append(string);
                return string.length();
            } else {
                throw new IOException("src is null");
            }
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() throws IOException {

        }

        public void clear() {
            sb = new StringBuilder();
        }
    }
}