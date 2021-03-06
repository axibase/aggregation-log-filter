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

import com.axibase.tsd.collector.*;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class Log4jMessageWriterTest {

    @Test
    public void testBuildSingleStatMessage() throws Exception {
        StringsCatcher catcher = new StringsCatcher();
        Log4jMessageWriter messageBuilder = createMessageBuilder(catcher);
        Map<String, EventCounter<String>> events = new HashMap<String, EventCounter<String>>();
        events.put("test-logger", createCounter(100, Level.ERROR.toString()));
        messageBuilder.writeStatMessages(catcher, events, 60000);
        String result = catcher.sb.toString();
        System.out.println("result = " + result);
        assertTrue(result.substring(0, result.length()).contains(
                "t:ttt1=vvv1 t:ttt2=vvv2 m:log_event_counter=100 t:level=ERROR t:logger=test-logger"));
    }

    @Test
    public void testBuildMultipleStatMessage() throws Exception {
        StringsCatcher catcher;
        Log4jMessageWriter log4jMessageWriter;
        Map<String, EventCounter<String>> events = new HashMap<String, EventCounter<String>>();

        {
            catcher = new StringsCatcher();
            log4jMessageWriter = createMessageBuilder(catcher);
            log4jMessageWriter.setSeriesSenderConfig(new SeriesSenderConfig(1, 30, -1));
            events.put("test-logger", createCounter(100, Level.ERROR.toString(), Level.WARN.toString(), Level.DEBUG.toString()));

            log4jMessageWriter.writeStatMessages(catcher, events, 60000);
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
//            assertTrue(result.contains("m:log_event_total_rate=100.0 "));
            assertTrue(result.contains("m:log_event_total_counter=100 "));
        }

        {
            catcher.clear();
            events.clear();
            events.put("test-logger", createCounter(1, Level.ERROR.toString()));
            log4jMessageWriter.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            System.out.println("result1 = " + result);
            assertTrue(result.contains("ERROR"));
            assertTrue(result.contains("WARN"));
            assertTrue(result.contains("DEBUG"));
            assertTrue(result.contains("m:log_event_counter=100"));
            assertTrue(result.contains("m:log_event_counter=101"));
//            assertTrue(result.contains("m:log_event_total_rate=0.0"));
//            assertTrue(result.contains("m:log_event_total_rate=1.0"));
            assertTrue(result.contains("m:log_event_total_counter=100"));
            assertTrue(result.contains("m:log_event_total_counter=101"));
        }
        {
            catcher.clear();
            events.clear();
            log4jMessageWriter.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            System.out.println("result2 = " + result);
            assertTrue(result.contains("ERROR"));
            assertTrue(result.contains("WARN"));
            assertTrue(result.contains("DEBUG"));
            assertTrue(result.contains("m:log_event_counter=101"));
//            assertTrue(result.contains("m:log_event_total_rate=0"));
            assertTrue(result.contains("m:log_event_total_counter=100"));
            assertTrue(result.contains("m:log_event_total_counter=101"));
        }
        {
            catcher.clear();
            events.clear();
            log4jMessageWriter.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            System.out.println("result3 = " + result);
            assertTrue(result.contains("ERROR"));
            assertTrue(result.contains("WARN"));
            assertTrue(result.contains("DEBUG"));
            assertTrue(result.contains("m:log_event_total_counter=100"));
            assertTrue(result.contains("m:log_event_total_counter=101"));
        }
    }

    public void testBuildSingleMessage() throws Exception {
        StringsCatcher catcher = new StringsCatcher();
        Log4jMessageWriter messageBuilder = createMessageBuilder(catcher);
        LoggingEvent event = Log4jUtils.createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread");
        messageBuilder.writeSingles(catcher, createSingles(event, 0));
        String result = catcher.sb.toString();
        assertTrue(result.substring(0, result.length()).contains(
                "t:ttt1=vvv1 t:ttt2=vvv2 t:type=logger m:test-message t:severity=ERROR t:level=ERROR t:source=test-logger"));
    }

    public void testBuildSingleMessageWithLines() throws Exception {
        StringsCatcher catcher = new StringsCatcher();
        Log4jMessageWriter messageBuilder = createMessageBuilder(catcher);
        LoggingEvent event = Log4jUtils.createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread",
                new NullPointerException("test"));
        messageBuilder.writeSingles(catcher, createSingles(event, 10));
        String result = catcher.sb.toString();
        System.out.println("result = " + result);
        final String text = "t:ttt1=vvv1 t:ttt2=vvv2 t:type=logger m:\"test-message\n" +
                "java.lang.NullPointerException: test\n" +
                "\tjava.lang.NullPointerException: test\n" +
                "\t\tat com.axibase.tsd.collector.log4j.Log4jMessageWriterTest.testBuildSingleMessageWithLines";
        assertTrue(result, result.contains(text));
    }

    private CountedQueue<EventWrapper<LoggingEvent>> createSingles(LoggingEvent event, int lines) {
        CountedQueue<EventWrapper<LoggingEvent>> singles = new CountedQueue<EventWrapper<LoggingEvent>>();
        singles.add(new EventWrapper<LoggingEvent>(event, lines, event.getRenderedMessage(),null));
        return singles;
    }

    private Log4jMessageWriter createMessageBuilder(WritableByteChannel writer) {
        Log4jMessageWriter log4jMessageWriter = new Log4jMessageWriter();
        log4jMessageWriter.setEntity("test-entity");
        SeriesSenderConfig seriesSenderConfig = new SeriesSenderConfig();
        log4jMessageWriter.setSeriesSenderConfig(seriesSenderConfig);
        log4jMessageWriter.addTag(new Tag("ttt1", "vvv1"));
        log4jMessageWriter.addTag(new Tag("ttt2", "vvv2"));
        log4jMessageWriter.start(writer, Level.TRACE_INT, 60, new HashMap<String, String>());
        return log4jMessageWriter;
    }

    private SimpleCounter<String> createCounter(int cnt, String... levels) {
        SimpleCounter<String> simpleCounter = new SimpleCounter<String>();
        for (String level : levels) {
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