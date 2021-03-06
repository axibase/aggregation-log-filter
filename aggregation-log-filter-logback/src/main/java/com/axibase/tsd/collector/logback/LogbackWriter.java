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
import com.axibase.tsd.collector.writer.BaseHttpAtsdWriter;
import com.axibase.tsd.collector.writer.LoggingWrapper;
import com.axibase.tsd.collector.writer.TcpAtsdWriter;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.*;

public class LogbackWriter<E extends ILoggingEvent>
        extends ContextAwareBase
        implements MessageWriter<E, String, Level> {
    private Map<String, String> tags = new LinkedHashMap<>();
    private String entity = AtsdUtil.resolveHostname();
    private final Map<LoggerAndLevel<Level>, CounterWithSum> loggerEventHistory = new HashMap<>();
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;
    private final Map<Level, CounterWithSum> totals = new HashMap<>();
    private MessageHelper messageHelper = new MessageHelper();
    private PatternLayout patternLayout;
    private String pattern;
    private String atsdUrl;
    private Set<String> mdcTags = new HashSet<>();
    private int messageLength = -1;

    @Override
    public synchronized void writeStatMessages(WritableByteChannel writer,
                                               Map<String, EventCounter<Level>> diff,
                                               long deltaTime) throws IOException {
        if (deltaTime < 1) {
            throw new IllegalArgumentException("Illegal delta tie value: " + deltaTime);
        }
        int repeatCount = seriesSenderConfig.getRepeatCount();

        // decrement all previous zero repeat counters
        for (Counter counter : loggerEventHistory.values()) {
            counter.decrementZeroRepeats();
        }

        // increment using new events
        for (Map.Entry<String, EventCounter<Level>> loggerAndCounter : diff.entrySet()) {
            EventCounter<Level> extCounter = loggerAndCounter.getValue();
            for (Map.Entry<Level, Long> levelAndCnt : extCounter.values()) {
                LoggerAndLevel<Level> key = new LoggerAndLevel<Level>(levelAndCnt.getKey(), loggerAndCounter.getKey());
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
        for (Map.Entry<LoggerAndLevel<Level>, CounterWithSum> entry : loggerEventHistory.entrySet()) {
            CounterWithSum counter = entry.getValue();
            if (counter.getZeroRepeats() < 0) {
                // iterator.remove(); //#2132 counter should not reset log_event_counter to 0 after repeatCount
            } else {
                LoggerAndLevel<Level> key = entry.getKey();
                Level level = key.getLevel();
                long value = counter.getValue();
                counter.clean();
                try {
                    if (seriesSenderConfig.isSendLoggerCounter()) {
                        String levelString = level.toString();
                        messageHelper.writeCounter(writer, key, levelString, counter.getSum());
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
        for (Map.Entry<Level, CounterWithSum> entry : totals.entrySet()) {
            Level level = entry.getKey();
            CounterWithSum counterWithSum = entry.getValue();
            try {
                String levelString = level.toString();
                counterWithSum.clean();
                // write total sum
                messageHelper.writeTotalCounter(writer, time, counterWithSum, levelString);
            } catch (Exception e) {
                AtsdUtil.logError("Could not write log_event_total_counter series " + atsdUrl + " - " + e.getMessage());
            } finally {
//                entry.getValue().decrementZeroRepeats();
            }
        }
    }

    @Override
    public void writeSingles(WritableByteChannel writer, CountedQueue<EventWrapper<E>> singles) throws IOException {
        EventWrapper<E> wrapper;
        while ((wrapper = singles.poll()) != null) {
            writeSingle(writer, wrapper);
        }
    }

    private void writeSingle(WritableByteChannel writer, EventWrapper<E> wrapper) {
        try {
            E event = wrapper.getEvent();
            StringBuilder sb = new StringBuilder();
            String message = wrapper.getMessage();
            if (messageLength >= 0)
                message = message.substring(0, Math.min(message.length(), messageLength));
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
            writeMessage(writer, event, sb, message, wrapper.getContext());
        } catch (IOException e) {
            AtsdUtil.logError("Could not write message " + atsdUrl);
        }
    }

    @Override
    public boolean sendErrorInstance(WritableByteChannel writableByteChannel, E event) {
        if (LogbackEventTrigger.isErrorInstance(event)) {
            writeSingle(writableByteChannel, createWrapper(event, Integer.MAX_VALUE));
            return true;
        }
        return false;
    }

    private void writeMessage(WritableByteChannel writer,
                              E event,
                              StringBuilder sb,
                              String message, Map context) throws IOException {
        final String levelValue = event.getLevel().toString();
        final String loggerName = event.getLoggerName();
        Map<String, String> locationMap = new HashMap<>();
        locationMap.put("thread", event.getThreadName());
        if (event.hasCallerData() && event.getCallerData().length > 0) {
            StackTraceElement stackTraceElement = event.getCallerData()[0];
            locationMap.put("line", String.valueOf(stackTraceElement.getLineNumber()));
            locationMap.put("method", stackTraceElement.getMethodName());
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
                      int level,
                      int intervalSeconds,
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
        stringSettings.put("level", Level.toLevel(level).toString());
        stringSettings.put("intervalSeconds", intervalSeconds + "");
        stringSettings.put("framework", "logback");
        messageHelper.init(writer, stringSettings);

        if (pattern != null) {
            patternLayout = new PatternLayout();
            patternLayout.setContext(context);
            patternLayout.setPattern(pattern);
            patternLayout.start();
        }
        int[] levels = {Level.TRACE_INT, Level.DEBUG_INT, Level.INFO_INT, Level.WARN_INT, Level.ERROR_INT};
        for (int l : levels) {
            if (l < level) {
                continue;
            }
            CounterWithSum total = new CounterWithSum(0, seriesSenderConfig.getRepeatCount());
            totals.put(Level.toLevel(l), total);
        }
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
                    AtsdUtil.logInfo("Aggregation log filter: connected to ATSD");
                else if (writerToCheck instanceof BaseHttpAtsdWriter) {
                    AtsdUtil.logInfo("Aggregation log filter: connected with status code " + ((BaseHttpAtsdWriter) writerToCheck).getStatusCode());
                }
            } catch (IOException e) {
                AtsdUtil.logError("Aggregation log filter: failed to connect to ATSD. " + e);
                AtsdUtil.logError("Writer failed to send initial total counter value for " + Level.toLevel(level));
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
    public EventWrapper<E> createWrapper(E event, int lines) {
        String message;
        if (patternLayout == null) {
            message = event.getFormattedMessage();
        } else {
            message = patternLayout.doLayout(event);
        }
        Map context = MDC.getCopyOfContextMap();
        return new EventWrapper<E>(event, lines, message, context);
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

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }
}