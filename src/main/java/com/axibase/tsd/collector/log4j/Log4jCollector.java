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
import java.net.URI;
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

    private final List<Tag> tags = new ArrayList<Tag>();

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
    private Boolean sendLoggerCounter;
    private String debug;
    private String pattern;
    private String scheme;
    private String atsdUrl;

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
        log4jMessageWriter.setAtsdUrl(atsdUrl);
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
        aggregator = new Aggregator<LoggingEvent, String, String>(log4jMessageWriter, new Log4jEventProcessor());
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
        stringSettings.put("scheme", scheme);
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
        final WriterType writerType = WriterType.valueOf(scheme.toUpperCase());
        try {
            this.writer = (WritableByteChannel) writerType.getWriterClass().newInstance();
        } catch (InstantiationException e) {
            final String msg = "Could not create writer instance by type, "
                    + e.getMessage();
            LogLog.error(msg);
        } catch (IllegalAccessException e) {
            LogLog.error("Could not instantiate writerType ", e);
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
                        LogLog.error("Invalid scheme " + scheme);
                }
            atsdWriter.setHost(writerHost);
            atsdWriter.setPort(writerPort);
            writer = atsdWriter;
        } else if (writer instanceof HttpAtsdWriter) {
            final HttpAtsdWriter simpleHttpAtsdWriter = new HttpAtsdWriter();
            simpleHttpAtsdWriter.setUrl(writerUrl);
            writer = simpleHttpAtsdWriter;
            if (writerPort <= 0)
                writerPort = 80;
        } else if (writer instanceof HttpsAtsdWriter) {
            final HttpsAtsdWriter simpleHttpsAtsdWriter = new HttpsAtsdWriter();
            simpleHttpsAtsdWriter.setUrl(writerUrl);
            writer = simpleHttpsAtsdWriter;
            if (writerPort <= 0)
                writerPort = 443;
        } else {
            final String msg = "Undefined writer for Log4jCollector: " + writer;
            LogLog.error(msg);
            throw new IllegalStateException(msg);
        }
        atsdUrl = scheme + "://" + writerHost + ":" + writerPort;
    }

    private void checkWriterProperty(boolean check, String propName, String propValue) {
        if (check) {
            final String msg = "Illegal writer property (" +
                    propName + "): " + propValue;
            LogLog.error(msg);
            throw new IllegalStateException(msg);
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
        try {
            URI uri = new URI(atsdUrl);
            this.scheme = uri.getScheme();
            if (scheme.equals("http") || scheme.equals("https")) {
                if (uri.getPath().isEmpty())
                    atsdUrl = atsdUrl.concat("/api/v1/commands/batch");
                writerUrl = atsdUrl;
            }
            writerHost = uri.getHost();
            writerPort = uri.getPort();
        } catch (URISyntaxException e) {
            LogLog.error("Syntax error in atsd-url " + atsdUrl);
        }
    }

    @Override
    public int decide(LoggingEvent event) {
        try {
            if (event.getLevel().isGreaterOrEqual(level)) {
                aggregator.register(event);
            }
        } catch (IOException e) {
            LogLog.error("Could not write message", e);
        }
        return NEUTRAL;
    }
}
