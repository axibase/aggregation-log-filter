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


import com.axibase.tsd.collector.config.SeriesSenderConfig;

import java.io.IOException;
import java.lang.Integer;import java.lang.InterruptedException;import java.lang.Runnable;import java.lang.System;import java.lang.Thread;import java.lang.Throwable;import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nikolay Malevanny.
 */
public class Aggregator<E, K, L> {
    private final Worker worker = new Worker();
    private final ConcurrentMap<K, SyncEventCounter<E, L>> total =
            new ConcurrentHashMap<K, SyncEventCounter<E, L>>();
    private CountedQueue<EventWrapper<E>> singles = new CountedQueue<EventWrapper<E>>();
    private AtomicLong totalCounter = new AtomicLong(0);
    private WritableByteChannel writer;
    private final MessageWriter<E, K, L> messageWriter;
    private final EventProcessor<E, K, L> eventProcessor;
    private ExecutorService senderExecutor;
    private SendMessageTrigger[] triggers = null;
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;

    private int skippedCount = 0;

    public Aggregator(MessageWriter<E, K, L> messageWriter, EventProcessor<E, K, L> eventProcessor) {
        this.messageWriter = messageWriter;
        this.eventProcessor = eventProcessor;
    }

    public boolean register(E event) throws IOException {
        try {
            K key = eventProcessor.extractKey(event);
            SyncEventCounter<E, L> counter = total.get(key);
            if (counter == null) {
                counter = eventProcessor.createSyncCounter();
                SyncEventCounter<E, L> old = total.putIfAbsent(key, counter);
                counter = old == null ? counter : old;
            }
            counter.increment(event);

            totalCounter.incrementAndGet();

            if (triggers != null) {
                int lines = 0;
                boolean fire = false;
                for (SendMessageTrigger<E> trigger : triggers) {
                    if (trigger.onEvent(event)) {
                        fire = true;
                        int stackTraceLines = trigger.getStackTraceLines();
                        if (stackTraceLines < 0) {
                            lines = Integer.MAX_VALUE;
                        } else if (stackTraceLines > lines) {
                            lines = stackTraceLines;
                        }
                    }
                }
                if (fire) {
                    sendSingle(event, lines);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new IOException(t);
        }
        return true;
    }

    private void sendSingle(final E event, final int lines) throws IOException {
        singles.add(new EventWrapper<E>(event, lines));
        if (singles.getCount() > seriesSenderConfig.getMessageSkipThreshold()) {
            singles.poll();
        }
    }

    public void start() {
        senderExecutor = Executors.newSingleThreadExecutor(AtsdUtil.DAEMON_THREAD_FACTORY);
        senderExecutor.execute(worker);
    }

    public void stop() {
        if (worker != null) {
            worker.stop();
        }
        if (senderExecutor != null && !senderExecutor.isShutdown()) {
            senderExecutor.shutdown();
        }
        if (writer != null && writer.isOpen()) {
            try {
                writer.close();
            } catch (IOException e) {
                AtsdUtil.logError("Could not close writer", e);
            }
        }
    }

    public void setWriter(WritableByteChannel writer) {
        this.writer = writer;
    }

    public void addSendMessageTrigger(SendMessageTrigger<E> messageTrigger) {
        messageTrigger.init();
        if (triggers == null) {
            triggers = new SendMessageTrigger[] {messageTrigger};
        } else {
            int l = triggers.length;
            triggers = Arrays.copyOf(triggers, l + 1);
            triggers[l] = messageTrigger;
        }
    }

    public void setSeriesSenderConfig(SeriesSenderConfig seriesSenderConfig) {
        this.seriesSenderConfig = seriesSenderConfig;
    }

    private class Worker implements Runnable {
        private final Map<K, EventCounter<L>> lastTotal = new HashMap<K, EventCounter<L>>();
        private long lastTotalCounter = 0;
        private long last = System.currentTimeMillis();

        private volatile boolean stopped = false;

        @java.lang.Override
        public void run() {
            while (!stopped) {
                try {
                    Thread.sleep(seriesSenderConfig.getCheckIntervalMs());
                    checkThresholdsAndWrite();
                } catch (IOException e) {
                    AtsdUtil.logError("Could not write messages", e);
                    // ignore
                } catch (InterruptedException e) {
                    AtsdUtil.logError("Interrupted", e);
                    // ignore
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void checkThresholdsAndWrite() throws IOException {
            long cnt = totalCounter.get() - lastTotalCounter;
            long currentTime = System.currentTimeMillis();
            long dt = currentTime - last;
            long intervalMs = seriesSenderConfig.getIntervalMs();
            if (dt > intervalMs) {
                flush(last, currentTime);
                cnt = 0;
            }

            int minIntervalThreshold = seriesSenderConfig.getMinIntervalThreshold();
            if (minIntervalThreshold > 0 && dt > seriesSenderConfig.getMinIntervalMs() && cnt > minIntervalThreshold) {
                flush(last, currentTime);
            }

            if (!singles.isEmpty()) {
                messageWriter.writeSingles(writer, singles);
            }
        }

        protected void flush(long lastTime, long currentTime) throws IOException {
            last = currentTime;

            Map<K, EventCounter<L>> diff = new HashMap<K, EventCounter<L>>();

            for (Map.Entry<K, SyncEventCounter<E, L>> kcEntry : total.entrySet()) {
                K key = kcEntry.getKey();
                SyncEventCounter<E, L> currentCount = kcEntry.getValue();
                EventCounter<L> lastCount = lastTotal.get(key);
                if (lastCount == null) {
                    lastCount = eventProcessor.createCounter();
                    lastTotal.put(key, lastCount);
                }
                EventCounter<L> diffCount = currentCount.updateAndCreateDiff(lastCount);
                if (diffCount != null) {
                    diff.put(key, diffCount);
                }
            }

            messageWriter.writeStatMessages(writer, diff, (1 + currentTime - lastTime));
        }

        public void stop() {
            stopped = true;
        }
    }
}
