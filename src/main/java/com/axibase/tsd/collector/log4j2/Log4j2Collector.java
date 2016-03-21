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
import com.axibase.tsd.collector.config.TotalCountInit;
import com.axibase.tsd.collector.writer.*;
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
import java.util.List;

@Plugin(name = "Collector", category = "Core", elementType = "filter", printObject = true)
public class Log4j2Collector extends AbstractFilter {
    private Aggregator<LogEvent, String, String> aggregator;
    private final List<Log4j2EventTrigger> triggers = new ArrayList<>();
    private Log4j2MessageWriter messageBuilder;

    private final List<Tag> tags = new ArrayList<>();

    // common
    private String entity;
    private Level level = Level.TRACE;

    // writer
    private WritableByteChannel writer;
    private String writerHost;
    private int writerPort;

    private String writerUrl;
    private String writerUsername;
    private String writerPassword;

    // series sender
    private SeriesSenderConfig seriesSenderConfig;
    private Integer intervalSeconds;
    private Integer minIntervalSeconds;
    private Integer minIntervalThreshold;
    private Integer repeatCount;
    private Integer rateIntervalSeconds;
    private String totalCountInit;
    private String debug;
    private String pattern;
    private final int DEFAULT_INTERVAL = 60;
    private final String DEFAULT_PATTERN = "%m";

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
        if (entity != null) {
            messageBuilder.setEntity(entity);
        }
        if (seriesSenderConfig != null) {
            messageBuilder.setSeriesSenderConfig(seriesSenderConfig);
        }
        if (pattern != null) {
            messageBuilder.setPattern(pattern);
        } else {
            messageBuilder.setPattern(DEFAULT_PATTERN);
        }
        for (Tag tag : tags) {
            messageBuilder.addTag(tag);
        }
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
        messageBuilder.start();
        aggregator.sendInitialTotalCounter();

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
            @PluginAttribute("messages") final String messages,
            @PluginAttribute("level") final Level level,
            @PluginAttribute("writer") final String writer,
            @PluginAttribute("writerHost") final String writerHost,
            @PluginAttribute("writerPort") final int writerPort,
            @PluginAttribute("writerUrl") final String writerUrl,
            @PluginAttribute("writerUsername") final String writerUsername,
            @PluginAttribute("writerPassword") final String writerPassword,
            @PluginAttribute("intervalSeconds") final Integer intervalSeconds,
            @PluginAttribute("minIntervalSeconds") final Integer minIntervalSeconds,
            @PluginAttribute("minIntervalThreshold") final Integer minIntervalThreshold,
            @PluginAttribute("repeatCount") final Integer repeatCount,
            @PluginAttribute("rateIntervalSeconds") final Integer rateIntervalSeconds,
            @PluginAttribute("totalCountInit") final String totalCountInit,
            @PluginAttribute("pattern") final String pattern,
            @PluginAttribute("debug") final String debug) {
        final Level minLevel = level == null ? Level.TRACE : level;
        final Log4j2Collector collector = new Log4j2Collector();
        collector.setEntity(entity);
        collector.setTags(tags);
        collector.setMessages(messages);
        collector.setLevel(minLevel);
        if (writer == null) {
            collector.setWriter("tcp");
        } else {
            collector.setWriter(writer);
        }
        collector.setWriterHost(writerHost);
        if (writerPort == 0) {
            collector.setWriterPort(8081);
        } else {
            collector.setWriterPort(writerPort);
        }
        collector.setWriterUrl(writerUrl);
        collector.setWriterUsername(writerUsername);
        collector.setWriterPassword(writerPassword);
        if (intervalSeconds <= 0) {
            collector.setIntervalSeconds(collector.DEFAULT_INTERVAL);
        } else {
            collector.setIntervalSeconds(intervalSeconds);
        }
        if (minIntervalSeconds > 0) {
            collector.setMinIntervalSeconds(minIntervalSeconds);
        }
        collector.setMinIntervalThreshold(minIntervalThreshold);
        collector.setRepeatCount(repeatCount);
        if (rateIntervalSeconds > 0) {
            collector.setRateIntervalSeconds(rateIntervalSeconds);
        }
        collector.setTotalCountInit(totalCountInit);
        collector.setPattern(pattern);
        collector.setDebug(debug);
        try {
            collector.init();
        } catch (Exception e) {
            AtsdUtil.logError("Could not initialize collector", e);
        }
        return collector;
    }

    private void initSeriesSenderConfig() {
        seriesSenderConfig = new SeriesSenderConfig();
        if (intervalSeconds != null) {
            seriesSenderConfig.setIntervalSeconds(intervalSeconds);
        }
        if (minIntervalSeconds != null) {
            seriesSenderConfig.setMinIntervalSeconds(minIntervalSeconds);
        }
        if (minIntervalThreshold != null) {
            seriesSenderConfig.setMinIntervalThreshold(minIntervalThreshold);
        }
        if (repeatCount != null) {
            seriesSenderConfig.setRepeatCount(repeatCount);
        }
        if (rateIntervalSeconds != null) {
            seriesSenderConfig.setRateIntervalSeconds(rateIntervalSeconds);
        }
        if (totalCountInit != null) {
            final String[] parts = totalCountInit.split(";");
            for (String part : parts) {
                final String[] levelAndCount = part.split("=");
                if (levelAndCount.length > 0) {
                    int value = -1;
                    if (levelAndCount.length >= 2) {
                        try {
                            value = Integer.parseInt(levelAndCount[1]);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    final TotalCountInit totalCountInit = new TotalCountInit(levelAndCount[0], value);
                    seriesSenderConfig.setTotalCountInit(totalCountInit);
                }
            }
        }
    }

    private void initWriter() {
        if (writer instanceof AbstractAtsdWriter) {
            final AbstractAtsdWriter atsdWriter = (AbstractAtsdWriter) this.writer;
            checkWriterProperty(writerHost == null, "writerHost", writerHost);
            checkWriterProperty(writerPort <= 0, "writerPort", Integer.toString(writerPort));
            atsdWriter.setHost(writerHost);
            atsdWriter.setPort(writerPort);
        } else if (writer instanceof HttpAtsdWriter) {
            final HttpAtsdWriter simpleHttpAtsdWriter = new HttpAtsdWriter();
            simpleHttpAtsdWriter.setUrl(writerUrl);
            simpleHttpAtsdWriter.setUsername(writerUsername);
            simpleHttpAtsdWriter.setPassword(writerPassword);
            writer = simpleHttpAtsdWriter;
        } else {
            final String msg = "Undefined writer for Log4jCollector: " + writer;
            StatusLogger.getLogger().error(msg);
            throw new IllegalStateException(msg);
        }
    }

    private void checkWriterProperty(boolean check, String propName, String propValue) {
        if (check) {
            final String msg = "Illegal writer property (" +
                    propName + "): " + propValue;
            StatusLogger.getLogger().error(msg);
            throw new IllegalStateException(msg);
        }
    }

    public void setWriter(String writerTypeName) {
        try {
            final WriterType writerType = WriterType.valueOf(writerTypeName.toUpperCase());
            this.writer = (WritableByteChannel) writerType.getWriterClass().newInstance();
            if (writerPort == 0) {
                writerTypeName = writerTypeName.toLowerCase();
                if (writerTypeName.equals("tcp")) writerPort = 8081;
                if (writerTypeName.equals("udp")) writerPort = 8082;
                if (writerTypeName.equals("http")) writerPort = 8088;
            }
        } catch (Exception e) {
            final String msg = "Could not create writer instance by type: " + writerTypeName + ", "
                    + e.getMessage();
            AtsdUtil.logError(msg);
            throw new IllegalStateException(msg);
        }
    }

    public void setWriterHost(String host) {
        this.writerHost = host;
    }

    public void setWriterPort(int port) {
        this.writerPort = port;
    }

    public void setWriterUrl(String writerUrl) {
        this.writerUrl = writerUrl;
    }

    public void setWriterUsername(String writerUsername) {
        this.writerUsername = writerUsername;
    }

    public void setWriterPassword(String writerPassword) {
        this.writerPassword = writerPassword;
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

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public void setMinIntervalSeconds(int minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public void setMinIntervalThreshold(int minIntervalThreshold) {
        this.minIntervalThreshold = minIntervalThreshold;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
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
//                    if (vParts.length >= 3) {
//                        trigger.setResetIntervalSeconds(Long.parseLong(vParts[2]));
//                    }
//                    if (vParts.length >= 4) {
//                        trigger.setEvery(Integer.parseInt(vParts[3]));
//                    }
                }
                trigger.init();
                triggers.add(trigger);
            }
        }
    }

    public void setRateIntervalSeconds(int rateIntervalSeconds) {
        this.rateIntervalSeconds = rateIntervalSeconds;
    }


    public void setTotalCountInit(String totalCountInit) {
        this.totalCountInit = totalCountInit;
    }

    public void setDebug(String debug) {
        this.debug = debug;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public Result filter(LogEvent event) {
        try {
            if (event.getLevel().intLevel() <= this.level.intLevel()) {
                aggregator.register(event);
            }
        } catch (IOException e) {
            StatusLogger.getLogger().error("Could not write message", e);
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
                ", writerHost='" + writerHost + '\'' +
                ", writerPort=" + writerPort +
                ", writerUrl='" + writerUrl + '\'' +
                ", writerUsername='" + writerUsername + '\'' +
                ", writerPassword='" + writerPassword + '\'' +
                ", seriesSenderConfig=" + seriesSenderConfig +
                ", intervalSeconds=" + intervalSeconds +
                ", minIntervalSeconds=" + minIntervalSeconds +
                ", minIntervalThreshold=" + minIntervalThreshold +
                ", repeatCount=" + repeatCount +
                ", rateIntervalSeconds=" + rateIntervalSeconds +
                ", totalCountInit='" + totalCountInit + '\'' +
                ", debug='" + debug + '\'' +
                ", pattern='" + pattern + '\'' +
                '}';
    }
}
