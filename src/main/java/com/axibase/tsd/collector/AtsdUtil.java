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

import org.apache.commons.lang3.StringEscapeUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
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
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    public static String sanitizeEntity(String s) {
        return sanitize(s);
    }

    public static String escapeCSV(String s) {
        String escaped;
        if (s == null || s.trim().length() == 0) {
            escaped = EMPTY_MESSAGE;
        } else
            escaped = StringEscapeUtils.escapeCsv(s.trim());
        if (escaped.contains(" ") && !escaped.startsWith("\"")) {
            StringBuilder sb = new StringBuilder("\"");
            escaped = sb.append(escaped).append("\"").toString();
        }
        return escaped;
    }

    public static String sanitizeName(String s) {
        String sanitized = StringEscapeUtils.escapeCsv(SPACE.matcher(s.trim()).replaceAll("_"));
        if (sanitized.contains("=") && !sanitized.startsWith("\"")) {
            StringBuilder sb = new StringBuilder("\"");
            sanitized = sb.append(sanitized).append("\"").toString();
        }
        return sanitized;
    }

    public static String sanitizeValue(String s) {
        String sanitized = escapeCSV(s);
        if ((sanitized.contains(" ") || sanitized.contains("=") || sanitized.contains("\t")) && !sanitized.startsWith("\"")) {
            StringBuilder sb = new StringBuilder("\"");
            sanitized = sb.append(sanitized).append("\"").toString();
        }

        return sanitized;
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
        String sanitized = SPACE.matcher(s).replaceAll("_");
        sanitized = QUOTES.matcher(sanitized).replaceAll("");
        return sanitized;
    }

    public static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            AtsdUtil.logInfo("Could not resolve hostname", e);
            return DEFAULT_ENTITY;
        }
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
