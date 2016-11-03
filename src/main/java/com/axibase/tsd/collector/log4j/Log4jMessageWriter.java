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
import com.axibase.tsd.collector.writer.BaseHttpAtsdWriter;
import com.axibase.tsd.collector.writer.LoggingWrapper;
import com.axibase.tsd.collector.writer.TcpAtsdWriter;
import org.apache.log4j.Level;
import org.apache.log4j.MDC;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.*;

public class Log4jMessageWriter implements MessageWriter<LoggingEvent, String, String> {
    private Map<String, String> tags = new LinkedHashMap<String, String>();
    private String entity = AtsdUtil.resolveHostname();
    private final Map<Key<String>, CounterWithSum> story = new HashMap<Key<String>, CounterWithSum>();
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;
    private final Map<String, CounterWithSum> totals = new HashMap<String, CounterWithSum>();
    private MessageHelper messageHelper = new MessageHelper();
    private String pattern;
    private String atsdUrl;
    private PatternLayout patternLayout;
    private Set<String> mdcTags = new HashSet<>();

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
                    if (seriesSenderConfig.isSendLoggerCounter())
                        messageHelper.writeCounter(writer, time, key, level, counter.getSum());
                } catch (Throwable e) {
                    AtsdUtil.logInfo("Could not write series " + atsdUrl + ". " + e.getMessage());
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
                counterWithSum.clean();
                // write total count
                messageHelper.writeTotalCounter(writer, time, counterWithSum, level);
            } catch (Throwable e) {
                AtsdUtil.logInfo("Could not write series " + atsdUrl + ". " + e.getMessage());
            } finally {
//                entry.getValue().decrementZeroRepeats();
            }
        }
    }

    @Override
    public void writeSingles(WritableByteChannel writer, CountedQueue<EventWrapper<LoggingEvent>> singles) throws IOException {
        EventWrapper<LoggingEvent> wrapper;
        while ((wrapper = singles.poll()) != null) {
            writeSingle(writer, wrapper);
        }
        singles.clearCount();
    }

    private void writeSingle(WritableByteChannel writer, EventWrapper<LoggingEvent> wrapper) {
        try {
            LoggingEvent event = wrapper.getEvent();
            StringBuilder sb = new StringBuilder();
            String message = wrapper.getMessage();
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
            writeMessage(writer, event, sb, message, wrapper.getContext());
        } catch (Exception e) {
            AtsdUtil.logInfo("Could not write message " + atsdUrl + " " + e.getMessage());
        }
    }

    public boolean sendErrorInstance(WritableByteChannel writer, LoggingEvent loggingEvent) {
        Log4jEventTrigger log4jEventTrigger = new Log4jEventTrigger(Level.ERROR);
        if (log4jEventTrigger.isErrorInstance(loggingEvent)) {
            writeSingle(writer, createWrapper(loggingEvent, Integer.MAX_VALUE));
            return true;
        }
        return false;
    }

    private void writeMessage(WritableByteChannel writer,
                              LoggingEvent event,
                              StringBuilder sb,
                              String message, Map context) throws IOException {
        final String levelValue = event.getLevel().toString();
        final String loggerName = event.getLoggerName();
        LocationInfo locationInformation = event.getLocationInformation();
        Map<String, String> locationMap = new HashMap<>();
        locationMap.put("thread", event.getThreadName());
        if (!locationInformation.getLineNumber().equals(LocationInfo.NA)) {
            locationMap.put("line", locationInformation.getLineNumber());
            locationMap.put("method", locationInformation.getMethodName());
        }
        if (context != null && mdcTags.size() > 0) {
            Set keySet = context.keySet();
            for (String mdcTag : mdcTags) {
                if (keySet.contains(mdcTag))
                    locationMap.put(mdcTag, (String) context.get(mdcTag));
            }
        }
        messageHelper.writeMessage(writer, sb, message, levelValue, loggerName, locationMap);
    }

    @Override
    public void start(WritableByteChannel writer, int level, int intervalSeconds, Map<String, String> stringSettings) {
        messageHelper.setSeriesSenderConfig(seriesSenderConfig);
        messageHelper.setEntity(AtsdUtil.sanitizeEntity(entity));
        messageHelper.setTags(tags);
        if (!tags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String key : tags.keySet()) {
                sb.append(key).append("=").append(tags.get(key)).append(" ");
            }
            stringSettings.put("tags", sb.toString().trim());
        }
        stringSettings.put("level", Level.toLevel(level).toString());
        stringSettings.put("intervalSeconds", intervalSeconds + "");
        stringSettings.put("framework", "log4j");
        messageHelper.init(writer, stringSettings);

        if (pattern != null) {
            patternLayout = new PatternLayout(pattern);
            patternLayout.activateOptions();
        }
        int[] levels = new int[]{
                Level.TRACE_INT, Level.DEBUG_INT,
                Level.FATAL_INT, Level.INFO_INT,
                Level.WARN_INT, Level.ERROR_INT};
        for (int l : levels) {
            if (l < level) {
                continue;
            }
            CounterWithSum total = new CounterWithSum(0, seriesSenderConfig.getRepeatCount());
            totals.put(Level.toLevel(l).toString(), total);
        }
        AtsdUtil.logInfo("Aggregation log filter: connecting to ATSD on " + atsdUrl);
        if (writer != null) {
            try {
                for (int l : levels) {
                    if (l < level) {
                        continue;
                    }
                    messageHelper.writeTotalCounter(writer, System.currentTimeMillis(), new CounterWithSum(0, 0),
                            Level.toLevel(l).toString());
                }
                WritableByteChannel writerToCheck = writer;
                if (writerToCheck instanceof LoggingWrapper) {
                    writerToCheck = ((LoggingWrapper) writerToCheck).getWrapped();
                }
                if (writerToCheck instanceof TcpAtsdWriter)
                    AtsdUtil.logInfo("Aggregation log filter: connected to ATSD.");
                else if (writerToCheck instanceof BaseHttpAtsdWriter) {
                    AtsdUtil.logInfo("Aggregation log filter: connected with status code " + ((BaseHttpAtsdWriter) writerToCheck).getStatusCode());
                }
            } catch (IOException e) {
                AtsdUtil.logInfo("Aggregation log filter: failed to connect to ATSD.");
                AtsdUtil.logInfo("Writer failed to send initial total counter value for " + Level.toLevel(level));
            }
        }
    }

    @Override
    public void checkPropertiesSent(WritableByteChannel writer) {
        messageHelper.checkSentStatus(writer);
    }

    @Override
    public void stop() {
    }

    @Override
    public EventWrapper<LoggingEvent> createWrapper(LoggingEvent event, int lines) {
        String message;
        Object om = event.getMessage();
        if (patternLayout == null) {
            message = (om == null ? "" : om.toString());
        } else {
            message = patternLayout.format(event);
        }
        Hashtable mdcContext = MDC.getContext();
        HashMap context = null;
        if (mdcContext != null) {
            context = new HashMap<String, String>();
            for (Object o : mdcContext.keySet()) {
                context.put(o.toString(), mdcContext.get(o.toString()));
            }
        }
        return new EventWrapper<LoggingEvent>(event, lines, message, context);
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

    public void setAtsdUrl(String atsdUrl) {
        this.atsdUrl = atsdUrl;
    }

    public void addMdcTag(String mdcTag) {
        mdcTags.add(mdcTag);
    }
}

