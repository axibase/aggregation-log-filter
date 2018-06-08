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
import com.axibase.tsd.collector.InternalLogger;
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
import org.apache.logging.log4j.status.StatusLogger;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Level level = Level.TRACE;
    private String url;
    // series sender
    private Integer intervalSeconds;
    private Boolean sendLoggerCounter;
    private String debug;
    private String pattern;
    private String messageLength;
    // tags
    private final List<Tag> tags = new ArrayList<>();
    private final List<String> mdcTags = new ArrayList<String>();


    public WritableByteChannel getWriterClass() {
        return writer;
    }

    static {
        AtsdUtil.setInternalLogger(new InternalLogger() {
            @Override
            public void error(String message, Throwable t) {
                StatusLogger.getLogger().error(message, t);
            }

            @Override
            public void error(String message) {
                StatusLogger.getLogger().error(message);
            }

            @Override
            public void warn(String message) {
                StatusLogger.getLogger().warn(message);
            }

            @Override
            public void info(String message) {
                StatusLogger.getLogger().debug(message);
            }

            @Override
            public void info(String message, Throwable t) {
                StatusLogger.getLogger().debug(message, t);
            }
        });
    }

    public Log4j2Collector() {
    }

    public void init() {
        initWriter();
        initSeriesSenderConfig();

        messageBuilder = new Log4j2MessageWriter();
        messageBuilder.setAtsdUrl(url);
        if (entity != null) {
            messageBuilder.setEntity(entity);
        }
        if (seriesSenderConfig != null) {
            messageBuilder.setSeriesSenderConfig(seriesSenderConfig);
        }
        if (pattern == null) {
            pattern = DEFAULT_PATTERN;
        }
        messageBuilder.setPattern(pattern);
        if (debug == null) {
            debug = "false";
        }
        for (Tag tag : tags) {
            messageBuilder.addTag(tag);
        }
        for (String mdcTag : mdcTags) {
            messageBuilder.addMdcTag(mdcTag);
        }
        if (messageLength == null) {
            messageLength = DEFAULT_MESSAGE_LENGTH;
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
        aggregator.start();
        Map<String, String> stringSettings = new HashMap<>();
        stringSettings.put("debug", debug);
        stringSettings.put("pattern", pattern);
        stringSettings.put("scheme", StringUtils.substringBefore(url,":"));
        messageBuilder.start(writer, level.intLevel(), (int) (seriesSenderConfig.getIntervalMs() / 1000), stringSettings);

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
            @PluginAttribute("sendLoggerCounter") final Boolean sendLoggerCounter,
            @PluginAttribute("pattern") final String pattern,
            @PluginAttribute("messageLength") final String messageLength,
            @PluginAttribute("debug") final String debug) {
        final Level minLevel = level == null ? Level.TRACE : level;
        final Log4j2Collector collector = new Log4j2Collector();
        collector.setEntity(entity);
        collector.setTags(tags);
        collector.setMdcTags(mdcTags);
        collector.setMessages(messages);
        collector.setLevel(minLevel);
        collector.setUrl(url);
        collector.setMessageLength(messageLength);
        if (intervalSeconds <= 0) {
            collector.setIntervalSeconds(collector.DEFAULT_INTERVAL);
        } else {
            collector.setIntervalSeconds(intervalSeconds);
        }
        if (sendLoggerCounter == null) {
            collector.setSendLoggerCounter(false);
        } else {
            collector.setSendLoggerCounter(sendLoggerCounter);
        }
        collector.setPattern(pattern);
        collector.setDebug(debug);
        try {
            collector.init();
        } catch (Exception e) {
            AtsdUtil.logError("Could not initialize collector. " + e.getMessage());
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

    private void initWriter() {
        try {
            writer = AtsdWriterFactory.getWriter(url);
        } catch (Exception e) {
            AtsdUtil.logError("Could not get writer class, " + e);
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
            if (event.getLevel().intLevel() <= this.level.intLevel()) {
                aggregator.register(event);
            }
        } catch (IOException e) {
            StatusLogger.getLogger().error("Could not write message. " + e.getMessage());
        }
        return Result.NEUTRAL;
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
                '}';
    }
}
