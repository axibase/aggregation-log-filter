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

package com.axibase.tsd.collector.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.axibase.tsd.collector.SendMessageTrigger;

public class LogbackEventTrigger<E extends ILoggingEvent> extends SendMessageTrigger<E> {
    public static final Level DEFAULT_LEVEL = Level.WARN;
    private Level level = DEFAULT_LEVEL;

    private boolean definedSendMultiplier = false;

    public LogbackEventTrigger() {
        super();
    }

//    public LogbackEventTrigger(int every) {
//        super();
//        setEvery(every);
//    }

    public LogbackEventTrigger(Level level) {
        super();
        this.level = level;
    }

    @Override
    public int getIntLevel() {
        return level.levelInt;
    }

    @Override
    public boolean onEvent(E event) {
        return event != null && event.getLevel().levelInt == level.levelInt && super.onEvent(event);
    }

    @Override
    public boolean isErrorInstance(E event) {
        return (event.getLevel().toInt() == Level.ERROR.toInt() && Error.class.isInstance(((ThrowableProxy) event.getThrowableProxy()).getThrowable()));
    }

    @Override
    public String resolveKey(E event) {
        return event.getLoggerName();
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public void setSendMultiplier(double sendMultiplier) {
        super.setSendMultiplier(sendMultiplier);
        definedSendMultiplier = true;
    }

    @Override
    public void init() {
        if (!definedSendMultiplier) {
            if (level.levelInt >= Level.ERROR_INT) {
                setSendMultiplier(ERROR_SKIP_MULTIPLIER);
                setStackTraceLines(ERROR_STACK_TRACE_LINES);
            } else if (level.levelInt >= Level.WARN_INT) {
                setSendMultiplier(WARN_SKIP_MULTIPLIER);
            } else {
                setSendMultiplier(INFO_SKIP_MULTIPLIER);
            }
        }
    }
}
