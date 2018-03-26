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
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MessageHelper {
    public static final String COMMAND_TAG = "command";
    private static final String LEVEL_TAG = " t:level=";
    private static final String COMMAND_KEY = " k:command=";
    private static final long PROPERTY_SEND_INTERVAL = 15 * 60 * 1000L;
    private static final String SERIES_COMMAND_PREFIX = "series e:";
    private static final String PROPERTY_COMMAND_PREFIX = "property e:";
    private SeriesSenderConfig seriesSenderConfig;
    private ByteBuffer seriesCounterPrefix;
    private ByteBuffer seriesTotalRatePrefix;
    private ByteBuffer seriesTotalCounterPrefix;
    private ByteBuffer messagePrefix;
    private PropertyBuffers propBuffers;
    private Map<String, String> tags;
    private String entity;
    private String command;
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

        propBuffers = new PropertyBuffers();

        command = System.getProperty("sun.java.command");
        if (command == null || command.trim().length() == 0) {
            command = "default";
        }
        if (command.contains(" ")) {
            command = command.split(" ")[0];
        }

        sendAggregatorEnvironmentProperty(writer);
        sendAggregatorSettingsProperty(writer, stringSettings);
        sendAggregatorRuntimeProperty(writer);
        sendAggregatorOSProperty(writer);
        lastPropertySentTime = System.currentTimeMillis();

        {
            StringBuilder sb = new StringBuilder(SERIES_COMMAND_PREFIX).append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeName(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesCounterPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder(SERIES_COMMAND_PREFIX).append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeName(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getRateSuffix())).append(
                    "=");
            seriesTotalRatePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder(SERIES_COMMAND_PREFIX).append(entity);
            appendTags(sb);
            sb.append(" m:").append(AtsdUtil.sanitizeName(
                    seriesSenderConfig.getMetricPrefix() + seriesSenderConfig.getTotalSuffix() + seriesSenderConfig.getCounterSuffix())).append(
                    "=");
            seriesTotalCounterPrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
        {
            StringBuilder sb = new StringBuilder("message e:").append(entity);
            appendTags(sb);
            unsafeAppendTag(sb, "type", "logger");
            sb.append(" m:");
            messagePrefix = ByteBuffer.wrap(sb.toString().getBytes(AtsdUtil.UTF_8));
        }
    }

    private void sendAggregatorEnvironmentProperty(WritableByteChannel writer) {
        StringBuilder sb = new StringBuilder(PROPERTY_COMMAND_PREFIX).append(entity);
        sb.append(" t:java.log_aggregator.environment");
        sb.append(COMMAND_KEY).append(AtsdUtil.sanitizeValue(command));

        Map<String, String> environmentSettings = System.getenv();

        for (Map.Entry<String, String> entry : environmentSettings.entrySet()) {
            String value = entry.getValue();
            if (!value.isEmpty()) {
                sb.append(" v:").append(AtsdUtil.sanitizeName(entry.getKey())).append("=").append(AtsdUtil.sanitizeValue(value));
            }
        }

        if (sb.indexOf(" v:") == -1) {
            AtsdUtil.logInfo("Environment settings are empty. Skip environment property sending");
        } else {
            sb.append("\n");
            propBuffers.initEnvironmentPropertyBuf(sb);
            try {
                propBuffers.writeEnvironmentPropertyBufTo(writer);
            } catch (IOException e) {
                AtsdUtil.logInfo("Writer failed to send java.log_aggregator.environment property");
            }
        }
    }

    private void sendAggregatorRuntimeProperty(WritableByteChannel writer) {
        StringBuilder sb = new StringBuilder(PROPERTY_COMMAND_PREFIX).append(entity);
        sb.append(" t:java.log_aggregator.runtime");
        sb.append(COMMAND_KEY).append(AtsdUtil.sanitizeValue(command));

        Properties systemProperties = System.getProperties();

        Enumeration enumeration = systemProperties.propertyNames();

        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            String value = systemProperties.getProperty(key);
            if (!value.isEmpty()) {
                sb.append(" v:").append(AtsdUtil.sanitizeName(key)).append("=").append(AtsdUtil.sanitizeValue(value));
            }
        }

        try {
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            sb.append(" v:").append("getBootClassPath=").append(AtsdUtil.sanitizeValue(runtimeMXBean.getBootClassPath()));
            String name = runtimeMXBean.getName();
            sb.append(" v:").append("Name=").append(AtsdUtil.sanitizeValue(name));
            if (name.contains("@")) {
                sb.append(" v:").append("Hostname=").append(AtsdUtil.sanitizeValue(name.substring(name.lastIndexOf('@') + 1)));
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
            if (!inputArguments.isEmpty()) {
                sb.append(" v:").append("InputArguments=\"");
                for (String inputArgument : inputArguments) {
                    sb.append(inputArgument).append(" ");
                }
                sb.append("\"");
            }
        } catch (Exception e) {
            AtsdUtil.logError("Writer failed to get runtime properties from runtime MXBean. " + e.getMessage());
        }

        if (sb.indexOf(" v:") == -1) {
            AtsdUtil.logInfo("Runtime settings are empty. Skip runtime property sending");
        } else {
            sb.append("\n");
            propBuffers.initRuntimePropertyBuf(sb);
            try {
                propBuffers.writeRuntimePropertyBufTo(writer);
            } catch (IOException e) {
                AtsdUtil.logInfo("Writer failed to send java.log_aggregator.runtime property");
            }
        }
    }

    private void sendAggregatorSettingsProperty(WritableByteChannel writer, Map<String, String> stringSettings) {
        StringBuilder sb = new StringBuilder(PROPERTY_COMMAND_PREFIX).append(entity);
        sb.append(" t:java.log_aggregator.settings");
        sb.append(COMMAND_KEY).append(AtsdUtil.sanitizeValue(command));

        for (Map.Entry<String, String> entry : stringSettings.entrySet()) {
            sb.append(" v:").append(AtsdUtil.sanitizeName(entry.getKey())).append("=").append(AtsdUtil.sanitizeValue(entry.getValue()));
        }

        sb.append("\n");
        propBuffers.initSettingsPropertyBuf(sb);
        try {
            propBuffers.writeSettingsPropertyBufTo(writer);
        } catch (IOException e) {
            AtsdUtil.logInfo("Writer failed to send java.log_aggregator.settings property");
        }
    }

    private void sendAggregatorOSProperty(WritableByteChannel writer) {
        StringBuilder sb = new StringBuilder(PROPERTY_COMMAND_PREFIX).append(entity);
        sb.append(" t:java.log_aggregator.operating_system");
        sb.append(COMMAND_KEY).append(AtsdUtil.sanitizeValue(command));

        try {
            OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
            sb.append(" v:").append("Arch=").append(AtsdUtil.sanitizeValue(osMXBean.getArch()));
            sb.append(" v:").append("AvailableProcessors=").append(AtsdUtil.sanitizeValue(osMXBean.getAvailableProcessors()));
            sb.append(" v:").append("Name=").append(AtsdUtil.sanitizeValue(osMXBean.getName()));
            sb.append(" v:").append("Version=").append(AtsdUtil.sanitizeValue(osMXBean.getVersion()));

            try {
                if (osMXBean instanceof com.sun.management.UnixOperatingSystemMXBean) {
                    com.sun.management.UnixOperatingSystemMXBean osMXBeanUnix = (com.sun.management.UnixOperatingSystemMXBean) osMXBean;
                    sb.append(" v:").append("MaxFileDescriptorCount=").append(AtsdUtil.sanitizeValue(osMXBeanUnix.getMaxFileDescriptorCount()));
                    sb.append(" v:").append("TotalPhysicalMemorySize=").append(AtsdUtil.sanitizeValue(osMXBeanUnix.getTotalPhysicalMemorySize()));
                    sb.append(" v:").append("TotalSwapSpaceSize=").append(AtsdUtil.sanitizeValue(osMXBeanUnix.getTotalSwapSpaceSize()));
                }
            } catch (Exception e) {
                AtsdUtil.logError("Writer failed to get operating_system properties for UnixOperatingSystem. " + e.getMessage());
            }

            sb.append("\n");
            propBuffers.initOsPropertyBuf(sb);
            try {
                propBuffers.writeOsPropertyBufTo(writer);
            } catch (IOException e) {
                AtsdUtil.logInfo("Writer failed to send java.log_aggregator.operating_system property");
            }

        } catch (Exception e) {
            AtsdUtil.logError("Writer failed to get general operating system properties. Skip operating system property sending. " + e.getMessage());
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
                             Key key,
                             String levelString,
                             long value) throws IOException {
        StringBuilder sb = new StringBuilder(String.valueOf(value));
        sb.append(LEVEL_TAG).append(levelString);
        sb.append(" t:logger=").append(AtsdUtil.sanitizeValue(key.getLogger()));
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesCounterPrefix.remaining() + bytes.length)
                .put(seriesCounterPrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void writeTotalCounter(WritableByteChannel writer,
                                  long time,
                                  CounterWithSum counterWithSum,
                                  String levelString) throws IOException {
        StringBuilder sb = new StringBuilder(String.valueOf(counterWithSum.getSum()));
        sb.append(LEVEL_TAG).append(levelString);
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesTotalCounterPrefix.remaining() + bytes.length)
                .put(seriesTotalCounterPrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void writeTotalRate(WritableByteChannel writer,
                               long time,
                               double rate,
                               String levelString) throws IOException {
        StringBuilder sb = new StringBuilder(String.valueOf(rate));
        sb.append(LEVEL_TAG).append(levelString);
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocate(seriesTotalRatePrefix.remaining() + bytes.length)
                .put(seriesTotalRatePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
    }

    public void writeMessage(WritableByteChannel writer,
                             StringBuilder sb,
                             String message,
                             String levelValue,
                             String loggerName,
                             Map<String, String> locationInformation) throws IOException {
        sb.append(AtsdUtil.escapeCSV(message));
        if ("debug".equalsIgnoreCase(levelValue) || "trace".equalsIgnoreCase(levelValue))
            sb.append(" t:severity=").append("NORMAL");
        else
            sb.append(" t:severity=").append(levelValue);
        sb.append(" t:level=").append(levelValue);
        sb.append(" t:source=").append(AtsdUtil.sanitizeValue(loggerName));
        for (Map.Entry<String, String> entry : locationInformation.entrySet()) {
            sb.append(" t:").append(AtsdUtil.sanitizeName(entry.getKey())).append("=").append(AtsdUtil.sanitizeValue(entry.getValue()));
        }
        sb.append("\n");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocate(messagePrefix.remaining() + bytes.length)
                .put(messagePrefix.duplicate()).put(bytes);
        byteBuffer.rewind();
        writer.write(byteBuffer);
    }

    public void checkSentStatus(WritableByteChannel writer) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPropertySentTime >= PROPERTY_SEND_INTERVAL) {
            try {
                if (propBuffers.isAllBuffersInitialized()) {
                    lastPropertySentTime = currentTime;
                    propBuffers.writeAllTo(writer);
                }
            } catch (IOException e) {
                AtsdUtil.logInfo("Writer failed to send java.log_aggregator property command");
            }
        }
    }
}
