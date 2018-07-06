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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.spi.FilterReply;
import com.axibase.tsd.collector.Aggregator;
import com.axibase.tsd.collector.AtsdUtil;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import com.axibase.tsd.collector.writer.*;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Collector<E extends ILoggingEvent> extends Filter<E> implements ContextAware {
    private static Collector instance;
    private LogbackWriter<E> logbackWriter;
    private Aggregator<E, String, Level> aggregator;
    private SeriesSenderConfig seriesSenderConfig;
    private final String MESSAGE_WITHOUT_STACKTRACE = "%nopex";
    private final List<LogbackEventTrigger<E>> triggers = new ArrayList<LogbackEventTrigger<E>>();
    private WritableByteChannel writer;
    /**
     * Settings from logback file.
     */
    // common
    private String entity;
    private Level level = Level.TRACE;
    private String url;
    private String ignoreSslErrors = "true";
    // series sender
    private String debug = "false";
    private String pattern;
    private Integer intervalSeconds;
    private Boolean sendLoggerCounter;
    private int messageLength = -1;
    // tags
    private final List<Tag> tags = new ArrayList<>();
    private final List<String> mdcTags = new ArrayList<String>();

    @Override
    public FilterReply decide(E event) {
        try {
            if (event.getLevel().isGreaterOrEqual(level)) {
                if (aggregator != null) {
                    aggregator.register(event);
                }
            }
        } catch (IOException e) {
            addError("Could not register event. " + e);
        }
        return FilterReply.NEUTRAL;
    }

    @Override
    public void start() {
        super.start();
        context.register(this);
        initSeriesSenderConfig();
        try {
            writer = AtsdWriterFactory.getWriter(url, ignoreSslErrors);
            writer = LoggingWrapper.tryWrap(debug, writer);
            logbackWriter = new LogbackWriter<>();
            if (entity != null) {
                logbackWriter.setEntity(entity);
            }
            logbackWriter.setSeriesSenderConfig(seriesSenderConfig);
            if (pattern == null) {
                pattern = "%m";
            }
            logbackWriter.setPattern(pattern.concat(MESSAGE_WITHOUT_STACKTRACE));
            logbackWriter.setContext(getContext());
            for (Tag tag : tags) {
                logbackWriter.addTag(tag);
            }
            for (String mdcTag : mdcTags) {
                logbackWriter.addMdcTag(mdcTag);
            }
            if (messageLength >= 0) {
                logbackWriter.setMessageLength(messageLength);
            }
            logbackWriter.setAtsdUrl(url);

            aggregator = new Aggregator<>(logbackWriter, new LogbackEventProcessor<E>());
            aggregator.setWriter(writer);
            aggregator.setSeriesSenderConfig(seriesSenderConfig);
            aggregator.addSendMessageTrigger(new LogbackEventTrigger<E>(Level.ERROR));
            aggregator.addSendMessageTrigger(new LogbackEventTrigger<E>(Level.WARN));
            aggregator.addSendMessageTrigger(new LogbackEventTrigger<E>(Level.INFO));
            for (LogbackEventTrigger<E> trigger : triggers) {
                aggregator.addSendMessageTrigger(trigger);
            }
            Map<String, String> stringSettings = new HashMap<>();
            stringSettings.put("debug", debug);
            stringSettings.put("ignoreSslErrors", ignoreSslErrors);
            stringSettings.put("pattern", pattern);
            stringSettings.put("scheme", StringUtils.substringBefore(url, ":"));
            logbackWriter.start(writer, level.levelInt, (int) (seriesSenderConfig.getIntervalMs() / 1000), stringSettings);
            aggregator.start();
        } catch (Exception e) {
            AtsdUtil.logError("Cannot start " + getClass().getSimpleName() + " - " + e + ".");
        }
    }

    private void initSeriesSenderConfig() {
        seriesSenderConfig = new SeriesSenderConfig();
        if (intervalSeconds != null) {
            seriesSenderConfig.setIntervalSeconds(intervalSeconds);
        }
        if (sendLoggerCounter != null) {
            seriesSenderConfig.setSendLoggerCounter(sendLoggerCounter);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (writer != null) {
            aggregator.stop();
            logbackWriter.stop();
        }
    }

    public void setTag(Tag tag) {
        tags.add(tag);
    }

    public void setMdcTag(String tag) {
        mdcTags.add(tag);
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setUrl(String atsdUrl) {
        url = atsdUrl;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void setSendMessage(LogbackEventTrigger<E> messageTrigger) {
        triggers.add(messageTrigger);
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public void setSendLoggerCounter(boolean sendLoggerCounter) {
        this.sendLoggerCounter = sendLoggerCounter;
    }

    public void setDebug(String debug) {
        this.debug = debug;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }

    public void setIgnoreSslErrors(String ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
    }
}
