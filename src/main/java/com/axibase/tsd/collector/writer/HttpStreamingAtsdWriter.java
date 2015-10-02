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

import com.axibase.tsd.collector.CountedQueue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Nikolay Malevanny.
 */
public class HttpStreamingAtsdWriter implements WritableByteChannel {
    public static final int DEFAULT_SKIP_DATA_THRESHOLD = 100000;
    public static final int MIN_RECONNECTION_TIME = 30 * 1000;
    private int skipDataThreshold = DEFAULT_SKIP_DATA_THRESHOLD;
    private String url;
    private String username;
    private String password;
    private CountedQueue<ByteBuffer> data = new CountedQueue<ByteBuffer>();
//    private StreamingWorker streamingWorker;
    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private long lastConnectionTryTime = 0;

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

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!isConnected()) {
            connect();
        }

        /*
        if (streamingWorker != null) {
            data.add(src);
            if (data.getCount() > skipDataThreshold) {
                data.poll(); // clean oldest data item
            }
            return src.remaining();
        }
        */
        return 0;
    }

    private void connect() {
        if (System.currentTimeMillis() - lastConnectionTryTime < MIN_RECONNECTION_TIME) {
            // ignore
            return;
        }
        lastConnectionTryTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        /*
        streamingWorker = new StreamingWorker(data, latch, url);
        streamingWorker.setCredentials(username, password);
        singleThreadExecutor.execute(streamingWorker);
        try {
            if (!latch.await(StreamingWorker.TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                streamingWorker.stop();
                streamingWorker = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        */
    }

    @Override
    public boolean isOpen() {
//        return streamingWorker != null && !streamingWorker.isStopped();
        return false;
    }

    @Override
    public void close() throws IOException {
//        if (streamingWorker != null) {
//            streamingWorker.stop();
//        }
//        if (singleThreadExecutor != null) {
//            singleThreadExecutor.shutdown();
//        }
    }

    public boolean isConnected() {
//        return streamingWorker != null && !streamingWorker.isStopped();
        return false;
    }

    /*
    private static class StreamingWorker implements HttpEntity, Runnable {
        public static final int PING_TIMEOUT_MS = 5000;
        public static final int BUFFER_SIZE = 64;
        private static final int TIMEOUT_MS = 5000;
        private volatile boolean stopped = false;
        private CountedQueue<ByteBuffer> data;
        private CountDownLatch latch;
        private String url;
        private BasicHttpClientConnectionManager connectionManager;
        private CloseableHttpClient httpClient;
        private String username;
        private String password;
        private long lastCommandTime = System.currentTimeMillis();

        public StreamingWorker(CountedQueue<ByteBuffer> data, CountDownLatch latch, String url) {
            this.data = data;
            this.latch = latch;
            this.url = url;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public boolean isChunked() {
            return false;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public Header getContentType() {
            return null;
        }

        @Override
        public Header getContentEncoding() {
            return null;
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            return null;
        }

        @Override
        public void writeTo(OutputStream outputStream) throws IOException {
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
        public boolean isStreaming() {
            return true;
        }

        @Override
        public void consumeContent() throws IOException {

        }

        @Override
        public void run() {
            System.out.println("Creating http client to send commands to URL: " + url);
            connectionManager = new BasicHttpClientConnectionManager();
            ConnectionConfig connectionConfig = ConnectionConfig.custom()
                    .setBufferSize(BUFFER_SIZE)
                    .build();
            connectionManager.setConnectionConfig(connectionConfig);
            httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            try {
                checkConfiguration();
                    HttpPost httpPost = createRequest();
                    httpPost.setEntity(new BufferedHttpEntity(this));
                    CloseableHttpResponse response = httpClient.execute(httpPost);
                    StatusLine statusLine = response.getStatusLine();
                    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
                        System.err.println("HTTP: " + statusLine.getStatusCode() + ", " + statusLine.getReasonPhrase());
                    }
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                stop();
            }
        }

        private void checkConfiguration() throws IOException {
            HttpPost httpPost = createRequest();
            httpPost.setEntity(new StringEntity(AtsdUtil.PING_COMMAND));
            CloseableHttpResponse response = httpClient.execute(httpPost);
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
                System.err.println("HTTP: " + statusLine.getStatusCode() + ", " + statusLine.getReasonPhrase());
                throw new IllegalStateException("Could not connect to URL: " + url + ", reason: " + statusLine.getReasonPhrase());
            } else {
                InputStream content = response.getEntity().getContent();
                IOUtils.closeQuietly(content);
            }
        }

        private HttpPost createRequest() {
            HttpPost httpPost = new HttpPost(url);
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(TIMEOUT_MS)
                    .setConnectTimeout(TIMEOUT_MS)
                    .setConnectionRequestTimeout(TIMEOUT_MS)
                    .build();
            httpPost.setConfig(requestConfig);
            if (StringUtils.isNotEmpty(username)) {
                httpPost.setHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary(
                        (username + ":" + password).getBytes()
                ));
            }
            return httpPost;
        }

        public void stop() {
            stopped = true;
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
                connectionManager.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public boolean isStopped() {
            return stopped;
        }

        public void setCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
    */
}
