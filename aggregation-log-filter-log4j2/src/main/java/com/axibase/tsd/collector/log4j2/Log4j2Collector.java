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

import com.axibase.tsd.collector.Aggregator;
import com.axibase.tsd.collector.AtsdUtil;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import com.axibase.tsd.collector.writer.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.axibase.tsd.collector.config.SeriesSenderConfig.DEFAULT_SEND_LOGGER_COUNTER;

@Plugin(name = "Collector", category = "Core", elementType = "filter", printObject = true)
public class Log4j2Collector extends AbstractFilter {
    private Aggregator<LogEvent, String, String> aggregator;
    private final List<Log4j2EventTrigger> triggers = new ArrayList<>();
    private Log4j2MessageWriter messageBuilder;
    private SeriesSenderConfig seriesSenderConfig;
    private WritableByteChannel writer;
    private static final int DEFAULT_INTERVAL = 60;
    private static final String DEFAULT_PATTERN = "%m";
    private static final String DEFAULT_MESSAGE_LENGTH = "-1";
    /**
     * Settings from log4j2 file.
     */
    // common
    private String entity;
    private Level level;
    private String url;
    private String ignoreSslErrors;
    // series sender
    private Integer intervalSeconds;
    private Boolean sendLoggerCounter;
    private String debug;
    private String pattern;
    private String messageLength;
    // tags
    private final List<Tag> tags = new ArrayList<>();
    private final List<String> mdcTags = new ArrayList<>();

    public WritableByteChannel getWriterClass() {
        return writer;
    }

    public void init() throws Exception {
        initSeriesSenderConfig();
        writer = AtsdWriterFactory.getWriter(url, ignoreSslErrors);
        messageBuilder = new Log4j2MessageWriter();
        messageBuilder.setAtsdUrl(url);
        if (entity != null) {
            messageBuilder.setEntity(entity);
        }
        if (seriesSenderConfig != null) {
            messageBuilder.setSeriesSenderConfig(seriesSenderConfig);
        }
        messageBuilder.setPattern(pattern);

        for (Tag tag : tags) {
            messageBuilder.addTag(tag);
        }
        for (String mdcTag : mdcTags) {
            messageBuilder.addMdcTag(mdcTag);
        }
        messageBuilder.setMessageLength(messageLength);

        aggregator = new Aggregator<>(messageBuilder, new Log4j2EventProcessor());
        writer = LoggingWrapper.tryWrap(debug, writer);
        aggregator.setWriter(writer);
        if (seriesSenderConfig != null) {
            aggregator.setSeriesSenderConfig(seriesSenderConfig);
        }
        aggregator.addSendMessageTrigger(new Log4j2EventTrigger(Level.ERROR));
        aggregator.addSendMessageTrigger(new Log4j2EventTrigger(Level.WARN));
        aggregator.addSendMessageTrigger(new Log4j2EventTrigger(Level.INFO));
        for (Log4j2EventTrigger trigger : triggers) {
            aggregator.addSendMessageTrigger(trigger);
        }

        Map<String, String> stringSettings = new HashMap<>();
        stringSettings.put("debug", debug);
        stringSettings.put("pattern", pattern);
        stringSettings.put("scheme", StringUtils.substringBefore(url, ":"));
        stringSettings.put("ignoreSslErrors", ignoreSslErrors);
        messageBuilder.start(writer, level.intLevel(), (int) (seriesSenderConfig.getIntervalMs() / 1000), stringSettings);
        aggregator.start();
    }

