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

package com.axibase.tsd.collector;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.logback.CountAppender;
import com.axibase.tsd.collector.logback.LogbackEventProcessor;
import com.axibase.tsd.collector.logback.LogbackEventTrigger;
import com.axibase.tsd.collector.logback.LogbackMessageWriter;
import com.axibase.tsd.collector.writer.UdpAtsdWriter;
import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Exception;import java.lang.InterruptedException;import java.lang.Override;import java.lang.Runnable;import java.lang.String;import java.lang.System;import java.lang.Thread;import java.lang.Throwable;import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ConcurrentHashMap;import java.util.concurrent.ConcurrentMap;import java.util.concurrent.CountDownLatch;import java.util.concurrent.ExecutorService;import java.util.concurrent.Executors;import java.util.concurrent.TimeUnit;import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Nikolay Malevanny.
 */
public class AggregatorTest extends TestCase {
    private static final Logger log = LoggerFactory.getLogger(AggregatorTest.class);
    private WritableByteChannel mockWriter;

    @Override
    public void setUp() throws Exception {
        CountAppender.clear();
        mockWriter = mock(WritableByteChannel.class);
    }

    @Test
    public void testThresholds() throws Exception {
        int cnt = 15;

        SeriesSenderConfig seriesSenderConfig = new SeriesSenderConfig(0, 1, 10);
        seriesSenderConfig.setMinPeriodSeconds(0);
        LogbackMessageWriter messageWriter = new LogbackMessageWriter();
        messageWriter.setSeriesSenderConfig(seriesSenderConfig);
        messageWriter.start();
        Aggregator aggregator = new Aggregator(messageWriter, new LogbackEventProcessor());
        aggregator.setWriter(mockWriter);
        aggregator.setSeriesSenderConfig(seriesSenderConfig);
        aggregator.addSendMessageTrigger(new LogbackEventTrigger(7));
        aggregator.start();
        LoggingEvent event = TestUtils.createLoggingEvent(Level.WARN, "logger", "test-msg", "test-thread");
        for (int i = 0; i < cnt; i++) {
            assertTrue(aggregator.register(event));
        }
        Thread.sleep(750);
        assertTrue(aggregator.register(event));
        Thread.sleep(1000);

        verify(mockWriter, times(28)).write(any(ByteBuffer.class));
    }

    @Ignore
    @Test
    public void loadTest() throws Exception {
        final int cnt = 1000000;
        int threadCount = 20;

        long st = System.currentTimeMillis();

        SeriesSenderConfig seriesSenderConfig = new SeriesSenderConfig(0, 1, 10);
        seriesSenderConfig.setMinPeriodSeconds(0);
        seriesSenderConfig.setMessageSkipThreshold(1000);
        LogbackMessageWriter messageWriter = new LogbackMessageWriter();
        messageWriter.setSeriesSenderConfig(seriesSenderConfig);
        messageWriter.start();
        final Aggregator aggregator = new Aggregator(messageWriter, new LogbackEventProcessor());
        UdpAtsdWriter writer = new UdpAtsdWriter();
        writer.setHost("localhost");
        writer.setPort(55555);
        aggregator.setWriter(writer);
        aggregator.setSeriesSenderConfig(seriesSenderConfig);
        aggregator.addSendMessageTrigger(new LogbackEventTrigger(1));
        aggregator.start();

        final LoggingEvent event = TestUtils.createLoggingEvent(Level.WARN, "logger", "test-msg", "test-thread");

        final CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            executorService.execute(new java.lang.Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < cnt; i++) {
                        try {
                            aggregator.register(event);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30000, TimeUnit.MILLISECONDS));

        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }

    @Ignore
    @Test
    public void loadCHMvsCLQ() throws Exception {
        final int cnt = 100000;
        int threads = 11;
        final int loggersSize = 1000;
        final String[] loggers = new String[loggersSize];
        for (int i = 0; i < loggers.length; i++) {
            loggers[i] = "logger_" + i;
        }

//        final Receiver receiver = new CHMReceiver();
        final Receiver receiver = new CLQReceiver();

        long st = System.currentTimeMillis();

        execute(cnt, threads, loggersSize, loggers, receiver);

        System.out.println("receiver (" + receiver.getClass().getName() +
                ") total = " + receiver.getTotal());
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }

    private void execute(final int cnt,
                         int threads,
                         final int loggersSize,
                         final String[] loggers,
                         final Receiver receiver) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < cnt; i++) {
                        LoggingEvent event = TestUtils.createLoggingEvent(Level.WARN, loggers[i % loggers.length], "test",
                                Thread.currentThread().getName());
                        receiver.onEvent(event);
                    }
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30000, TimeUnit.MILLISECONDS));
    }

    private static interface Receiver {
        void onEvent(ILoggingEvent event);

        long getTotal();
    }

    private static class CLQReceiver implements Receiver {
        private volatile AtomicReference<RedirCountedQueue<ILoggingEvent>> queueRef =
                new AtomicReference<RedirCountedQueue<ILoggingEvent>>(new RedirCountedQueue<ILoggingEvent>());
        private long cnt = 0;

        public CLQReceiver() {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        RedirCountedQueue<ILoggingEvent> queue = queueRef.get();
                        if (queue.getCount() > 10000) {
                            RedirCountedQueue<ILoggingEvent> newQueue = new RedirCountedQueue<ILoggingEvent>();
                            if (queueRef.compareAndSet(queue, newQueue)) {
                                queue.next = newQueue;
                                cnt += queue.size();
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void onEvent(ILoggingEvent event) {
            CountedQueue<ILoggingEvent> iLoggingEvents = queueRef.get();
            iLoggingEvents.add(event);
        }

        @Override
        public long getTotal() {
            return cnt + queueRef.get().size();
        }
    }

    private static class CHMReceiver implements Receiver {
        private ConcurrentMap<String, AtomicLong> data = new ConcurrentHashMap<String, AtomicLong>();

        @Override
        public void onEvent(ILoggingEvent event) {
            String loggerName = event.getLoggerName();
            AtomicLong count = data.get(loggerName);
            if (count == null) {
                count = new AtomicLong(0);
                AtomicLong old = data.putIfAbsent(loggerName, count);
                count = old == null?count:old;
            }
            count.incrementAndGet();
        }

        @Override
        public long getTotal() {
            long l = 0;
            for (AtomicLong atomicLong : data.values()) {
                l += atomicLong.get();
            }
            return l;
        }
    }

    private static class RedirCountedQueue<E> extends CountedQueue<E> {
        private volatile RedirCountedQueue<E> next;

        public void setNext(RedirCountedQueue<E> next) {
            this.next = next;
        }

        @Override
        public boolean offer(E e) {
            if (next == null) {
                return super.offer(e);
            } else {
                return next.offer(e);
            }
        }
    }
}