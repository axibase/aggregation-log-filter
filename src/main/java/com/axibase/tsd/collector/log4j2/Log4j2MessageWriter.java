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

import com.axibase.tsd.collector.*;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.*;

/**
 * @author Nikolay Malevanny.
 */
public class Log4j2MessageWriter implements MessageWriter<LogEvent, String, String> {
    private Map<String, String> tags = new LinkedHashMap<String, String>();
    private String entity = AtsdUtil.resolveHostname();
    private final Map<Key<String>, CounterWithSum> story = new HashMap<Key<String>, CounterWithSum>();
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;
    private final Map<String, CounterWithSum> totals = new HashMap<String, CounterWithSum>();
    private MessageHelper messageHelper = new MessageHelper();
    private String pattern;
    private List<PatternFormatter> formatters;

    @Override
    public void writeStatMessages(WritableByteChannel writer,
                                  Map<String, EventCounter<String>> diff,
                                  long deltaTime) throws IOException {
        if (deltaTime < 1) {
            throw new IllegalArgumentException("Illegal delta time value: " + deltaTime);
        }
        int repeatCount = seriesSenderConfig.getRepeatCount();

        // decrement all previous zero repeat counters
        for (Counter counter : story.values()) {
            counter.decrementZeroRepeats();
        }

        // increment using new events
        for (Map.Entry<String, EventCounter<String>> loggerAndCounter : diff.entrySet()) {
            EventCounter<String> extCounter = loggerAndCounter.getValue();
            for (Map.Entry<String, Long> levelAndCnt : extCounter.values()) {
                Key<String> key = new Key<String>(levelAndCnt.getKey(), loggerAndCounter.getKey());
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
        for (Iterator<Map.Entry<Key<String>, CounterWithSum>> iterator = story.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Key<String>, CounterWithSum> entry = iterator.next();
            CounterWithSum counter = entry.getValue();
            if (counter.getZeroRepeats() < 0) {
                // iterator.remove(); //#2132 counter should not reset log_event_counter to 0 after repeatCount
            } else {
                Key<String> key = entry.getKey();
                String level = key.getLevel();
                long value = counter.getValue();
                counter.clean();
                try {
                    messageHelper.writeCounter(writer, time, key, level, counter.getSum());
                } catch (Throwable e) {
                    AtsdUtil.logInfo("Could not write series", e);
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
        for (Map.Entry<String, CounterWithSum> entry : totals.entrySet()) {
            String level = entry.getKey();
            CounterWithSum counterWithSum = entry.getValue();
            try {
                // write total rate
                double rate = counterWithSum.getValue() * (double) seriesSenderConfig.getRateIntervalMs() / deltaTime;
                messageHelper.writeTotalRate(writer, time, rate, level);
                counterWithSum.clean();
                // write total count
                messageHelper.writeTotalCounter(writer, time, counterWithSum, level);
            } catch (Throwable e) {
                AtsdUtil.logInfo("Could not write series", e);
            } finally {
//                entry.getValue().decrementZeroRepeats();
            }
        }
    }

    @Override
    public void writeSingles(WritableByteChannel writer,
                             CountedQueue<EventWrapper<LogEvent>> singles) throws IOException {
        EventWrapper<LogEvent> wrapper;
        while ((wrapper = singles.poll()) != null) {
            try {
                LogEvent event = wrapper.getEvent();
                StringBuilder sb = new StringBuilder();
                String message = wrapper.getMessage();
                int lines = wrapper.getLines();
                final ThrowableProxy tp = event.getThrownProxy();
                if (lines > 0 && tp != null) {
                    StringBuilder msb = new StringBuilder(message);
                    int s = 0;
                    final Throwable throwableProxy = tp.getThrowable();
                    msb.append("\n").append(throwableProxy.getClass().getName())
                            .append(": ").append(throwableProxy.getMessage());
                    ExtendedStackTraceElement[] traceElementProxyArray = tp.getExtendedStackTrace();
                    if (traceElementProxyArray != null) {
                        for (int i = 0; i < traceElementProxyArray.length && s < lines; i++, s++) {
                            msb.append("\n\t").append(traceElementProxyArray[i].toString());
                        }
                    }
                    message = msb.toString();
                }
                writeMessage(writer, event, sb, message);
            } catch (Exception e) {
                AtsdUtil.logInfo("Could not write message", e);
            }
        }
        singles.clearCount();
    }

    private void writeMessage(WritableByteChannel writer,
                              LogEvent event,
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
            String level = levelAndValue.getKey().toUpperCase();
            if (levelAndValue.getValue() != null && levelAndValue.getValue() >= 0) {
                totals.put(level,
                        new CounterWithSum(levelAndValue.getValue(), seriesSenderConfig.getRepeatCount()));
            }
        }

        if (pattern != null) {
            final PatternParser patternParser = new PatternParser(null, PatternLayout.KEY,
                    LogEventPatternConverter.class);
            formatters = patternParser.parse(pattern);
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public EventWrapper<LogEvent> createWrapper(LogEvent event, int lines) {
        String message;
        Object om = event.getMessage();
        if (formatters == null) {
            message = (om == null ? "" : om.toString());
        } else {
            final StringBuilder sb = new StringBuilder();
            for (PatternFormatter formatter : formatters) {
                formatter.format(event, sb);
            }
            message = sb.toString();
        }
        return new EventWrapper<LogEvent>(event, lines, message);
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

