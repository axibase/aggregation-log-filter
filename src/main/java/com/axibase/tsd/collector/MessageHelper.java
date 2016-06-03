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
    private static final long PROPERTY_SEND_INTERVAL = 15 * 60 * 1000;
    private SeriesSenderConfig seriesSenderConfig;
    private ByteBuffer seriesCounterPrefix;
    private ByteBuffer seriesTotalRatePrefix;
    private ByteBuffer seriesTotalCounterPrefix;
    private ByteBuffer messagePrefix;
    private Map<String, String> tags;
    private String entity;
    private String command;
    private ByteBuffer[] props;
    private long lastPropertySentTime;

    public void setSeriesSenderConfig(SeriesSenderConfig seriesSenderConfig) {
        this.seriesSenderConfig = seriesSenderConfig;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void setEntity(String entity) {
        this.entity = AtsdUtil.sanitizeEntity(entity);
    }

    public void init(WritableByteChannel writer, Map<String, String> stringSettings) {

        props = new ByteBuffer[2];

        command = System.getProperty("sun.java.command");
        if (command == null || command.trim().length() == 0) {
            command = "default";
        }
        if (command.contains(" ")) {
            command = command.split(" ")[0];
        }

        sendAggregatorSettingsProperty(writer, stringSettings);
        sendAggregatorRuntimeProperty(writer);
        lastPropertySentTime = System.currentTimeMillis();

        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeName(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesCounterPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeName(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getRateSuffix())).append(
                    "=");
            seriesTotalRatePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder();
            sb.append("series e:").append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeName(
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

    private void sendAggregatorRuntimeProperty(WritableByteChannel writer) {
        StringBuilder sb = new StringBuilder();
        sb.append("property e:").append(entity);
        sb.append(" t:java.log_aggregator.runtime");
        sb.append(" k:command=").append(AtsdUtil.sanitizeValue(command));

        Properties systemProperties = System.getProperties();

        Enumeration enumeration = systemProperties.propertyNames();

        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            String value = systemProperties.getProperty(key);
            if (!value.isEmpty()) {
                String s = value.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");
                sb.append(" v:").append(AtsdUtil.sanitizeName(key)).append("=").append(AtsdUtil.sanitizeValue(s));
            }
        }

        OperatingSystemMXBean osMXBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        sb.append(" v:").append("Arch=").append(AtsdUtil.sanitizeValue(osMXBean.getArch()));
        sb.append(" v:").append("AvailableProcessors=").append(AtsdUtil.sanitizeValue(osMXBean.getAvailableProcessors()));
        sb.append(" v:").append("Name=").append(AtsdUtil.sanitizeValue(osMXBean.getName()));
        sb.append(" v:").append("Version=").append(AtsdUtil.sanitizeValue(osMXBean.getVersion()));

        try {
            UnixOperatingSystemMXBean osMXBeanUnix = (UnixOperatingSystemMXBean) osMXBean;
            sb.append(" v:").append("MaxFileDescriptorCount=").append(AtsdUtil.sanitizeValue(osMXBeanUnix.getMaxFileDescriptorCount()));
            sb.append(" v:").append("TotalPhysicalMemorySize=").append(AtsdUtil.sanitizeValue(osMXBeanUnix.getTotalPhysicalMemorySize()));
            sb.append(" v:").append("TotalSwapSpaceSize=").append(AtsdUtil.sanitizeValue(osMXBeanUnix.getTotalSwapSpaceSize()));
        } catch (Exception e) {
            AtsdUtil.logError("Writer failed to get java.log_aggregator.runtime properties for UnixOperatingSystem. " + e.getMessage());
        }

        RuntimeMXBean runtimeMXBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
        sb.append(" v:").append("getBootClassPath=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getBootClassPath()));
        String name = runtimeMXBean.getName();
        sb.append(" v:").append("Name=").append(AtsdUtil.sanitizeValue(name));
        if (name.contains("@")) {
            sb.append(" v:").append("Hostname=").append(AtsdUtil.sanitizeValue(name.substring(name.lastIndexOf("@") + 1)));
        }
        sb.append(" v:").append("ClassPath=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getClassPath()));
        sb.append(" v:").append("LibraryPath=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getLibraryPath()));
        sb.append(" v:").append("SpecName=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getSpecName()));
        sb.append(" v:").append("SpecVendor=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getSpecVendor()));
        sb.append(" v:").append("StartTime=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getStartTime()));
        sb.append(" v:").append("VmName=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getVmName()));
        sb.append(" v:").append("VmVendor=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getVmVendor()));
        sb.append(" v:").append("VmVersion=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getVmVersion()));
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
        props[0] = byteBuffer;
        try {
            writer.write(props[0]);
            props[0].rewind();
        } catch (IOException e) {
            AtsdUtil.logInfo("Writer failed to send java.log_aggregator.runtime property");
        }
    }

    private void sendAggregatorSettingsProperty(WritableByteChannel writer, Map<String, String> stringSettings) {
        StringBuilder sb = new StringBuilder();
        sb.append("property e:").append(entity);
        sb.append(" t:java.log_aggregator.settings");
        sb.append(" k:command=").append(AtsdUtil.sanitizeValue(command));
        for (String key : stringSettings.keySet()) {
            String value = stringSettings.get(key);
            sb.append(" v:").append(AtsdUtil.sanitizeName(key)).append("=").append(AtsdUtil.sanitizeValue(value));
        }
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length).put(bytes);
        byteBuffer.rewind();
        props[1] = byteBuffer;
        try {
            writer.write(props[1]);
            props[1].rewind();
        } catch (IOException e) {
            AtsdUtil.logInfo("Writer failed to send java.log_aggregator.settings property");
        }
    }

    private void appendTags(StringBuilder sb) {
        if (!tags.containsKey(COMMAND_TAG)) {
            unsafeAppendTag(sb, COMMAND_TAG, command);
        }
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tagName = AtsdUtil.sanitizeName(entry.getKey());
            String tagValue = AtsdUtil.sanitizeValue(entry.getValue());
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
        sb.append(" t:logger=").append(AtsdUtil.sanitizeValue(key.getLogger()));
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
                             String loggerName,
                             Map<String, String> locationInformation) throws IOException {
        sb.append(AtsdUtil.escapeCSV(message));
        if (levelValue.toLowerCase().equals("trace") || levelValue.toLowerCase().equals("debug"))
            sb.append(" t:severity=").append("NORMAL");
        else
            sb.append(" t:severity=").append(levelValue);
        sb.append(" t:level=").append(levelValue);
        sb.append(" t:source=").append(AtsdUtil.sanitizeValue(loggerName));
        for (String key : locationInformation.keySet()) {
            sb.append(" t:").append(AtsdUtil.sanitizeName(key)).append("=").append(AtsdUtil.sanitizeValue(locationInformation.get(key)));
        }
//        sb.append(" ms:").append(System.currentTimeMillis()).append("\n");
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(messagePrefix.remaining() + bytes.length)
                .put(messagePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void checkSentStatus(WritableByteChannel writer) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPropertySentTime >= PROPERTY_SEND_INTERVAL) {
            try {
                if (props[0] != null && props[1] != null) {
                    writer.write(props[0]);
                    writer.write(props[1]);
                    props[0].rewind();
                    props[1].rewind();
                    lastPropertySentTime = currentTime;
                }
            } catch (IOException e) {
                AtsdUtil.logInfo("Writer failed to send java.log_aggregator property command");
            }
        }
    }
}
