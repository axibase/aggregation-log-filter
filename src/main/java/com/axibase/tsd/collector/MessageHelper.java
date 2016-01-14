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

package com.axibase.tsd.collector;

import com.axibase.tsd.collector.config.SeriesSenderConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 * @author Nikolay Malevanny.
 */
public class MessageHelper {
    public static final String COMMAND_TAG = "command";
    private SeriesSenderConfig seriesSenderConfig;
    private ByteBuffer seriesCounterPrefix;
    private ByteBuffer seriesTotalRatePrefix;
    private ByteBuffer seriesTotalCounterPrefix;
    private ByteBuffer messagePrefix;
    private Map<String, String> tags;
    private String entity;
    private String command;

    public void setSeriesSenderConfig(SeriesSenderConfig seriesSenderConfig) {
        this.seriesSenderConfig = seriesSenderConfig;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void init() {
        command = System.getProperty("sun.java.command");
        if (command == null || command.trim().length() == 0) {
            command = "default";
        }
        if (command.contains(" ")) {
            command = command.split(" ")[0];
        }

        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(entity);
            appendTags(sb);
            sb.append(" m:").append(
                    AtsdUtil.sanitizeMetric(
                            seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesCounterPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeMetric(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getRateSuffix())).append(
                    "=");
            seriesTotalRatePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeMetric(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesTotalCounterPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("message e:").append(entity);
            appendTags(sb);
            unsafeAppendTag(sb, "type", "logger");
            sb.append(" m:");
            messagePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
    }

    private void appendTags(StringBuilder sb) {
        if (!tags.containsKey(COMMAND_TAG)) {
            unsafeAppendTag(sb, COMMAND_TAG, command);
        }
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tagName = AtsdUtil.sanitizeTagKey(entry.getKey());
            String tagValue = AtsdUtil.sanitizeTagValue(entry.getValue());
            unsafeAppendTag(sb, tagName, tagValue);
        }
    }

    private void unsafeAppendTag(StringBuilder sb, String tagName, String tagValue) {
        sb.append(" t:").append(tagName).append("=").append(tagValue);
    }

    public void writeCounter(WritableByteChannel writer,
                              long time,
                              Key key,
                              String levelString,
                              long value) throws IOException {
        StringBuilder sb = new StringBuilder().append(value);
        sb.append(" t:level=").append(levelString);
        sb.append(" t:logger=").append(AtsdUtil.sanitizeTagValue(key.getLogger()));
        sb.append(" ms:").append(time).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesCounterPrefix.remaining() + bytes.length)
                .put(seriesCounterPrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void writeTotalCounter(WritableByteChannel writer,
                                   long time,
                                   CounterWithSum counterWithSum,
                                   String levelString) throws IOException {
        StringBuilder sb = new StringBuilder().append(counterWithSum.getSum());
        sb.append(" t:level=").append(levelString);
        sb.append(" ms:").append(time).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesTotalCounterPrefix.remaining() + bytes.length)
                .put(seriesTotalCounterPrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void writeTotalRate(WritableByteChannel writer,
                                long time,
                                double rate,
                                String levelString) throws IOException {
        StringBuilder sb = new StringBuilder().append(rate);
        sb.append(" t:level=").append(levelString);
        sb.append(" ms:").append(time).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesTotalRatePrefix.remaining() + bytes.length)
                .put(seriesTotalRatePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void writeMessage(WritableByteChannel writer,
                              StringBuilder sb,
                              String message,
                              String levelValue,
                              String loggerName) throws IOException {
        sb.append(AtsdUtil.sanitizeMessage(message));
        sb.append(" t:severity=").append(levelValue);
        sb.append(" t:level=").append(levelValue);
        sb.append(" t:source=").append(AtsdUtil.sanitizeTagValue(loggerName));
        sb.append(" ms:").append(System.currentTimeMillis()).append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(messagePrefix.remaining() + bytes.length)
                .put(messagePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }
}
