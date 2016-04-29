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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.status.StatusLogger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    private final List<Tag> tags = new ArrayList<>();

    // common
    private String entity;
    private Level level = Level.TRACE;

    // writer
    private WritableByteChannel writer;
    private String writerHost;
    private int writerPort;

    private String writerUrl;

    // series sender
    private SeriesSenderConfig seriesSenderConfig;
    private Integer intervalSeconds;
    private String debug;
    private String pattern;
    private String scheme;

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
            pattern = DEFAULT_PATTERN;
            messageBuilder.setPattern(DEFAULT_PATTERN);
        }
        if (debug == null) {
            debug = "false";
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
        Map<String, String> stringSettings = new HashMap<>();
        stringSettings.put("debug", debug);
        stringSettings.put("pattern", pattern);
        stringSettings.put("scheme", scheme);
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
            @PluginAttribute("messages") final String messages,
            @PluginAttribute("level") final Level level,
            @PluginAttribute("url") final String url,
            @PluginAttribute("intervalSeconds") final Integer intervalSeconds,
            @PluginAttribute("pattern") final String pattern,
            @PluginAttribute("debug") final String debug) {
        final Level minLevel = level == null ? Level.TRACE : level;
        final Log4j2Collector collector = new Log4j2Collector();
        collector.setEntity(entity);
        collector.setTags(tags);
        collector.setMessages(messages);
        collector.setLevel(minLevel);
        collector.setUrl(url);
        if (intervalSeconds <= 0) {
            collector.setIntervalSeconds(collector.DEFAULT_INTERVAL);
        } else {
            collector.setIntervalSeconds(intervalSeconds);
        }
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
    }

    private void initWriter() {
        final WriterType writerType = WriterType.valueOf(scheme.toUpperCase());
        try {
            this.writer = (WritableByteChannel) writerType.getWriterClass().newInstance();
        } catch (InstantiationException e) {
            final String msg = "Could not create writer instance by type, " + e.getMessage();
            AtsdUtil.logError(msg);
        } catch (IllegalAccessException e) {
            AtsdUtil.logError("Could not instantiate writerType ", e);
        }

        if (writer instanceof AbstractAtsdWriter) {
            final AbstractAtsdWriter atsdWriter = (AbstractAtsdWriter) this.writer;
            checkWriterProperty(writerHost == null, "writerHost", writerHost);
            if (writerPort <= 0)
                switch (scheme.toLowerCase()){
                    case "tcp":
                        writerPort = 8081;
                        break;
                    case "udp":
                        writerPort = 8082;
                        break;
                    default:
                        AtsdUtil.logError("Invalid scheme " + scheme);
                }
            atsdWriter.setHost(writerHost);
            atsdWriter.setPort(writerPort);
            writer = atsdWriter;
        } else if (writer instanceof HttpAtsdWriter) {
            final HttpAtsdWriter simpleHttpAtsdWriter = new HttpAtsdWriter();
            simpleHttpAtsdWriter.setUrl(writerUrl);
            writer = simpleHttpAtsdWriter;
        } else if (writer instanceof HttpsAtsdWriter) {
            final HttpsAtsdWriter simpleHttpsAtsdWriter = new HttpsAtsdWriter();
            simpleHttpsAtsdWriter.setUrl(writerUrl);
            writer = simpleHttpsAtsdWriter;
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

    public void setUrl(String stringURI) {
        try {
            URI uri = new URI(stringURI);
            this.scheme = uri.getScheme();
            if (scheme.equals("http") || scheme.equals("https")) {
                if (uri.getPath().isEmpty())
                    stringURI = stringURI.concat("/api/v1/commands/batch");
                this.writerUrl = stringURI;
            } else {
                this.writerHost = uri.getHost();
                this.writerPort = uri.getPort();
            }
        } catch (URISyntaxException e) {
            AtsdUtil.logError("Could not parse generic url " + stringURI, e);
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

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
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
                ", scheme='" + scheme + '\'' +
                ", seriesSenderConfig=" + seriesSenderConfig +
                ", intervalSeconds=" + intervalSeconds +
                ", debug='" + debug + '\'' +
                ", pattern='" + pattern + '\'' +
                '}';
    }
}
