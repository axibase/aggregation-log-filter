/*
 * Copyright 2015 Axibase Corporation or its affiliates. All Rights Reserved.
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

/**
 * @author Nikolay Malevanny.
 */
public class SeriesSenderConfig {
    public static final int DEFAULT_CHECK_PERIOD_MS = 333;
    public static final String DEFAULT_METRIC_NAME = "log_event";
    public static final String DEFAULT_RATE_SUFFIX = "_rate";
    public static final String DEFAULT_TOTAL_SUFFIX = "_total";
    public static final String DEFAULT_COUNTER_SUFFIX = "_counter";
    public static final int DEFAULT_ZERO_REPEAT_COUNT = 1;
    public static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    public static final long DEFAULT_PERIOD_MS = MINUTE;
    public static final long DEFAULT_MIN_PERIOD_MS = 5 * SECOND;
    public static final int MIN_MESSAGE_SKIP_THRESHOLD = 10;
    public static final int DEFAULT_MESSAGE_SKIP_THRESHOLD = 100;
    public static final int MAX_MESSAGE_SKIP_THRESHOLD = 1000;

    public static final SeriesSenderConfig DEFAULT = new SeriesSenderConfig();

    private String metric = DEFAULT_METRIC_NAME;
    private int zeroRepeatCount = DEFAULT_ZERO_REPEAT_COUNT;
    private long periodMs = DEFAULT_PERIOD_MS;
    private long minPeriodMs = DEFAULT_MIN_PERIOD_MS;
    private int sendThreshold;
    private long ratePeriodMs = MINUTE;

    private String rateSuffix = DEFAULT_RATE_SUFFIX;
    private String totalSuffix = DEFAULT_TOTAL_SUFFIX;
    private String counterSuffix = DEFAULT_COUNTER_SUFFIX;

    private int messageSkipThreshold = DEFAULT_MESSAGE_SKIP_THRESHOLD;
    private int checkPeriodMs = DEFAULT_CHECK_PERIOD_MS;

    public SeriesSenderConfig() {
    }

    public SeriesSenderConfig(int zeroRepeatCount,
                              int periodSeconds,
                              int sendThreshold) {
        this.zeroRepeatCount = zeroRepeatCount;
        this.sendThreshold = sendThreshold;
        setPeriodSeconds(periodSeconds);
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public void setZeroRepeatCount(int zeroRepeatCount) {
        this.zeroRepeatCount = zeroRepeatCount;
    }

    public void setPeriodSeconds(int periodSeconds) {
        if (periodSeconds < 1) {
            throw new IllegalArgumentException("Period value must by more than 0, currently " + periodSeconds);
        }
        this.periodMs = periodSeconds * SECOND;
    }

    public void setMinPeriodSeconds(long minPeriodSeconds) {
        if (minPeriodSeconds < 0) {
            throw new IllegalArgumentException(
                    "Min period value must by more than or equals 0, currently " + minPeriodSeconds);
        }
        this.minPeriodMs = minPeriodSeconds * SECOND;
    }

    public void setSendThreshold(int sendThreshold) {
        this.sendThreshold = sendThreshold;
    }

    public void setTotalSuffix(String totalSuffix) {
        this.totalSuffix = totalSuffix;
    }

    public String getMetric() {
        return metric;
    }

    public int getZeroRepeatCount() {
        return zeroRepeatCount;
    }

    public long getPeriodMs() {
        return periodMs;
    }

    public long getMinPeriodMs() {
        return minPeriodMs;
    }

    public int getSendThreshold() {
        return sendThreshold;
    }

    public String getTotalSuffix() {
        return totalSuffix;
    }

    public long getRatePeriodMs() {
        return ratePeriodMs;
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

    public void setRatePeriodSeconds(long ratePeriodSeconds) {
        if (ratePeriodSeconds < 1) {
            throw new IllegalArgumentException("Period value must by more than 0, currently " + ratePeriodSeconds);
        }
        this.ratePeriodMs = ratePeriodSeconds * SECOND;
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

    public int getCheckPeriodMs() {
        return checkPeriodMs;
    }

    public void setCheckPeriodMs(int checkPeriodMs) {
        this.checkPeriodMs = checkPeriodMs;
    }
}
