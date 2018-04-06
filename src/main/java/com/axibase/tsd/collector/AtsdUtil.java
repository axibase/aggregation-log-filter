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

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.String;
import java.lang.StringBuilder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;

public class AtsdUtil {
    public static final String EMPTY_MESSAGE = "\"\"";
    private static InternalLogger internalLogger = InternalLogger.SYSTEM;

    public static final String DEFAULT_ENTITY = "defaultEntity";

    public static final ThreadFactory DAEMON_THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread result = Executors.defaultThreadFactory().newThread(runnable);
            result.setDaemon(true);
            return result;
        }
    };
    private static final Pattern SPACE = Pattern.compile("[[\\s]]");
    private static final Pattern QUOTES = Pattern.compile("[[\'|\"]]");
    private static final Pattern CRLF = Pattern.compile("[\\r\\n]+");
    public static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int TRUNCATE_SIZE = 1000;

    public static String sanitizeEntity(String s) {
        return sanitize(s);
    }

    public static String escapeCSV(String s) {
        if (s == null || s.trim().length() == 0) {
            s = EMPTY_MESSAGE;
        } else
            s = StringEscapeUtils.escapeCsv(s.trim());
        if (s.contains(" ") && !s.startsWith("\"")) {
            StringBuilder sb = new StringBuilder("\"");
            s = sb.append(s).append("\"").toString();
        }
        return s;
    }

    // discard tags with the same names with different case, discard tags with empty values
    static SortedMap<String, String> filterProperties(Map<?, ?> map) {
        SortedMap<String, String> tagMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key instanceof String && value != null && !StringUtils.EMPTY.equals(value)) {
                tagMap.put(key.toString(), value.toString());
            }
        }
        return tagMap;
    }

    public static String sanitizeName(String s) {
        s = StringEscapeUtils.escapeCsv(SPACE.matcher(s.trim()).replaceAll("_"));
        if (s.contains("=") && !s.startsWith("\"")) {
            StringBuilder sb = new StringBuilder("\"");
            s = sb.append(s).append("\"").toString();
        }
        return s;
    }

    public static String sanitizeValue(String s) {
        if (s.length() > TRUNCATE_SIZE) {
            s = s.substring(0, TRUNCATE_SIZE);
        }
        s = CRLF.matcher(s).replaceAll("\\\\n");
        s = escapeCSV(s);
        if ((s.contains(" ") || s.contains("=") || s.contains("\t")) && !s.startsWith("\"")) {
            StringBuilder sb = new StringBuilder("\"");
            s = sb.append(s).append("\"").toString();
        }

        return s;
    }

    public static String sanitizeValue(int i) {
        String s = Integer.toString(i);
        return sanitizeValue(s.trim());
    }

    public static String sanitizeValue(long l) {
        String s = Long.toString(l);
        return sanitizeValue(s.trim());
    }

    public static String sanitize(String s) {
        s = SPACE.matcher(s).replaceAll("_");
        s = QUOTES.matcher(s).replaceAll("");
        return s;
    }

    public static String resolveHostname() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if ("localhost".equals(hostName)) {
                hostName = executeHostname();
            }
            return hostName;
        } catch (UnknownHostException e) {
            AtsdUtil.logInfo("Could not resolve hostname. " + e.getMessage());
            return DEFAULT_ENTITY;
        } catch (IOException e) {
            e.printStackTrace();
            return DEFAULT_ENTITY;
        }
    }

    private static String executeHostname() throws IOException {
        CommandLine cmd = CommandLine.parse("hostname");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(baos));
        executor.execute(cmd);
        return baos.toString().trim();
    }

    public static void logError(String message, Throwable exception) {
        internalLogger.error(message, exception);
    }

    public static void logError(String message) {
        internalLogger.error(message);
    }

    public static void logWarn(String message) {
        internalLogger.warn(message);
    }

    public static void logInfo(String message) {
        internalLogger.info(message);
    }

    public static void logInfo(String message, Throwable exception) {
        internalLogger.info(message, exception);
    }

    public static void setInternalLogger(InternalLogger internalLogger) {
        AtsdUtil.internalLogger = internalLogger;
    }
}
