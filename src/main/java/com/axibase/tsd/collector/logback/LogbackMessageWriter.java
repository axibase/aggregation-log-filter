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
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.spi.ContextAwareBase;
import com.axibase.tsd.collector.*;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;

import java.io.IOException;
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
    private final Map<Key<Level>, CounterWithSum> story = new HashMap<Key<Level>, CounterWithSum>();
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;
    private final Map<Level, CounterWithSum> totals = new HashMap<Level, CounterWithSum>();
    private MessageHelper messageHelper = new MessageHelper();
    private PatternLayout patternLayout = null;
    private String pattern;

    @Override
    public void writeStatMessages(WritableByteChannel writer,
                                  Map<String, EventCounter<Level>> diff,
                                  long deltaTime) throws IOException {
        if (deltaTime < 1) {
            throw new IllegalArgumentException("Illegal delta tie value: " + deltaTime);
        }
        int repeatCount = seriesSenderConfig.getRepeatCount();

        // decrement all previous zero repeat counters
        for (Counter counter : story.values()) {
            counter.decrementZeroRepeats();
        }

        // increment using new events
        for (Map.Entry<String, EventCounter<Level>> loggerAndCounter : diff.entrySet()) {
            EventCounter<Level> extCounter = loggerAndCounter.getValue();
            for (Map.Entry<Level, Long> levelAndCnt : extCounter.values()) {
                Key<Level> key = new Key<Level>(levelAndCnt.getKey(), loggerAndCounter.getKey());
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
        for (Iterator<Map.Entry<Key<Level>, CounterWithSum>> iterator = story.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Key<Level>, CounterWithSum> entry = iterator.next();
            CounterWithSum counter = entry.getValue();
            if (counter.getZeroRepeats() < 0) {
                // iterator.remove(); //#2132 counter should not reset log_event_counter to 0 after repeatCount
            } else {
                Key<Level> key = entry.getKey();
                Level level = key.getLevel();
                long value = counter.getValue();
                counter.clean();
                try {
                    String levelString = level.toString();
                    messageHelper.writeCounter(writer, time, key, levelString, counter.getSum());
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
                double rate = counterWithSum.getValue() * (double) seriesSenderConfig.getRateIntervalMs() / deltaTime;
                String levelString = level.toString();
                messageHelper.writeTotalRate(writer, time, rate, levelString);
                counterWithSum.clean();
                // write total sum
                messageHelper.writeTotalCounter(writer, time, counterWithSum, levelString);
            } catch (Throwable e) {
                addError("Could not write series", e);
            } finally {
//                entry.getValue().decrementZeroRepeats();
            }
        }
    }

    @Override
    public void writeSingles(WritableByteChannel writer, CountedQueue<EventWrapper<E>> singles) throws IOException {
        EventWrapper<E> wrapper;
        while ((wrapper = singles.poll()) != null) {
            try {
                E event = wrapper.getEvent();
                StringBuilder sb = new StringBuilder();
                String message = wrapper.getMessage();
                int lines = wrapper.getLines();
                if (lines > 0 && event.getCallerData() != null) {
                    StringBuilder msb = new StringBuilder(message);
                    IThrowableProxy throwableProxy = event.getThrowableProxy();
                    int s = 0;
                    while (throwableProxy != null && s++ < lines) {
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
        final String levelValue = event.getLevel().toString();
        final String loggerName = event.getLoggerName();
        messageHelper.writeMessage(writer, sb, message, levelValue, loggerName);
    }

    @Override
    public void start() {
        messageHelper.setSeriesSenderConfig(seriesSenderConfig);
        messageHelper.setEntity(AtsdUtil.sanitizeEntity(entity));
        messageHelper.setTags(tags);
        messageHelper.init();

        Map<String, Integer> initMap = seriesSenderConfig.getTotalCountInitMap();
        for (Map.Entry<String, Integer> levelAndValue : initMap.entrySet()) {
            Level level = Level.toLevel(levelAndValue.getKey(), null);
            if (level != null && levelAndValue.getValue() != null && levelAndValue.getValue() >= 0) {
                totals.put(level,
                        new CounterWithSum(levelAndValue.getValue(), seriesSenderConfig.getRepeatCount()));
            }
        }

        if (pattern != null) {
            patternLayout = new PatternLayout();
            patternLayout.setContext(context);
            patternLayout.setPattern(pattern);
            patternLayout.start();
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public EventWrapper<E> createWrapper(E event, int lines) {
        String message;
        if (patternLayout == null) {
            message = event.getFormattedMessage();
        } else {
            message = patternLayout.doLayout(event);
        }
        return new EventWrapper<E>(event, lines, message);
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

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
