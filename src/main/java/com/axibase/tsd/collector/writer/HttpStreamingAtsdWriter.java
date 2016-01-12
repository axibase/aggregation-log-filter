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

package com.axibase.tsd.collector.writer;

import com.axibase.tsd.collector.AtsdUtil;
import com.axibase.tsd.collector.CountedQueue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Nikolay Malevanny.
 */
public class HttpStreamingAtsdWriter extends BaseHttpAtsdWriter {
    public static final int DEFAULT_SKIP_DATA_THRESHOLD = 100000;
    public static final int MIN_RECONNECTION_TIME = 30 * 1000;
    public static final long DEFAULT_RECONNECT_TIMEOUT = 1 * 60 * 1000L;
    public static final int CLOSE_TIMEOUT_MS = 30000;
    public static final int CLOSE_TIMEOUT_STEPS = 30;
    private int skipDataThreshold = DEFAULT_SKIP_DATA_THRESHOLD;
    private CountedQueue<ByteBuffer> data = new CountedQueue<ByteBuffer>();
    private StreamingWorker streamingWorker;
    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(AtsdUtil.DAEMON_THREAD_FACTORY);
    private long lastConnectionTryTime = 0;
    private long skippedCount = 0;
    private long reconnectTimeoutMs = DEFAULT_RECONNECT_TIMEOUT;

    public void setSkipDataThreshold(int skipDataThreshold) {
        this.skipDataThreshold = skipDataThreshold;
    }

    public void setReconnectTimeoutMs(long reconnectTimeoutMs) {
        if (reconnectTimeoutMs > 0) {
            this.reconnectTimeoutMs = reconnectTimeoutMs;
        }
    }

    protected long getSkippedCount() {
        return skippedCount;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!isConnected()) {
            connect();
        }

        if (streamingWorker != null) {
            data.add(src);
            if (data.getCount() > skipDataThreshold / 2) {
                LockSupport.parkNanos(1);
            } else if (data.getCount() > skipDataThreshold) {
                skippedCount++;
                data.poll(); // clean oldest data item
            }
            return src.remaining();
        }
        return 0;
    }

    private void connect() {
        if (System.currentTimeMillis() - lastConnectionTryTime < MIN_RECONNECTION_TIME) {
            // ignore
            return;
        }
        lastConnectionTryTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        streamingWorker = new StreamingWorker(latch);
        singleThreadExecutor.execute(streamingWorker);
        try {
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                streamingWorker.stop();
                AtsdUtil.logInfo("Connection timeout: " + timeout);
                streamingWorker = null;
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        AtsdUtil.logInfo("Connected to " + url);
    }

    @Override
    public boolean isOpen() {
        return streamingWorker != null && !streamingWorker.isStopped();
    }

    @Override
    public void close() throws IOException {
        if (streamingWorker != null) {
            long st = System.currentTimeMillis();
            while (!data.isEmpty() && System.currentTimeMillis() - st < CLOSE_TIMEOUT_MS) {
                try {
                    AtsdUtil.logInfo("Waiting to close http stream");
                    Thread.sleep(CLOSE_TIMEOUT_MS / CLOSE_TIMEOUT_STEPS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            AtsdUtil.logInfo("Nothing to send, stop writer");
            streamingWorker.stop();
        }
        if (singleThreadExecutor != null) {
            singleThreadExecutor.shutdown();
        }
    }

    public boolean isConnected() {
        return streamingWorker != null && !streamingWorker.isStopped();
    }

    private class StreamingWorker implements Runnable {
        public static final int PING_TIMEOUT_MS = 5000;
        private volatile boolean stopped = false;
        private CountDownLatch latch;
        private long lastCommandTime = System.currentTimeMillis();
        private HttpURLConnection connection;
        private OutputStream outputStream;

        public StreamingWorker(CountDownLatch latch) {
            this.latch = latch;
        }

        private void writeTo(OutputStream outputStream) throws IOException {
            while (!stopped) {
                if (latch.getCount() > 0) {
                    latch.countDown();
                }
                ByteBuffer buffer;
                int cnt = 0;
                while ((buffer = data.poll()) != null && outputStream != null) {
                    cnt++;
                    writeBuffer(outputStream, buffer);
                    if (reconnectTimeoutMs > 0 && System.currentTimeMillis() - lastConnectionTryTime > reconnectTimeoutMs) {
                        outputStream.flush();
                        return;
                    }
                }
                if (cnt > 0) {
                    data.clearCount();
                    outputStream.flush();
                    lastCommandTime = System.currentTimeMillis();
                } else {
                    if (System.currentTimeMillis() - lastCommandTime > PING_TIMEOUT_MS && outputStream != null) {
                        outputStream.write(AtsdUtil.SAFE_PING_COMMAND.getBytes());
                        outputStream.flush();
                        lastCommandTime = System.currentTimeMillis();
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void run() {
            if (!checkConfiguration()) {
                stop();
                return;
            }
            stopped = false;

            connection = null;
            outputStream = null;

            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                initConnection(connection);
                connection.setChunkedStreamingMode(DEFAULT_CHUNK_SIZE);
                connection.setUseCaches(false);

                outputStream = connection.getOutputStream();
                writeTo(outputStream);
            } catch (Throwable e) {
                AtsdUtil.logInfo("Could not write messages", e);
            } finally {
                stop();
            }
        }

        private boolean checkConfiguration() {
            HttpURLConnection testConnection = null;
            try {
                AtsdUtil.logInfo("Connecting to: " + url);
                testConnection = (HttpURLConnection) new URL(url).openConnection();
                initConnection(testConnection);
                OutputStream outputStream = null;
                try {
                    outputStream = testConnection.getOutputStream();
                    outputStream.write(AtsdUtil.PING_COMMAND.getBytes());
                    outputStream.flush();
                } finally {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                }

                int responseCode = testConnection.getResponseCode();
                if ((responseCode == HttpURLConnection.HTTP_OK)) {
                    return true;
                } else {
                    AtsdUtil.logInfo("Could not connect to: " + method + " " + url);
                    AtsdUtil.logInfo("HTTP response code: " + responseCode);
                    return false;
                }
            } catch (SocketTimeoutException e) {
                AtsdUtil.logInfo("Timeout", e);
                return false;
            } catch (Exception e) {
                AtsdUtil.logInfo("Could not connect to: " + method + " " + url, e);
                return false;
            } finally {
                if (testConnection != null) {
                    testConnection.disconnect();
                }
            }
        }

        public void stop() {
            AtsdUtil.logInfo("Stop HTTP worker");
            stopped = true;
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    AtsdUtil.logInfo("Could not close output stream", e);
                }
            }
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    AtsdUtil.logInfo("Could not disconnect", e);
                }
                connection = null;
            }
        }

        public boolean isStopped() {
            return stopped;
        }
    }
}
