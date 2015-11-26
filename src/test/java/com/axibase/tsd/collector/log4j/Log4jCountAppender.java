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
