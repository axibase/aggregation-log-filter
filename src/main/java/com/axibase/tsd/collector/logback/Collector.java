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
import com.axibase.tsd.collector.InternalLogger;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import com.axibase.tsd.collector.writer.AbstractAtsdWriter;
import com.axibase.tsd.collector.writer.HttpAtsdWriter;
import com.axibase.tsd.collector.writer.LoggingWrapper;
import com.axibase.tsd.collector.writer.WriterType;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

public class Collector<E extends ILoggingEvent> extends Filter<E> implements ContextAware {
    private LogbackMessageWriter<E> logbackMessageBuilder;
    private Aggregator<E, String, Level> aggregator;
    private Level level = Level.TRACE;
    private SeriesSenderConfig seriesSenderConfig;
    private String entity;
    private final List<LogbackEventTrigger<E>> triggers = new ArrayList<LogbackEventTrigger<E>>();
    private final List<Tag> tags = new ArrayList<Tag>();
    private WritableByteChannel writer;
    private String writerType;
    private String host;
    private int port;
    private String url;
    private String username;
    private String password;
    private String debug;
    private String pattern;

    @Override
    public FilterReply decide(E event) {
        try {
            if (event.getLevel().isGreaterOrEqual(level)) {
                aggregator.register(event);
            }
        } catch (IOException e) {
            addError("Could not write message", e);
        }
        return FilterReply.NEUTRAL;
    }

    @Override
    public void start() {
        super.start();
        logbackMessageBuilder = new LogbackMessageWriter<E>();
        if (entity != null) {
            logbackMessageBuilder.setEntity(entity);
        }
        if (seriesSenderConfig != null) {
            logbackMessageBuilder.setSeriesSenderConfig(seriesSenderConfig);
        }
        if (pattern != null) {
            logbackMessageBuilder.setPattern(pattern);
        }
        logbackMessageBuilder.setContext(getContext());
        for (Tag tag : tags) {
            logbackMessageBuilder.addTag(tag);
        }
        aggregator = new Aggregator<E, String, Level>(logbackMessageBuilder, new LogbackEventProcessor<E>());
        initWriter();
        if (debug != null) {
            writer = new LoggingWrapper(writer);
        }
        aggregator.setWriter(writer);
        if (seriesSenderConfig != null) {
            aggregator.setSeriesSenderConfig(seriesSenderConfig);
        }

        aggregator.addSendMessageTrigger(new LogbackEventTrigger<E>(Level.ERROR));
        aggregator.addSendMessageTrigger(new LogbackEventTrigger<E>(Level.WARN));
        aggregator.addSendMessageTrigger(new LogbackEventTrigger<E>(Level.INFO));

        for (LogbackEventTrigger<E> trigger : triggers) {
            aggregator.addSendMessageTrigger(trigger);
        }
        aggregator.start();
        logbackMessageBuilder.start();
        aggregator.sendInitialTotalCounter();

    }

    private void initWriter() {
        if (writer == null) {
            if (writerType == null) {
                writerType = "tcp";
            }
            final WriterType w = WriterType.valueOf(writerType.toUpperCase());
            try {
                this.writer = (WritableByteChannel) w.getWriterClass().newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            if (port == 0) {
                if (writerType.equals("tcp")) port = 8081;
                if (writerType.equals("udp")) port = 8082;
                if (writerType.equals("http")) port = 8088;
            }
            if (writer instanceof AbstractAtsdWriter) {
                final AbstractAtsdWriter atsdWriter = (AbstractAtsdWriter) this.writer;
                atsdWriter.setHost(host);
                atsdWriter.setPort(port);
            } else if (writer instanceof HttpAtsdWriter) {
                final HttpAtsdWriter simpleHttpAtsdWriter = new HttpAtsdWriter();
                simpleHttpAtsdWriter.setUrl(url);
                simpleHttpAtsdWriter.setUsername(username);
                simpleHttpAtsdWriter.setPassword(password);
                writer = simpleHttpAtsdWriter;
            } else {
                final String msg = "Undefined writer for Collector: " + writer;
                throw new IllegalStateException(msg);
            }
        }
    }

    @Override
    public void stop() {
        AtsdUtil.setInternalLogger(new InternalLogger() {
            @Override
            public void error(String message, Throwable exception) {
                addError(message, exception);
            }

            @Override
            public void error(String message) {
                addError(message);
            }

            @Override
            public void warn(String message) {
                addWarn(message);
            }

            @Override
            public void info(String message) {
                addInfo(message);
            }

            @Override
            public void info(String message, Throwable exception) {
                addInfo(message, exception);
            }
        });
        super.stop();
        aggregator.stop();
        logbackMessageBuilder.stop();
    }

    public void setTag(Tag tag) {
        tags.add(tag);
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setWriter(WritableByteChannel writer) {
        this.writer = writer;
    }

    public void setWriterType(String writerTypeName) {
        writerType = writerTypeName;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void setSendMessage(LogbackEventTrigger<E> messageTrigger) {
//        if (messageTrigger.getEvery() > 0) {
        triggers.add(messageTrigger);
//        }
    }

    public void setSendSeries(SeriesSenderConfig seriesSenderConfig) {
        this.seriesSenderConfig = seriesSenderConfig;
    }

    public void setDebug(String debug) {
        this.debug = debug;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

}
