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
import sun.misc.BASE64Encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Nikolay Malevanny.
 */
public class HttpStreamingAtsdWriter implements WritableByteChannel {
    public static final int DEFAULT_SKIP_DATA_THRESHOLD = 100000;
    public static final int MIN_RECONNECTION_TIME = 30 * 1000;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    public static final String DEFAULT_METHOD = "POST";
    public static final int DEFAULT_CHUNK_SIZE = 1024;
    private String method = DEFAULT_METHOD;
    private int skipDataThreshold = DEFAULT_SKIP_DATA_THRESHOLD;
    private String url;
    private String username;
    private String password;
    private CountedQueue<ByteBuffer> data = new CountedQueue<ByteBuffer>();
    private StreamingWorker streamingWorker;
    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private long lastConnectionTryTime = 0;
    private int timeout = DEFAULT_TIMEOUT_MS;
    private long skippedCount = 0;

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSkipDataThreshold(int skipDataThreshold) {
        this.skipDataThreshold = skipDataThreshold;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    protected void setMethod(String method) {
        this.method = method;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
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
            if (data.getCount() > skipDataThreshold/2) {
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
                streamingWorker = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isOpen() {
        return streamingWorker != null && !streamingWorker.isStopped();
    }

    @Override
    public void close() throws IOException {
        if (streamingWorker != null) {
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
                while ((buffer = data.poll()) != null) {
                    cnt ++;
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    outputStream.write(data);
                }
                if (cnt > 0) {
                    data.clearCount();
                    outputStream.flush();
                    lastCommandTime = System.currentTimeMillis();
                } else {
                    if (System.currentTimeMillis() - lastCommandTime > PING_TIMEOUT_MS) {
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
            System.out.println("Creating http client to send commands to URL: " + method + " " + url);

            if (!checkConfiguration()) {
                stop();
                return;
            }
            stopped = false;

            connection = null;
            OutputStream outputStream = null;

            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                initConnection(connection);
                connection.setChunkedStreamingMode(DEFAULT_CHUNK_SIZE);
                connection.setUseCaches(false);

                outputStream = connection.getOutputStream();
                writeTo(outputStream);
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private boolean checkConfiguration() {
            HttpURLConnection testConnection = null;
            try {
                testConnection = (HttpURLConnection) new URL(url).openConnection();
                initConnection(testConnection);
                OutputStream outputStream = null;
                try {
                    outputStream = testConnection.getOutputStream();
                    outputStream.write(AtsdUtil.PING_COMMAND.getBytes());
                    outputStream.flush();
                } finally {
                    if (outputStream!= null) {
                        outputStream.close();
                    }
                }
                
                int responseCode = testConnection.getResponseCode();
                if ((responseCode == HttpURLConnection.HTTP_OK)) {
                    return true;
                } else {
                    System.err.println("HTTP response code: " + responseCode);
                    return false;
                }
            } catch (SocketTimeoutException e) {
                System.err.println("Timeout");
                return false;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                if (testConnection != null) {
                    testConnection.disconnect();
                }
            }
        }

        private void initConnection(HttpURLConnection con) throws IOException {
            con.setRequestMethod(method);
            BASE64Encoder enc = new BASE64Encoder();
            if (username != null && username.trim().length() > 0) {
                String encodedAuthorization = enc.encode((username + ":" + password).getBytes());
                con.setRequestProperty("Authorization",
                        "Basic " + encodedAuthorization);
            }
            con.setRequestProperty("Content-Type",
                    "text/plain; charset=\"UTF-8\"");
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setDoOutput(true);
        }

        public void stop() {
            stopped = true;
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public boolean isStopped() {
            return stopped;
        }
    }
}
