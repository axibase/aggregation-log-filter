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
    private Level level = DEFAULT_LEVEL;

    public LogbackEventTrigger() {
    }

    public LogbackEventTrigger(int every) {
        super();
        setEvery(every);
    }

    @Override
    public boolean onEvent(E event) {
        return event != null && event.getLevel().isGreaterOrEqual(level) && super.onEvent(event);
    }

    public void setLevel(Level level) {
        this.level = level;
    }
}
