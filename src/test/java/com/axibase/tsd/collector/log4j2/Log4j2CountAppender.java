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


import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

@Plugin(name = "Counter", category = "Core", elementType = "appender", printObject = false)
public class Log4j2CountAppender extends AbstractAppender {

    private static final AtomicInteger counter = new AtomicInteger();

    protected Log4j2CountAppender(String name,
                                  Filter filter,
                                  Layout<? extends Serializable> layout) {
        super(name, filter, layout);
    }

    @PluginFactory
    public static Log4j2CountAppender createAppender(
            @PluginAttribute("name") final String name,
            @PluginElement("Filter") final Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout
            ) {
        return new Log4j2CountAppender(name, filter, layout);
    }

    protected Log4j2CountAppender(String name,
                                  Filter filter,
                                  Layout<? extends Serializable> layout, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
    }

    @Override
    public void append(LogEvent event) {
        counter.incrementAndGet();
    }

    public static int getCount() {
        return counter.get();
    }

    public static void clear() {
        counter.set(0);
    }
}
