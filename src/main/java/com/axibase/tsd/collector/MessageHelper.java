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
import com.sun.management.UnixOperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    private boolean sentStatus;
    private ByteBuffer[] props;

    public void setSeriesSenderConfig(SeriesSenderConfig seriesSenderConfig) {
        this.seriesSenderConfig = seriesSenderConfig;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void init(WritableByteChannel writer, Map<String, String> stringSettings) {

        sentStatus = true;
        props = new ByteBuffer[2];

        command = System.getProperty("sun.java.command");
        if (command == null || command.trim().length() == 0) {
            command = "default";
        }
        if (command.contains(" ")) {
            command = command.split(" ")[0];
        }

        sendAggregatorSettings(writer, stringSettings);
        sendAggregatorRuntime(writer);

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

    private void sendAggregatorRuntime(WritableByteChannel writer) {
        StringBuilder sb = new StringBuilder();
        sb.append("property e:").append(entity);
        sb.append(" t:java.log_aggregator.runtime");
        sb.append(" k:command=").append(command);

        Properties systemProperties = System.getProperties();

        Enumeration enumeration = systemProperties.propertyNames();

        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            String value = systemProperties.getProperty(key);
            if (!value.isEmpty()) {
                String s = value.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");
                sb.append(" v:").append(key).append("=\"").append(s).append("\"");
            }
        }

        OperatingSystemMXBean osMXBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        sb.append(" v:").append("Arch=\"").append(osMXBean.getArch()).append("\"");
        sb.append(" v:").append("AvailableProcessors=\"").append(osMXBean.getAvailableProcessors()).append("\"");
        sb.append(" v:").append("Name=\"").append(osMXBean.getName()).append("\"");
        sb.append(" v:").append("Version=\"").append(osMXBean.getVersion()).append("\"");

        try {
            UnixOperatingSystemMXBean osMXBeanUnix = (UnixOperatingSystemMXBean) osMXBean;
            sb.append(" v:").append("MaxFileDescriptorCount=\"").append(osMXBeanUnix.getMaxFileDescriptorCount()).append("\"");
            sb.append(" v:").append("TotalPhysicalMemorySize=\"").append(osMXBeanUnix.getTotalPhysicalMemorySize()).append("\"");
            sb.append(" v:").append("TotalSwapSpaceSize=\"").append(osMXBeanUnix.getTotalSwapSpaceSize()).append("\"");
        } catch (Exception e) {
            AtsdUtil.logError("Writer failed to get java.log_aggregator.runtime properties for UnixOperatingSystem ", e);
        }

        RuntimeMXBean runtimeMXBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
        sb.append(" v:").append("getBootClassPath=\"").append(runtimeMXBean.getBootClassPath()).append("\"");
        String name = runtimeMXBean.getName();
        sb.append(" v:").append("Name=\"").append(name).append("\"");
        if (name.contains("@")) {
            sb.append(" v:").append("Hostname=\"").append(name.substring(name.lastIndexOf("@") + 1)).append("\"");
        }
        sb.append(" v:").append("ClassPath=\"").append(runtimeMXBean.getClassPath()).append("\"");
        sb.append(" v:").append("LibraryPath=\"").append(runtimeMXBean.getLibraryPath()).append("\"");
        sb.append(" v:").append("SpecName=\"").append(runtimeMXBean.getSpecName()).append("\"");
        sb.append(" v:").append("SpecVendor=\"").append(runtimeMXBean.getSpecVendor()).append("\"");
        sb.append(" v:").append("StartTime=\"").append(runtimeMXBean.getStartTime()).append("\"");
        sb.append(" v:").append("VmName=\"").append(runtimeMXBean.getVmName()).append("\"");
        sb.append(" v:").append("VmVendor=\"").append(runtimeMXBean.getVmVendor()).append("\"");
        sb.append(" v:").append("VmVersion=\"").append(runtimeMXBean.getVmVersion()).append("\"");
        List<String> inputArguments = runtimeMXBean.getInputArguments();
        sb.append(" v:").append("InputArguments=\"");
        for (String inputArgument : inputArguments) {
            sb.append(inputArgument).append(" ");
        }
        sb.append("\"");

        sb.append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length).put(bytes);
        byteBuffer.rewind();
        try {
            writer.write(byteBuffer);
            sentStatus = true;
        } catch (IOException e) {
            props[0] = byteBuffer;
            sentStatus = false;
            AtsdUtil.logError("Writer failed to send java.log_aggregator.runtime property command: " + sb.toString(), e);
        }
    }

    private void sendAggregatorSettings(WritableByteChannel writer, Map<String, String> stringSettings) {
        StringBuilder sb = new StringBuilder();
        sb.append("property e:").append(entity);
        sb.append(" t:java.log_aggregator.settings");
        sb.append(" k:command=").append(command);
        for (String key : stringSettings.keySet()) {
            sb.append(" v:").append(key).append("=").append("\"").append(stringSettings.get(key)).append("\"");
        }
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length).put(bytes);
        byteBuffer.rewind();
        try {
            writer.write(byteBuffer);
            sentStatus = true;
        } catch (IOException e) {
            props[1] = byteBuffer;
            sentStatus = false;
            AtsdUtil.logError("Writer failed to send java.log_aggregator.settings property command: " + sb.toString(), e);
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
//        sb.append(" ms:").append(time).append("\n");
        sb.append("\n");
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
//        sb.append(" ms:").append(time).append("\n");
        sb.append("\n");
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
//        sb.append(" ms:").append(time).append("\n");
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesTotalRatePrefix.remaining() + bytes.length)
                .put(seriesTotalRatePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
//        writer.write(byteBuffer);
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
//        sb.append(" ms:").append(System.currentTimeMillis()).append("\n");
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(messagePrefix.remaining() + bytes.length)
                .put(messagePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void checkSentStatus(WritableByteChannel writer) {
        if (!sentStatus) {
            try {
                writer.write(props[0]);
                writer.write(props[1]);
                sentStatus = true;
                AtsdUtil.logInfo("Writer succeeded to send java.log_aggregator property command");
            } catch (IOException e) {
                sentStatus = false;
            }
        }
    }
}
