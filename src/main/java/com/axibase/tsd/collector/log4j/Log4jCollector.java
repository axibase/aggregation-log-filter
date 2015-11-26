package com.axibase.tsd.collector.log4j;

import com.axibase.tsd.collector.Aggregator;
import com.axibase.tsd.collector.AtsdUtil;
import com.axibase.tsd.collector.InternalLogger;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.Tag;
import com.axibase.tsd.collector.config.TotalCountInit;
import com.axibase.tsd.collector.writer.AbstractAtsdWriter;
import com.axibase.tsd.collector.writer.HttpStreamingAtsdWriter;
import com.axibase.tsd.collector.writer.WriterType;
import org.apache.log4j.Level;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import sun.rmi.runtime.Log;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Nikolay Malevanny.
 */
public class Log4jCollector extends Filter {
    private Aggregator<LoggingEvent, String, Level> aggregator;
    private final List<Log4jEventTrigger> triggers = new ArrayList<Log4jEventTrigger>();
    private Log4jMessageWriter messageBuilder;

    private final List<Tag> tags = new ArrayList<Tag>();

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
    private String metricPrefix;
    private Integer rateIntervalSeconds;
    private String totalCountInit;

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
        });
    }

    @Override
    public void activateOptions() {
        super.activateOptions();
        initWriter();
        initSeriesSenderConfig();

        messageBuilder = new Log4jMessageWriter();
        if (entity != null) {
            messageBuilder.setEntity(entity);
        }
        if (seriesSenderConfig != null) {
            messageBuilder.setSeriesSenderConfig(seriesSenderConfig);
        }
        for (Tag tag : tags) {
            messageBuilder.addTag(tag);
        }
        aggregator = new Aggregator<LoggingEvent, String, Level>(messageBuilder, new Log4jEventProcessor());
        aggregator.setWriter(writer);
        if (seriesSenderConfig != null) {
            aggregator.setSeriesSenderConfig(seriesSenderConfig);
        }
        for (Log4jEventTrigger trigger : triggers) {
            aggregator.addSendMessageTrigger(trigger);
        }
        aggregator.start();
        messageBuilder.start();
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
        if (metricPrefix != null) {
            seriesSenderConfig.setMetricPrefix(metricPrefix);
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
        } else if (writer instanceof HttpStreamingAtsdWriter) {
            final HttpStreamingAtsdWriter httpWriter = (HttpStreamingAtsdWriter) this.writer;
            checkWriterProperty(writerUrl == null, "writerUrl", writerUrl);
            checkWriterProperty(writerUsername == null, "writerUsername", writerUsername);
            checkWriterProperty(writerPassword == null, "writerPassword", writerPassword);
            httpWriter.setUrl(writerUrl);
            httpWriter.setUsername(writerUsername);
            httpWriter.setPassword(writerPassword);
        } else {
            final String msg = "Undefined writer for Log4jCollector: " + writer;
            LogLog.error(msg);
            throw new IllegalStateException(msg);
        }
    }

    private void checkWriterProperty(boolean check, String propName, String propValue) {
        if (check) {
            final String msg = "Illegal writer property (" +
                    propName + "): " + propValue;
            LogLog.error(msg);
            throw new IllegalStateException(msg);
        }
    }

    public void setWriter(String writerTypeName) {
        try {
            final WriterType writerType = WriterType.valueOf(writerTypeName.toUpperCase());
            this.writer = (WritableByteChannel) writerType.getWriterClass().newInstance();
        } catch (Exception e) {
            final String msg = "Could not create writer instance by type: " + writerTypeName + ", "
                    + e.getMessage();
            LogLog.error(msg);
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

    public void setMetricPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
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
                    if (vParts.length >= 3) {
                        trigger.setResetIntervalSeconds(Long.parseLong(vParts[2]));
                    }
                    if (vParts.length >= 4) {
                        trigger.setEvery(Integer.parseInt(vParts[3]));
                    }
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
