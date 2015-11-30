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

import com.axibase.tsd.collector.*;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Nikolay Malevanny.
 */
public class Log4jMessageWriter implements MessageWriter<LoggingEvent, String, String> {
    private Map<String, String> tags = new LinkedHashMap<String, String>();
    private String entity = AtsdUtil.resolveHostname();
    private final Map<Key<String>, CounterWithSum> story = new HashMap<Key<String>, CounterWithSum>();
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;
    private final Map<String, CounterWithSum> totals = new HashMap<String, CounterWithSum>();
    private MessageHelper messageHelper = new MessageHelper();

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
                iterator.remove();
            } else {
                Key<String> key = entry.getKey();
                String level = key.getLevel();
                long value = counter.getValue();
                counter.clean();
                try {
                    messageHelper.writeCounter(writer, time, key, level, counter.getSum());
                } catch (Throwable e) {
                    AtsdUtil.logError("Could not write series", e);
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
                AtsdUtil.logError("Could not write series", e);
            } finally {
//                entry.getValue().decrementZeroRepeats();
            }
        }
    }

    @Override
    public void writeSingles(WritableByteChannel writer, CountedQueue<EventWrapper<LoggingEvent>> singles) throws IOException {
        EventWrapper<LoggingEvent> wrapper;
        while ((wrapper = singles.poll()) != null) {
            try {
                LoggingEvent event = wrapper.getEvent();
                StringBuilder sb = new StringBuilder();
                final Object om = event.getMessage();
                String message = (om == null?"":om.toString());
                int lines = wrapper.getLines();
                if (lines > 0 && event.getThrowableInformation() != null && event.getThrowableInformation().getThrowable() != null) {
                    StringBuilder msb = new StringBuilder(message);
                    final ThrowableInformation throwableInfo = event.getThrowableInformation();
                    int s = 0;
                    final Throwable throwableProxy = throwableInfo.getThrowable();
                    msb.append("\n").append(throwableProxy.getClass().getName())
                            .append(": ").append(throwableProxy.getMessage());
                    String[] traceElementProxyArray = throwableInfo.getThrowableStrRep();
                    if (traceElementProxyArray != null) {
                        for (int i = 0; i < traceElementProxyArray.length && s < lines; i++, s++) {
                            msb.append("\n\t").append(traceElementProxyArray[i]);
                        }
                    }
                    message = msb.toString();
                }
                writeMessage(writer, event, sb, message);
            } catch (Exception e) {
                AtsdUtil.logError("Could not write message", e);
            }
        }
        singles.clearCount();
    }

    private void writeMessage(WritableByteChannel writer,
                              LoggingEvent event,
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
}

