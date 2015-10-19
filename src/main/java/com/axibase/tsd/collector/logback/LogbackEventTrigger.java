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

package com.axibase.tsd.collector.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.axibase.tsd.collector.SendMessageTrigger;

/**
 * @author Nikolay Malevanny.
 */
public class LogbackEventTrigger<E extends ILoggingEvent> extends SendMessageTrigger<E>{
    public static final Level DEFAULT_LEVEL = Level.WARN;
    public static final double ERROR_SKIP_MULTIPLIER = 2.0;
    public static final double WARN_SKIP_MULTIPLIER = 2.0;
    public static final double INFO_SKIP_MULTIPLIER = 5.0;
    private Level level = DEFAULT_LEVEL;

    private boolean definedSkipMultiplier = false;

    public LogbackEventTrigger() {
        super();
    }

    public LogbackEventTrigger(int every) {
        super();
        setEvery(every);
    }

    @Override
    public boolean onEvent(E event) {
        return event != null && event.getLevel().isGreaterOrEqual(level) && super.onEvent(event);
    }

    @Override
    public String resolveKey(E event) {
        return event.getLoggerName();
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public void setSkipMultiplier(double skipMultiplier) {
        super.setSkipMultiplier(skipMultiplier);
        definedSkipMultiplier = true;
    }

    @Override
    public void init() {
        if (!definedSkipMultiplier) {
            if (level.levelInt >= Level.ERROR_INT) {
                setSkipMultiplier(ERROR_SKIP_MULTIPLIER);
            } else if (level.levelInt >= Level.WARN_INT) {
                setSkipMultiplier(WARN_SKIP_MULTIPLIER);
            } else {
                setSkipMultiplier(INFO_SKIP_MULTIPLIER);
            }
        }
    }
}
