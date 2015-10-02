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
import java.util.*;

/**
 * @author Nikolay Malevanny.
 */
public class LogbackMessageWriter<E extends ILoggingEvent>
        extends ContextAwareBase
        implements MessageWriter<E, String, Level> {
    private Map<String, String> tags = new LinkedHashMap<String, String>();
    private String entity = AtsdUtil.resolveHostname();
    private final Map<Key, Counter> story = new HashMap<Key, Counter>();
    private ByteBuffer seriesRatePrefix;
    private ByteBuffer seriesTotalRatePrefix;
    private ByteBuffer seriesTotalSumPrefix;
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
        int zeroRepeatCount = seriesSenderConfig.getZeroRepeatCount();

        // clean total counters
        for (Iterator<Map.Entry<Level, CounterWithSum>> iterator = totals.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Level, CounterWithSum> entry = iterator.next();
            if (entry.getValue().zeroRepeats < 0) {
                iterator.remove();
            }
        }

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
                Counter counter = story.get(key);
                if (counter == null) {
                    story.put(key, new Counter(v, zeroRepeatCount));
                } else {
                    counter.add(v);
                    counter.setZeroRepeats(zeroRepeatCount);
                }
            }
        }

        long time = System.currentTimeMillis();

        // compose & clean
        for (Iterator<Map.Entry<Key, Counter>> iterator = story.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Key, Counter> entry = iterator.next();
            Counter counter = entry.getValue();
            if (counter.zeroRepeats < 0) {
                iterator.remove();
            } else {
                Key key = entry.getKey();
                Level level = key.getLevel();
                long value = counter.value;
                try {
                    writer.write(seriesRatePrefix.duplicate());
                    StringBuilder sb = new StringBuilder();
                    String levelString = level.toString();
                    double rate = value * (double) seriesSenderConfig.getRatePeriodMs() / deltaTime;
                    sb.append(rate);
                    sb.append(" t:level=").append(levelString);
                    sb.append(" t:logger=").append(AtsdUtil.sanitizeTagValue(key.getLogger()));
                    sb.append(" ms:").append(time).append("\n");
                    writer.write(ByteBuffer.wrap(sb.toString().getBytes()));
                } catch (Throwable e) {
                    addError("Could not write series", e);
                } finally {
                    if (value > 0) {
                        CounterWithSum total = totals.get(level);
                        if (total == null) {
                            total = new CounterWithSum(value, zeroRepeatCount);
                            totals.put(level, total);
                        } else {
                            total.add(value);
                            total.setZeroRepeats(zeroRepeatCount);
                        }
                    }
                    counter.clean();
                }
            }
        }

        // send totals
        for (Map.Entry<Level, CounterWithSum> entry : totals.entrySet()) {
            Level level = entry.getKey();
            CounterWithSum counterWithSum = entry.getValue();
            try {
                // write total rate
                writer.write(seriesTotalRatePrefix.duplicate());
                StringBuilder sb = new StringBuilder();
                String levelString = level.toString();
                double rate = counterWithSum.value * (double) seriesSenderConfig.getRatePeriodMs() / deltaTime;
                sb.append(rate);
                sb.append(" t:level=").append(levelString);
                sb.append(" ms:").append(time).append("\n");
                writer.write(ByteBuffer.wrap(sb.toString().getBytes()));
                counterWithSum.clean();
                // write total sum
                writer.write(seriesTotalSumPrefix.duplicate());
                sb = new StringBuilder();
                sb.append(counterWithSum.sum);
                sb.append(" t:level=").append(levelString);
                sb.append(" ms:").append(time).append("\n");
                writer.write(ByteBuffer.wrap(sb.toString().getBytes()));
            } catch (Throwable e) {
                addError("Could not write series", e);
            } finally {
                entry.getValue().decrementZeroRepeats();
            }
        }
    }

    @Override
    public void writeSingles(WritableByteChannel writer, CountedQueue<EventWrapper<E>> singles) throws IOException {
        EventWrapper<E> wrapper;
        while ((wrapper = singles.poll()) != null) {
            try {
                E event = wrapper.getEvent();
                writer.write(messagePrefix.duplicate());
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
                sb.append(AtsdUtil.sanitizeMessage(message));
                sb.append(" t:severity=").append(event.getLevel());
                sb.append(" t:level=").append(event.getLevel());
                sb.append(" t:source=").append(AtsdUtil.sanitizeTagValue(event.getLoggerName()));
                sb.append(" ms:").append(System.currentTimeMillis()).append("\n");
                writer.write(ByteBuffer.wrap(sb.toString().getBytes()));
            } catch (IOException e) {
                addError("Could not write message", e);
            }
        }
        singles.clearCount();
    }

    @Override
    public void start() {
        String sanitizedEntity = AtsdUtil.sanitizeEntity(entity);
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(sanitizedEntity);
            appendTags(sb);
            sb.append(" m:").append(
                    AtsdUtil.sanitizeMetric(seriesSenderConfig.getMetric() + seriesSenderConfig.getRateSuffix())).append(
                    "=");
            seriesRatePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(sanitizedEntity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeMetric(
                    seriesSenderConfig.getMetric() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getRateSuffix())).append(
                    "=");
            seriesTotalRatePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(sanitizedEntity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeMetric(
                    seriesSenderConfig.getMetric() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesTotalSumPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
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
