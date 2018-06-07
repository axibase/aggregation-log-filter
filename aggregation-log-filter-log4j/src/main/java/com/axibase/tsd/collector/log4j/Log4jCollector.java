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

import com.axibase.tsd.collector.Aggregator;
import com.axibase.tsd.collector.AtsdUtil;
import com.axibase.tsd.collector.InternalLogger;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import com.axibase.tsd.collector.writer.*;
import org.apache.log4j.Level;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Log4jCollector extends Filter {
    private Aggregator<LoggingEvent, String, String> aggregator;
    private final List<Log4jEventTrigger> triggers = new ArrayList<Log4jEventTrigger>();
    private Log4jMessageWriter log4jMessageWriter;
    private WritableByteChannel writer;
    private SeriesSenderConfig seriesSenderConfig;
    /**
     * Settings from log4j file.
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
    private int messageLength = -1;
    // tags
    private final List<Tag> tags = new ArrayList<Tag>();
    private final List<String> mdcTags = new ArrayList<String>();

    public WritableByteChannel getWriterClass() {
        return writer;
    }

    static {
        AtsdUtil.setInternalLogger(new InternalLogger() {
            @Override
            public void error(String message, Throwable t) {
                LogLog.error(message, t);
            }

            @Override
            public void error(String message) {
                LogLog.error(message);
            }

            @Override
            public void warn(String message) {
                LogLog.warn(message);
            }

            @Override
            public void info(String message) {
                LogLog.debug(message);
            }

            @Override
            public void info(String message, Throwable t) {
                LogLog.debug(message, t);
            }
        });
    }

    @Override
    public void activateOptions() {
        super.activateOptions();
        initWriter();
        initSeriesSenderConfig();

        log4jMessageWriter = new Log4jMessageWriter();
        log4jMessageWriter.setAtsdUrl(url);
        if (entity != null) {
            log4jMessageWriter.setEntity(entity);
        }
        if (seriesSenderConfig != null) {
            log4jMessageWriter.setSeriesSenderConfig(seriesSenderConfig);
        }
        if (pattern == null) {
            pattern = "%m";
        }
        log4jMessageWriter.setPattern(pattern);
        if (debug == null) {
            debug = "false";
        }
        for (Tag tag : tags) {
            log4jMessageWriter.addTag(tag);
        }
        for (String mdcTag : mdcTags) {
            log4jMessageWriter.addMdcTag(mdcTag);
        }
        if (messageLength >= 0) {
            log4jMessageWriter.setMessageLength(messageLength);
        }

        aggregator = new Aggregator<>(log4jMessageWriter, new Log4jEventProcessor());
        writer = LoggingWrapper.tryWrap(debug, writer);
        aggregator.setWriter(writer);
        if (seriesSenderConfig != null) {
            aggregator.setSeriesSenderConfig(seriesSenderConfig);
        }

        aggregator.addSendMessageTrigger(new Log4jEventTrigger(Level.ERROR));
        aggregator.addSendMessageTrigger(new Log4jEventTrigger(Level.WARN));
        aggregator.addSendMessageTrigger(new Log4jEventTrigger(Level.INFO));
        for (Log4jEventTrigger trigger : triggers) {
            aggregator.addSendMessageTrigger(trigger);
        }
        aggregator.start();
        Map<String, String> stringSettings = new HashMap<>();
        stringSettings.put("debug", debug);
        stringSettings.put("pattern", pattern);
        stringSettings.put("url", url);
        log4jMessageWriter.start(writer, level.toInt(), (int) (seriesSenderConfig.getIntervalMs() / 1000), stringSettings);
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
        } catch (IllegalStateException | URISyntaxException e) {
            AtsdUtil.logError("Could not get writer class, " + e.getMessage());
        }
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
                final Log4jEventTrigger trigger = new Log4jEventTrigger();
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

    public void setUrl(String atsdUrl) {
        url = atsdUrl;
    }

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }

    @Override
    public int decide(LoggingEvent event) {
        try {
            if (event.getLevel().isGreaterOrEqual(level)) {
                aggregator.register(event);
            }
        } catch (IOException e) {
            LogLog.error("Could not write message. " + e.getMessage());
        }
        return NEUTRAL;
    }
}
