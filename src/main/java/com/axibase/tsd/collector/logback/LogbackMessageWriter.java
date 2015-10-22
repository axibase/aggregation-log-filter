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
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.spi.ContextAwareBase;
import com.axibase.tsd.collector.*;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Nikolay Malevanny.
 */
public class LogbackMessageWriter<E extends ILoggingEvent>
        extends ContextAwareBase
        implements MessageWriter<E, String, Level> {
    private Map<String, String> tags = new LinkedHashMap<String, String>();
    private String entity = AtsdUtil.resolveHostname();
    private final Map<Key, CounterWithSum> story = new HashMap<Key, CounterWithSum>();
    private ByteBuffer seriesCounterPrefix;
    private ByteBuffer seriesTotalRatePrefix;
    private ByteBuffer seriesTotalCounterPrefix;
    private ByteBuffer messagePrefix;
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;
    private final Map<Level, CounterWithSum> totals = new HashMap<Level, CounterWithSum>();

    @Override
    public void writeStatMessages(WritableByteChannel writer,
                                  Map<String, EventCounter<Level>> diff,
                                  long deltaTime) throws IOException {
        if (deltaTime < 1) {
            throw new IllegalArgumentException("Illegal delta tie value: " + deltaTime);
        }
        int repeatCount = seriesSenderConfig.getRepeatCount();

        // clean total counters
//        for (Iterator<Map.Entry<Level, CounterWithSum>> iterator = totals.entrySet().iterator(); iterator.hasNext(); ) {
//            Map.Entry<Level, CounterWithSum> entry = iterator.next();
//            if (entry.getValue().zeroRepeats < 0) {
//                iterator.remove();
//            }
//        }

        // decrement all previous zero repeat counters
        for (Counter counter : story.values()) {
            counter.decrementZeroRepeats();
        }

        // increment using new events
        for (Map.Entry<String, EventCounter<Level>> loggerAndCounter : diff.entrySet()) {
            EventCounter<Level> extCounter = loggerAndCounter.getValue();
            for (Map.Entry<Level, Long> levelAndCnt : extCounter.values()) {
                Key key = new Key(levelAndCnt.getKey(), loggerAndCounter.getKey());
                long v = levelAndCnt.getValue();
                CounterWithSum counter = story.get(key);
                if (counter == null) {
                    story.put(key, new CounterWithSum(v, repeatCount));
                } else {
                    counter.add(v);
                    counter.setZeroRepeats(repeatCount);
                }
            }
        }

        long time = System.currentTimeMillis();

        // compose & clean
        for (Iterator<Map.Entry<Key, CounterWithSum>> iterator = story.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Key, CounterWithSum> entry = iterator.next();
            CounterWithSum counter = entry.getValue();
            if (counter.zeroRepeats < 0) {
                iterator.remove();
            } else {
                Key key = entry.getKey();
                Level level = key.getLevel();
                long value = counter.value;
                counter.clean();
                try {
                    String levelString = level.toString();
                    writeCounter(writer, time, key, levelString, counter.sum);
                } catch (Throwable e) {
                    addError("Could not write series", e);
                } finally {
                    if (value > 0) {
                        CounterWithSum total = totals.get(level);
                        if (total == null) {
                            total = new CounterWithSum(value, repeatCount);
                            totals.put(level, total);
                        } else {
                            total.add(value);
//                            total.setZeroRepeats(repeatCount);
                        }
                    }
                }
            }
        }

        // send totals
        for (Map.Entry<Level, CounterWithSum> entry : totals.entrySet()) {
            Level level = entry.getKey();
            CounterWithSum counterWithSum = entry.getValue();
            try {
                // write total rate
                double rate = counterWithSum.value * (double) seriesSenderConfig.getRateIntervalMs() / deltaTime;
                String levelString = level.toString();
                writeTotalRate(writer, time, rate, levelString);
                counterWithSum.clean();
                // write total sum
                writeTotalCounter(writer, time, counterWithSum, levelString);
            } catch (Throwable e) {
                addError("Could not write series", e);
            } finally {
//                entry.getValue().decrementZeroRepeats();
            }
        }
    }

    private void writeCounter(WritableByteChannel writer,
                              long time,
                              Key key,
                              String levelString,
                              long value) throws IOException {
        StringBuilder sb = new StringBuilder().append(value);
        sb.append(" t:level=").append(levelString);
        sb.append(" t:logger=").append(AtsdUtil.sanitizeTagValue(key.getLogger()));
        sb.append(" ms:").append(time).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesCounterPrefix.remaining() + bytes.length)
                .put(seriesCounterPrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    private void writeTotalCounter(WritableByteChannel writer,
                                   long time,
                                   CounterWithSum counterWithSum,
                                   String levelString) throws IOException {
        StringBuilder sb = new StringBuilder().append(counterWithSum.sum);
        sb.append(" t:level=").append(levelString);
        sb.append(" ms:").append(time).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesTotalCounterPrefix.remaining() + bytes.length)
                .put(seriesTotalCounterPrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    private void writeTotalRate(WritableByteChannel writer,
                                long time,
                                double rate,
                                String levelString) throws IOException {
        StringBuilder sb = new StringBuilder().append(rate);
        sb.append(" t:level=").append(levelString);
        sb.append(" ms:").append(time).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesTotalRatePrefix.remaining() + bytes.length)
                .put(seriesTotalRatePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    @Override
    public void writeSingles(WritableByteChannel writer, CountedQueue<EventWrapper<E>> singles) throws IOException {
        EventWrapper<E> wrapper;
        while ((wrapper = singles.poll()) != null) {
            try {
                E event = wrapper.getEvent();
                StringBuilder sb = new StringBuilder();
                String message = event.getFormattedMessage();
                int lines = wrapper.getLines();
                if (lines > 0 && event.getCallerData() != null) {
                    StringBuilder msb = new StringBuilder(message);
                    IThrowableProxy throwableProxy = event.getThrowableProxy();
                    int s = 0;
                    while (throwableProxy!= null && s++ < lines) {
                        msb.append("\n").append(throwableProxy.getClassName())
                                .append(": ").append(throwableProxy.getMessage());
                        StackTraceElementProxy[] traceElementProxyArray = throwableProxy.getStackTraceElementProxyArray();
                        for (int i = 0; i < traceElementProxyArray.length && s < lines; i++, s++) {
                            StackTraceElementProxy traceElement = traceElementProxyArray[i];
                            msb.append("\n\t").append(traceElement.toString());
                        }
                        throwableProxy = throwableProxy.getCause();
                    }
                    message = msb.toString();
                }
                writeMessage(writer, event, sb, message);
            } catch (IOException e) {
                addError("Could not write message", e);
            }
        }
        singles.clearCount();
    }

    private void writeMessage(WritableByteChannel writer,
                              E event,
                              StringBuilder sb,
                              String message) throws IOException {
        sb.append(AtsdUtil.sanitizeMessage(message));
        sb.append(" t:severity=").append(event.getLevel());
        sb.append(" t:level=").append(event.getLevel());
        sb.append(" t:source=").append(AtsdUtil.sanitizeTagValue(event.getLoggerName()));
        sb.append(" ms:").append(System.currentTimeMillis()).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(messagePrefix.remaining() + bytes.length)
                .put(messagePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    @Override
    public void start() {
        String sanitizedEntity = AtsdUtil.sanitizeEntity(entity);
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(sanitizedEntity);
            appendTags(sb);
            sb.append(" m:").append(
                    AtsdUtil.sanitizeMetric(seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesCounterPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(sanitizedEntity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeMetric(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getRateSuffix())).append(
                    "=");
            seriesTotalRatePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(sanitizedEntity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeMetric(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesTotalCounterPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("message e:").append(sanitizedEntity);
            appendTags(sb);
            unsafeAppendTag(sb, "type", "logger");
            sb.append(" m:");
            messagePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }


    }

    private void appendTags(StringBuilder sb) {
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tagName = AtsdUtil.sanitizeTagKey(entry.getKey());
            String tagValue = AtsdUtil.sanitizeTagValue(entry.getValue());
            unsafeAppendTag(sb, tagName, tagValue);
        }
    }

    private void unsafeAppendTag(StringBuilder sb, String tagName, String tagValue) {
        sb.append(" t:").append(tagName).append("=").append(tagValue);
    }

    @Override
    public void stop() {
    }

    public void addTag(Tag tag) {
        tags.put(tag.getName(), tag.getValue());
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void setSeriesSenderConfig(SeriesSenderConfig seriesSenderConfig) {
        this.seriesSenderConfig = seriesSenderConfig;
    }

    private static class Counter {
        protected long value;
        protected int zeroRepeats;

        public Counter(long value, int zeroRepeats) {
            this.value = value;
            setZeroRepeats(zeroRepeats);
        }

        void increment() {
            value++;
        }

        public void add(long value) {
            this.value += value;
        }

        void decrementZeroRepeats() {
            zeroRepeats--;
        }

        public void setZeroRepeats(int zeroRepeats) {
            this.zeroRepeats = zeroRepeats;
        }

        public void clean() {
            value = 0;
        }
    }

    private static class CounterWithSum extends Counter {
        private long sum;

        public CounterWithSum(long value, int zeroRepeats) {
            super(value, zeroRepeats);
        }

        @Override
        public void clean() {
            sum += value;
            super.clean();
        }
    }
}
