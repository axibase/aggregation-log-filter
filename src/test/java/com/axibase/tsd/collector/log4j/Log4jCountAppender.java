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

package com.axibase.tsd.collector.log4j;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Nikolay Malevanny.
 */
public class Log4jCountAppender extends AppenderSkeleton {
    private static final AtomicInteger counter = new AtomicInteger();

    @Override
    protected void append(LoggingEvent event) {
        counter.incrementAndGet();
    }

    @Override
    public void close() {

    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    public static int getCount() {
        return counter.get();
    }

    public static void clear() {
        counter.set(0);
    }

}
