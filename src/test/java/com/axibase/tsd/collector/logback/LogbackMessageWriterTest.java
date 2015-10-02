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
import ch.qos.logback.classic.spi.LoggingEvent;
import com.axibase.tsd.collector.*;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nikolay Malevanny.
 */
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
        Assert.assertEquals(
                "series e:test-entity t:ttt1=vvv1 t:ttt2=vvv2 m:test-metric_rate=100.0 t:level=ERROR t:logger=test-logger ",
                result.substring(0, result.indexOf("ms:1")));
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
            System.out.println("result = " + result);
            Assert.assertTrue(
                    result.contains("series e:test-entity t:ttt1=vvv1 t:ttt2=vvv2 m:test-metric_rate=100.0 t:level="));
            Assert.assertTrue(result.contains("ERROR"));
            Assert.assertTrue(result.contains("WARN"));
            Assert.assertTrue(result.contains("DEBUG"));
            Assert.assertTrue(result.contains("m:test-metric_rate=100.0 "));
            Assert.assertTrue(result.contains("m:test-metric_total_rate=100.0 "));
            Assert.assertTrue(result.contains("m:test-metric_total_counter=100 "));
        }

        {
            catcher.clear();
            events.clear();
            events.put("test-logger", createCounter(1, Level.ERROR));
            messageBuilder.writeStatMessages(catcher, events, 60000 );
            String result = catcher.sb.toString();
            System.out.println("result = " + result);
            Assert.assertTrue(result.contains("ERROR"));
            Assert.assertTrue(result.contains("WARN"));
            Assert.assertTrue(result.contains("DEBUG"));
            Assert.assertTrue(result.contains("m:test-metric_rate=0.0"));
            Assert.assertTrue(result.contains("m:test-metric_rate=1.0"));
            Assert.assertTrue(result.contains("m:test-metric_total_rate=0.0"));
            Assert.assertTrue(result.contains("m:test-metric_total_rate=1.0"));
            Assert.assertTrue(result.contains("m:test-metric_total_counter=100"));
            Assert.assertTrue(result.contains("m:test-metric_total_counter=101"));
        }
        {
            catcher.clear();
            events.clear();
            messageBuilder.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            Assert.assertTrue(result.contains("ERROR"));
            Assert.assertFalse(result.contains("WARN"));
            Assert.assertFalse(result.contains("DEBUG"));
            Assert.assertTrue(result.contains("m:test-metric_rate=0"));
            Assert.assertTrue(result.contains("m:test-metric_total_rate=0"));
            Assert.assertFalse(result.contains("m:test-metric_total_counter=100"));
            Assert.assertTrue(result.contains("m:test-metric_total_counter=101"));
        }
        {
            catcher.clear();
            events.clear();
            messageBuilder.writeStatMessages(catcher, events, 60000);
            String result = catcher.sb.toString();
            Assert.assertEquals("", result);
        }
    }

    @Test
    public void testBuildSingleMessage() throws Exception {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = createMessageBuilder();
        LoggingEvent event = TestUtils.createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread");
        StringsCatcher catcher = new StringsCatcher();
        messageBuilder.writeSingles(catcher, createSingles(event, 0));
        String result = catcher.sb.toString();
        Assert.assertEquals(
                "message e:test-entity t:ttt1=vvv1 t:ttt2=vvv2 t:type=logger m:test-message t:severity=ERROR t:level=ERROR t:source=test-logger ",
                result.substring(0, result.indexOf("ms:1")));
    }

    @Test
    public void testBuildSingleMessageWithLines() throws Exception {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = createMessageBuilder();
        LoggingEvent event = TestUtils.createLoggingEvent(Level.ERROR, "test-logger", "test-message", "test-thread",
                new NullPointerException("test"));
        StringsCatcher catcher = new StringsCatcher();
        messageBuilder.writeSingles(catcher, createSingles(event, 10));
        String result = catcher.sb.toString();
        System.out.println("result = " + result);
        Assert.assertEquals(
                "message e:test-entity t:ttt1=vvv1 t:ttt2=vvv2 t:type=logger m:\"test-message\n" +
                        "java.lang.NullPointerException: test\n" +
                        "\tat com.axibase.tsd.collector.logback.LogbackMessageWriterTest.testBuildSingleMessageWithLines(LogbackMessageWriterTest.java:132)\n" +
                        "\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                        "\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)\n" +
                        "\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
                        "\tat java.lang.reflect.Method.invoke(Method.java:597)\n" +
                        "\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:45)\n" +
                        "\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:15)\n" +
                        "\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:42)\n" +
                        "\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:20)\" t:severity=ERROR t:level=ERROR t:source=test-logger ",
                result.substring(0, result.indexOf("ms:1")));
    }

    private CountedQueue<EventWrapper<ILoggingEvent>> createSingles(LoggingEvent event, int lines) {
        CountedQueue<EventWrapper<ILoggingEvent>> singles = new CountedQueue<EventWrapper<ILoggingEvent>>();
        singles.add(new EventWrapper<ILoggingEvent>(event, lines));
        return singles;
    }

    private LogbackMessageWriter<ILoggingEvent> createMessageBuilder() {
        LogbackMessageWriter<ILoggingEvent> messageBuilder = new LogbackMessageWriter<ILoggingEvent>();
        messageBuilder.setEntity("test-entity");
        SeriesSenderConfig seriesSenderConfig = new SeriesSenderConfig();
        seriesSenderConfig.setMetric("test-metric");
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