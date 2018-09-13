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

import com.axibase.tsd.collector.config.SeriesSenderConfig;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Aggregator<E, K, L> {
    private final Worker worker = new Worker();
    private final ConcurrentMap<K, SyncEventCounter<E, L>> total =
            new ConcurrentHashMap<K, SyncEventCounter<E, L>>();
    private CountedQueue<EventWrapper<E>> singles = new CountedQueue<>();
    private AtomicLong totalCounter = new AtomicLong(0);
    private WritableByteChannel writer;
    private final MessageWriter<E, K, L> messageWriter;
    private final EventProcessor<E, K, L> eventProcessor;
    private ExecutorService senderExecutor;
    private SendMessageTrigger[] triggers = null;
    private SeriesSenderConfig seriesSenderConfig = SeriesSenderConfig.DEFAULT;

    private WorkerFinisher workerFinisher = new WorkerFinisher();

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

            // try to send immediately instance of Error
            if (!messageWriter.sendErrorInstance(writer, event) && (triggers != null)) {
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
        singles.add(messageWriter.createWrapper(event, lines));
        if (singles.getCount() > seriesSenderConfig.getMessageSkipThreshold()) {
            singles.poll();
        }
    }

    public void start() {
        senderExecutor = Executors.newSingleThreadExecutor(AtsdUtil.DAEMON_THREAD_FACTORY);
        senderExecutor.execute(worker);

        Runtime.getRuntime().addShutdownHook(workerFinisher);
    }

    public void stop() {
        try {
            worker.finish();
        } catch (Exception e) {
            AtsdUtil.logInfo("Could not finish worker. " + e.getMessage());
        }
        worker.stop();
        if (senderExecutor != null && !senderExecutor.isShutdown()) {
            senderExecutor.shutdown();
        }
        closeWriter();
        Runtime.getRuntime().removeShutdownHook(workerFinisher);
    }

    private void closeWriter() {
        writeSingles();
        if (writer != null && writer.isOpen()) {
            AtsdUtil.logInfo("Close writer");
            try {
                writer.close();
            } catch (IOException e) {
                AtsdUtil.logInfo("Could not close writer. "  + e.getMessage());
            }
        } else {
            AtsdUtil.logInfo("Writer has already been closed");
        }
    }

    private void writeSingles() {
        if (!singles.isEmpty()) {
            try {
                messageWriter.writeSingles(writer, singles);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void setWriter(WritableByteChannel writer) {
        this.writer = writer;
    }

    public void addSendMessageTrigger(SendMessageTrigger<E> messageTrigger) {
        messageTrigger.init();
        if (triggers == null) {
            triggers = new SendMessageTrigger[]{messageTrigger};
        } else {
            Map<Integer, SendMessageTrigger> triggerMap = new HashMap<Integer, SendMessageTrigger>();
            for (SendMessageTrigger trigger : triggers) {
                int intLevel = trigger.getIntLevel();
                triggerMap.put(intLevel, trigger);
            }
            triggerMap.put(messageTrigger.getIntLevel(), messageTrigger);
            triggers = triggerMap.values().toArray(new SendMessageTrigger[0]);
        }
    }

    public void setSeriesSenderConfig(SeriesSenderConfig seriesSenderConfig) {
        this.seriesSenderConfig = seriesSenderConfig;
    }

    private class Worker implements Runnable {
        private final Map<K, EventCounter<L>> lastTotal = new HashMap<K, EventCounter<L>>();
        private long lastTotalCounter = 0;
        private long last = System.currentTimeMillis();

        private volatile boolean stopped;

        @Override
        public void run() {
            while (!stopped) {
                try {
                    Thread.sleep(seriesSenderConfig.getCheckIntervalMs());
                    messageWriter.checkPropertiesSent(writer);
                    checkThresholdsAndWrite();
                } catch (IOException e) {
                    AtsdUtil.logInfo("Could not write messages before finish. " + e.getMessage());
                    // ignore
                } catch (InterruptedException e) {
                    AtsdUtil.logInfo("Interrupted. " + e.getMessage());
                    // ignore
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void checkThresholdsAndWrite() throws IOException {
            final long total = totalCounter.get();
            long cnt = total - lastTotalCounter;
            long currentTime = System.currentTimeMillis();
            long dt = currentTime - last;
            long intervalMs = seriesSenderConfig.getIntervalMs();
            if (dt > intervalMs) {
                flush(last, currentTime);
                cnt = 0;
                lastTotalCounter = total;
            }

            int minIntervalThreshold = seriesSenderConfig.getMinIntervalThreshold();
            if (minIntervalThreshold > 0 && dt > seriesSenderConfig.getMinIntervalMs() && cnt > minIntervalThreshold) {
                flush(last, currentTime);
                lastTotalCounter = total;
            }
            writeSingles();
        }

        protected void flush(long lastTime, long currentTime) throws IOException {
            last = currentTime;

            Map<K, EventCounter<L>> diff = new HashMap<K, EventCounter<L>>();

            for (Map.Entry<K, SyncEventCounter<E, L>> keyAndCounter : total.entrySet()) {
                K key = keyAndCounter.getKey();
                SyncEventCounter<E, L> currentCount = keyAndCounter.getValue();
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

        public void finish() throws IOException {
            flush(last, System.currentTimeMillis());
            closeWriter();
        }
    }

    private class WorkerFinisher extends Thread {
        @Override
        public void run() {
            try {
                worker.finish();
            } catch (Exception e) {
                AtsdUtil.logInfo("Could not finish worker. " + e.getMessage());
            }
        }
    }
}
