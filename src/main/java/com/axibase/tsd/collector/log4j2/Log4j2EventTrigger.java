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

package com.axibase.tsd.collector.log4j2;

import com.axibase.tsd.collector.SendMessageTrigger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;

public class Log4j2EventTrigger extends SendMessageTrigger<LogEvent> {
    public static final Level DEFAULT_LEVEL = Level.WARN;
    private Level level = DEFAULT_LEVEL;

    private boolean definedSendMultiplier = false;

    public Log4j2EventTrigger() {
        super();
    }

//    public Log4j2EventTrigger(int every) {
//        super();
//        setEvery(every);
//    }


    public Log4j2EventTrigger(Level level) {
        super();
        this.level = level;
    }

    @Override
    public boolean onEvent(LogEvent event) {
        return event != null && event.getLevel().intLevel() == level.intLevel() && super.onEvent(event);
    }

    @Override
    public boolean isErrorInstance(LogEvent event) {
        return (event.getLevel().intLevel() == Level.ERROR.intLevel() && event.getThrown() instanceof Error);
    }

    @Override
    public String resolveKey(LogEvent event) {
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
            if (level.intLevel() <= (Level.ERROR.intLevel())) {
                setSendMultiplier(ERROR_SKIP_MULTIPLIER);
                setStackTraceLines(ERROR_STACK_TRACE_LINES);
            } else if (level.intLevel() <= (Level.WARN.intLevel())) {
                setSendMultiplier(WARN_SKIP_MULTIPLIER);
            } else {
                setSendMultiplier(INFO_SKIP_MULTIPLIER);
            }
        }
    }

    @Override
    public int getIntLevel() {
        return level.intLevel();
    }
}
