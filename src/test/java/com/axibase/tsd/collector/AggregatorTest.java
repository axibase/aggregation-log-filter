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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.axibase.tsd.collector.config.SeriesSenderConfig;
import com.axibase.tsd.collector.config.TotalCountInit;
import com.axibase.tsd.collector.logback.*;
import com.axibase.tsd.collector.writer.UdpAtsdWriter;
import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class AggregatorTest extends TestCase {
    private MockWritableByteChannel mockWriter;

    @Override
    public void setUp() throws Exception {
        CountAppender.clear();
        mockWriter = new MockWritableByteChannel();
    }

    public void testThresholds() throws Exception {
        int cnt = 15;

        SeriesSenderConfig seriesSenderConfig = new SeriesSenderConfig(0, 1, 10);
        seriesSenderConfig.setTotalCountInit(new TotalCountInit("INFO", -1));
        seriesSenderConfig.setTotalCountInit(new TotalCountInit("WARN", -1));
        seriesSenderConfig.setTotalCountInit(new TotalCountInit("ERROR", -1));
        seriesSenderConfig.setMinIntervalSeconds(0);
        LogbackWriter messageWriter = new LogbackWriter();
        messageWriter.setSeriesSenderConfig(seriesSenderConfig);
        messageWriter.start(mockWriter,Level.WARN_INT,60,new HashMap<String, String>());
        Aggregator aggregator = new Aggregator(messageWriter, new LogbackEventProcessor());
        aggregator.setWriter(mockWriter);
        aggregator.setSeriesSenderConfig(seriesSenderConfig);
        aggregator.addSendMessageTrigger(new LogbackEventTrigger());
        aggregator.start();
        LoggingEvent event = LogbackUtils.createLoggingEvent(Level.WARN, "logger", "test-msg", "test-thread");
        System.out.println(timePrefix() + "START");
        for (int i = 0; i < cnt; i++) {
            assertTrue(aggregator.register(event));
        }
        System.out.println(timePrefix() + "AFTER 15 EVENTS");
        Thread.sleep(750);
        System.out.println(timePrefix() + "+750MS");
        assertTrue(aggregator.register(event));
        System.out.println(timePrefix() + "+16 EVENT");
        Thread.sleep(1001);
        System.out.println(timePrefix() + "+1000MS");

        // 2 -- series fired by cnt (counter and total counter)
        // 3 -- warn message because of default warning multiplier is 3, i.e we got 1,3,9 messages
        // 2 -- series fired by time (counter and total counter)
        // 3 -- initial properties
        // 4 -- initial total zeros
        assertEquals(14, mockWriter.cnt);
    }

    @Test
    public void loadTest() throws Exception {
        final int cnt = 1000000;
        int threadCount = 20;

        long st = System.currentTimeMillis();

        SeriesSenderConfig seriesSenderConfig = new SeriesSenderConfig(0, 1, 10);
        seriesSenderConfig.setMinIntervalSeconds(0);
        seriesSenderConfig.setMessageSkipThreshold(1000);
        LogbackWriter messageWriter = new LogbackWriter();
        messageWriter.setSeriesSenderConfig(seriesSenderConfig);
        UdpAtsdWriter writer = new UdpAtsdWriter();
        writer.setHost("localhost");
        writer.setPort(55555);
        messageWriter.start(writer, Level.TRACE_INT, 60, new HashMap<String, String>());
        final Aggregator aggregator = new Aggregator(messageWriter, new LogbackEventProcessor());
        aggregator.setWriter(writer);
        aggregator.setSeriesSenderConfig(seriesSenderConfig);
        aggregator.addSendMessageTrigger(new LogbackEventTrigger());
        aggregator.start();

        final LoggingEvent event = LogbackUtils.createLoggingEvent(Level.WARN, "logger", "test-msg", "test-thread");

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
                        LoggingEvent event = LogbackUtils.createLoggingEvent(Level.WARN, loggers[i % loggers.length],
                                "test",
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
                count = old == null ? count : old;
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

    private static class MockWritableByteChannel implements WritableByteChannel {
        private int cnt;
        @Override
        public int write(ByteBuffer src) throws IOException {
            cnt++;
            byte[] data = new byte[src.remaining()];
            src.duplicate().get(data);
            System.out.printf("%s%s%n", timePrefix(), new String(data, StandardCharsets.UTF_8));
            return 0;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() throws IOException {

        }
    }

    private static String timePrefix() {
        return "[" + System.currentTimeMillis() + ":" + new Date() +
                "] data = ";
    }
}
