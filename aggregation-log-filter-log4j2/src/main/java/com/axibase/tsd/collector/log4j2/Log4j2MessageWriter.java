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
import com.axibase.tsd.collector.writer.BaseHttpAtsdWriter;
import com.axibase.tsd.collector.writer.LoggingWrapper;
import com.axibase.tsd.collector.writer.TcpAtsdWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
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

public class Log4j2MessageWriter implements MessageWriter<LogEvent, String, String> {
    private Map<String, String> tags = new LinkedHashMap<String, String>();
    private String entity = AtsdUtil.resolveHostname();
    private final Map<LoggerAndLevel<String>, CounterWithSum> loggerEventHistory = new HashMap<LoggerAndLevel<String>, CounterWithSum>();
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;
    private final Map<String, CounterWithSum> totals = new HashMap<String, CounterWithSum>();
    private MessageHelper messageHelper = new MessageHelper();
    private String pattern;
    private String atsdUrl;
    private PatternFormatter[] formatters;
    private Set<String> mdcTags = new HashSet<>();
    private int messageLength = -1;

    @Override
    public synchronized void writeStatMessages(WritableByteChannel writer,
                                               Map<String, EventCounter<String>> diff,
                                               long deltaTime) throws IOException {
        if (deltaTime < 1) {
            throw new IllegalArgumentException("Illegal delta time value: " + deltaTime);
        }
        int repeatCount = seriesSenderConfig.getRepeatCount();

        // decrement all previous zero repeat counters
        for (Counter counter : loggerEventHistory.values()) {
            counter.decrementZeroRepeats();
        }

        // increment using new events
        for (Map.Entry<String, EventCounter<String>> loggerAndCounter : diff.entrySet()) {
            EventCounter<String> extCounter = loggerAndCounter.getValue();
            for (Map.Entry<String, Long> levelAndCnt : extCounter.values()) {
                LoggerAndLevel<String> key = new LoggerAndLevel<String>(levelAndCnt.getKey(), loggerAndCounter.getKey());
                long v = levelAndCnt.getValue();
                CounterWithSum counter = loggerEventHistory.get(key);
                if (counter == null) {
                    loggerEventHistory.put(key, new CounterWithSum(v, repeatCount));
                } else {
                    counter.add(v);
                    counter.setZeroRepeats(repeatCount);
                }
            }
        }

        long time = System.currentTimeMillis();

        // compose & clean
        for (Map.Entry<LoggerAndLevel<String>, CounterWithSum> entry : loggerEventHistory.entrySet()) {
            CounterWithSum counter = entry.getValue();
            if (counter.getZeroRepeats() < 0) {
                // iterator.remove(); //#2132 counter should not reset log_event_counter to 0 after repeatCount
            } else {
                LoggerAndLevel<String> key = entry.getKey();
                String level = key.getLevel();
                long value = counter.getValue();
                counter.clean();
                try {
                    if (seriesSenderConfig.isSendLoggerCounter()) {
                        messageHelper.writeCounter(writer, key, level, counter.getSum());
                    }
                } catch (Exception e) {
                    AtsdUtil.logError("Could not write log_event_counter series " + atsdUrl + " - " + e.getMessage());
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
            } catch (Exception e) {
                AtsdUtil.logError("Could not write log_event_total_counter series " + atsdUrl + " - " + e.getMessage());
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
            writeSingle(writer, wrapper);
        }
    }

    private void writeSingle(WritableByteChannel writer, EventWrapper<LogEvent> wrapper) {
        try {
            LogEvent event = wrapper.getEvent();
            StringBuilder sb = new StringBuilder();
            String message = wrapper.getMessage();
            if (messageLength >= 0)
                message = message.substring(0, Math.min(message.length(), messageLength));
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
            writeMessage(writer, event, sb, message, wrapper.getContext());
        } catch (Exception e) {
            AtsdUtil.logError("Could not write message " + atsdUrl);
        }
    }

    @Override
    public boolean sendErrorInstance(WritableByteChannel writableByteChannel, LogEvent logEvent) {
        if (Log4j2EventTrigger.isErrorInstance(logEvent)) {
            writeSingle(writableByteChannel, createWrapper(logEvent, Integer.MAX_VALUE));
            return true;
        }
        return false;
    }

    private void writeMessage(WritableByteChannel writer,
                              LogEvent event,
                              StringBuilder sb,
                              String message, Map context) throws IOException {
        final String levelValue = event.getLevel().toString();
        final String loggerName = event.getLoggerName();
        StackTraceElement source = event.getSource();
        Map<String, String> locationMap = new HashMap<>();
        locationMap.put("thread", event.getThreadName());
        if (source != null) {
            locationMap.put("line", String.valueOf(source.getLineNumber()));
            locationMap.put("method", source.getMethodName());
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
    public void start(WritableByteChannel writer,
                      int level, int intervalSeconds,
                      Map<String, String> stringSettings) {
        messageHelper.setSeriesSenderConfig(seriesSenderConfig);
        messageHelper.setEntity(AtsdUtil.sanitizeEntity(entity));
        messageHelper.setTags(tags);

        if (!tags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }
            stringSettings.put("tags", sb.toString().trim());
        }

        Level curLevel = Level.TRACE;
        Level[] levels = {Level.FATAL, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE};

        for (Level l : levels) {
            if (l.intLevel() == level) {
                curLevel = l;
                break;
            }
        }

        stringSettings.put("level", curLevel.toString());
        stringSettings.put("intervalSeconds", intervalSeconds + "");
        stringSettings.put("framework", "log4j2");
        messageHelper.init(writer, stringSettings);

        if (pattern != null) {
            final PatternParser patternParser = new PatternParser(null, PatternLayout.KEY,
                    LogEventPatternConverter.class);
            formatters = patternParser.parse(pattern).toArray(new PatternFormatter[0]);
        }
        for (Level l : levels) {
            if (l.intLevel() > level)
                continue;
            CounterWithSum total = new CounterWithSum(0, seriesSenderConfig.getRepeatCount());
            totals.put(l.toString(), total);
        }
        if (writer != null) {
            try {
                for (Level l : levels) {
                    if (l.intLevel() > level)
                        continue;
                    messageHelper.writeTotalCounter(writer, System.currentTimeMillis(), new CounterWithSum(0, 0), l.toString());
                }
                WritableByteChannel writerToCheck = writer;
                if (writerToCheck instanceof LoggingWrapper) {
                    writerToCheck = ((LoggingWrapper) writerToCheck).getWrapped();
                }
                if (writerToCheck instanceof TcpAtsdWriter)
                    AtsdUtil.logInfo("Aggregation log filter: connected to ATSD");
                else if (writerToCheck instanceof BaseHttpAtsdWriter) {
                    AtsdUtil.logInfo("Aggregation log filter: connected with status code " + ((BaseHttpAtsdWriter) writerToCheck).getStatusCode());
                }
            } catch (IOException e) {
                AtsdUtil.logError("Aggregation log filter: failed to connect to ATSD. " + e);
                AtsdUtil.logError("Writer failed to send initial total counter value for " + curLevel);
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
    public EventWrapper<LogEvent> createWrapper(LogEvent event, int lines) {
        event = event.toImmutable();
        final String message;
        if (formatters == null) {
            message = Objects.toString(event.getMessage(), "");
        } else {
            final StringBuilder sb = new StringBuilder();
            for (PatternFormatter formatter : formatters) {
                formatter.format(event, sb);
            }
            message = sb.toString();
        }
        return new EventWrapper<>(event, lines, message, ThreadContext.getContext());
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

    public void setMessageLength(String messageLength) {
        this.messageLength = Integer.valueOf(messageLength);
    }
}

