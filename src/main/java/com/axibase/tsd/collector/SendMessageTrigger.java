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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class SendMessageTrigger<E> {
    public static final double ERROR_SKIP_MULTIPLIER = 2.0;
    public static final double WARN_SKIP_MULTIPLIER = 3.0;
    public static final double INFO_SKIP_MULTIPLIER = 5.0;

    public static final int ERROR_STACK_TRACE_LINES = -1;
    public static final int DEFAULT_STACK_TRACE_LINES = 0;

    public static final double DEFAULT_SEND_MULTIPLIER = 1.0;
    public static final long DEFAULT_RESET_INTERVAL = 600 * 1000L;
    public static final int MIN_RESET_INTERVAL_SECONDS = 1;
    private Map<String, History> keyToHistory = new ConcurrentHashMap<>();
    private int stackTraceLines = DEFAULT_STACK_TRACE_LINES;

    private double sendMultiplier = DEFAULT_SEND_MULTIPLIER;
    private long resetInterval = DEFAULT_RESET_INTERVAL;

    public SendMessageTrigger() {
    }

    public boolean onEvent(E event) {
        long st = System.currentTimeMillis();
        String key = resolveKey(event);
        History history = keyToHistory.get(key);
        if (history == null) {
            history = new History();
            history.reset();
            keyToHistory.put(key, history);
        }
        if (st - history.first > resetInterval) {
            history.reset();
        }
        history.count++;
        boolean result = history.count >= history.modEvery;
        if (result && sendMultiplier > 1 && history.count >= 1) {
            history.update(sendMultiplier);
        }
        return result;
    }

    public abstract boolean isErrorInstance(E event);

    public abstract String resolveKey(E event);

    public void setStackTraceLines(int stackTraceLines) {
        this.stackTraceLines = stackTraceLines;
    }

    public int getStackTraceLines() {
        return stackTraceLines;
    }

    public void setSendMultiplier(double sendMultiplier) {
        if (sendMultiplier > DEFAULT_SEND_MULTIPLIER) {
            this.sendMultiplier = sendMultiplier;
        }
    }

    public void setResetIntervalSeconds(long resetIntervalSeconds) {
        if (resetIntervalSeconds >= MIN_RESET_INTERVAL_SECONDS) {
            this.resetInterval = resetIntervalSeconds * 1000;
        }
    }

    public void init() {
        // do nothing
    }

    public abstract int getIntLevel();

    private static class History {
        private volatile long count;
        private volatile long first;
        private double modEvery;

        public void reset() {
            count = 0;
            modEvery = 1;
            first = System.currentTimeMillis();
        }

        public void update(double sendMultiplier) {
            modEvery = modEvery * sendMultiplier;
        }
    }
}
