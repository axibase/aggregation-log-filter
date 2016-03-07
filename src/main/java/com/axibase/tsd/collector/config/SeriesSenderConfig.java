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

package com.axibase.tsd.collector.config;

import java.util.HashMap;
import java.util.Map;

public class SeriesSenderConfig {
    public static final int DEFAULT_CHECK_INTERVAL_MS = 333;
    public static final String DEFAULT_METRIC_PREFIX = "log_event";
    public static final String DEFAULT_RATE_SUFFIX = "_rate";
    public static final String DEFAULT_TOTAL_SUFFIX = "_total";
    public static final String DEFAULT_COUNTER_SUFFIX = "_counter";
    public static final int DEFAULT_REPEAT_COUNT = 1;
    public static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    public static final long DEFAULT_INTERVAL_MS = MINUTE;
    public static final long DEFAULT_MIN_INTERVAL_MS = 5 * SECOND;
    public static final int MIN_MESSAGE_SKIP_THRESHOLD = 10;
    public static final int DEFAULT_MESSAGE_SKIP_THRESHOLD = 100;
    public static final int MAX_MESSAGE_SKIP_THRESHOLD = 1000;

    public static final SeriesSenderConfig DEFAULT = new SeriesSenderConfig();

    private String metricPrefix = DEFAULT_METRIC_PREFIX;
    private int repeatCount = DEFAULT_REPEAT_COUNT;
    private long intervalMs = DEFAULT_INTERVAL_MS;
    private long minIntervalMs = DEFAULT_MIN_INTERVAL_MS;
    private int minIntervalThreshold;
    private long rateIntervalMs = MINUTE;

    private String rateSuffix = DEFAULT_RATE_SUFFIX;
    private String totalSuffix = DEFAULT_TOTAL_SUFFIX;
    private String counterSuffix = DEFAULT_COUNTER_SUFFIX;

    private int messageSkipThreshold = DEFAULT_MESSAGE_SKIP_THRESHOLD;
    private int checkIntervalMs = DEFAULT_CHECK_INTERVAL_MS;

    private Map<String, Integer> totalCountInitMap = new HashMap<String, Integer>();

    public SeriesSenderConfig() {
        totalCountInitMap.put("INFO",0);
        totalCountInitMap.put("WARN",0);
        totalCountInitMap.put("ERROR",0);
    }

    public SeriesSenderConfig(int repeatCount,
                              int intervalSeconds,
                              int minIntervalThreshold) {
        this();
        this.repeatCount = repeatCount;
        this.minIntervalThreshold = minIntervalThreshold;
        setIntervalSeconds(intervalSeconds);
    }

    public void setMetricPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("Interval value must by more than 0, currently " + intervalSeconds);
        }
        this.intervalMs = intervalSeconds * SECOND;
    }

    public void setMinIntervalSeconds(long minIntervalSeconds) {
        if (minIntervalSeconds < 0) {
            throw new IllegalArgumentException(
                    "Min interval value must by more than or equals 0, currently " + minIntervalSeconds);
        }
        this.minIntervalMs = minIntervalSeconds * SECOND;
    }

    public void setMinIntervalThreshold(int minIntervalThreshold) {
        this.minIntervalThreshold = minIntervalThreshold;
    }

    public void setTotalSuffix(String totalSuffix) {
        this.totalSuffix = totalSuffix;
    }

    public String getMetricPrefix() {
        return metricPrefix;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }

    public int getMinIntervalThreshold() {
        return minIntervalThreshold;
    }

    public String getTotalSuffix() {
        return totalSuffix;
    }

    public long getRateIntervalMs() {
        return rateIntervalMs;
    }

    public String getRateSuffix() {
        return rateSuffix;
    }

    public void setRateSuffix(String rateSuffix) {
        this.rateSuffix = rateSuffix;
    }

    public String getCounterSuffix() {
        return counterSuffix;
    }

    public void setCounterSuffix(String counterSuffix) {
        this.counterSuffix = counterSuffix;
    }

    public void setRateIntervalSeconds(long rateIntervalSeconds) {
        if (rateIntervalSeconds < 1) {
            throw new IllegalArgumentException("Interval value must by more than 0, currently " + rateIntervalSeconds);
        }
        this.rateIntervalMs = rateIntervalSeconds * SECOND;
    }

    public void setMessageSkipThreshold(int messageSkipThreshold) {
        if (messageSkipThreshold < MIN_MESSAGE_SKIP_THRESHOLD) {
            this.messageSkipThreshold = MIN_MESSAGE_SKIP_THRESHOLD;
        } else if (messageSkipThreshold>MAX_MESSAGE_SKIP_THRESHOLD) {
            this.messageSkipThreshold = MAX_MESSAGE_SKIP_THRESHOLD;
        } else {
            this.messageSkipThreshold = messageSkipThreshold;
        }
    }

    public int getMessageSkipThreshold() {
        return messageSkipThreshold;
    }

    public int getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(int checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public void setTotalCountInit(TotalCountInit countInit) {
        if (countInit != null && countInit.getLevel() != null && countInit.getLevel().trim().length() > 0) {
            totalCountInitMap.put(countInit.getLevel().trim(), countInit.getValue());
        }
    }

    public Map<String, Integer> getTotalCountInitMap() {
        return totalCountInitMap;
    }
}