    /**
     * Create a Log4j2Collector.
     *
     * @return The created Log4j2Collector.
     */
    @PluginFactory
    public static Log4j2Collector createFilter(
            @PluginAttribute("entity") final String entity,
            @PluginAttribute("tags") final String tags,
            @PluginAttribute("mdcTags") final String mdcTags,
            @PluginAttribute("messages") final String messages,
            @PluginAttribute("level") final Level level,
            @PluginAttribute("url") final String url,
            @PluginAttribute("intervalSeconds") final Integer intervalSeconds,
            @PluginAttribute(value = "sendLoggerCounter", defaultBoolean = DEFAULT_SEND_LOGGER_COUNTER) final boolean sendLoggerCounter,
            @PluginAttribute(value = "pattern", defaultString = DEFAULT_PATTERN) final String pattern,
            @PluginAttribute(value = "messageLength", defaultString = DEFAULT_MESSAGE_LENGTH) final String messageLength,
            @PluginAttribute(value = "debug", defaultString = "false") final String debug,
            @PluginAttribute(value = "ignoreSslErrors", defaultString = "true") final String ignoreSslErrors) {
        final Level minLevel = (level == null) ? Level.TRACE : level;
        final Log4j2Collector collector = new Log4j2Collector();
        collector.setEntity(entity);
        collector.setTags(tags);
        collector.setMdcTags(mdcTags);
        collector.setMessages(messages);
        collector.setLevel(minLevel);
        collector.setUrl(url);
        collector.setMessageLength(messageLength);
        collector.setDebug(debug);
        collector.setPattern(pattern);
        collector.setIgnoreSslErrors(ignoreSslErrors);
        if (intervalSeconds <= 0) {
            collector.setIntervalSeconds(DEFAULT_INTERVAL);
        } else {
            collector.setIntervalSeconds(intervalSeconds);
        }
        collector.setSendLoggerCounter(sendLoggerCounter);
        try {
            collector.init();
        } catch (Exception e) {
            AtsdUtil.logError("Cannot init Log4j2Collector" + " - " + e + ".");
        }
        return collector;
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

    public void setUrl(String atsdUrl) {
        url = atsdUrl;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setTags(String tags) {
        if (tags != null) {
            final String[] parts = tags.split(";");
            for (String tagValue : parts) {
                final String[] nameAndValue = tagValue.split("=", 2);
                if (nameAndValue.length == 2) {
                    this.tags.add(new Tag(nameAndValue[0], nameAndValue[1]));
                }
            }
        }
    }

    public void setMdcTags(String mdcTags) {
        if (mdcTags != null) {
            final String[] parts = mdcTags.split(";");
            for (String tag : parts) {
                this.mdcTags.add(tag);
            }
        }
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public void setSendLoggerCounter(boolean sendLoggerCounter) {
        this.sendLoggerCounter = sendLoggerCounter;
    }

    public void setMessages(String messages) {
        if (messages == null) {
            return;
        }
        final String[] parts = messages.split(";");
        for (String part : parts) {
            final String[] levelAndValues = part.split("=", 2);
            if (levelAndValues.length >= 1) {
                final Log4j2EventTrigger trigger = new Log4j2EventTrigger();
                trigger.setLevel(Level.toLevel(levelAndValues[0]));
                if (levelAndValues.length >= 2) {
                    final String[] vParts = levelAndValues[1].split(",");
                    if (vParts.length >= 1) {
                        trigger.setStackTraceLines(Integer.parseInt(vParts[0]));
                    }
                    if (vParts.length >= 2) {
                        trigger.setSendMultiplier(Double.parseDouble(vParts[1]));
                    }
                }
                trigger.init();
                triggers.add(trigger);
            }
        }
    }

    public void setDebug(String debug) {
        this.debug = debug;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setMessageLength(String messageLength) {
        this.messageLength = messageLength;
    }

    @Override
    public Result filter(LogEvent event) {
        try {
            if (event.getLevel().intLevel() <= level.intLevel()) {
                if (aggregator != null) {
                    aggregator.register(event);
                }
            }
        } catch (IOException e) {
            AtsdUtil.logError("Could not register event. " + e);
        }
        return Result.NEUTRAL;
    }

    public void setIgnoreSslErrors(String ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        if (aggregator != null) {
            aggregator.stop();
        }
        if (messageBuilder != null) {
            messageBuilder.stop();
        }
        return super.stop(timeout, timeUnit);
    }

    @Override
    public String toString() {
        return "Log4j2Collector{" +
                "aggregator=" + aggregator +
                ", triggers=" + triggers +
                ", messageBuilder=" + messageBuilder +
                ", tags=" + tags +
                ", entity='" + entity + '\'' +
                ", level=" + level +
                ", writer=" + writer +
                ", url='" + url + '\'' +
                ", seriesSenderConfig=" + seriesSenderConfig +
                ", intervalSeconds=" + intervalSeconds +
                ", sendLoggerCounter='" + sendLoggerCounter + '\'' +
                ", debug='" + debug + '\'' +
                ", pattern='" + pattern + '\'' +
                ", ignoreSslErrors='" + ignoreSslErrors + '\'' +
                '}';
    }
}
