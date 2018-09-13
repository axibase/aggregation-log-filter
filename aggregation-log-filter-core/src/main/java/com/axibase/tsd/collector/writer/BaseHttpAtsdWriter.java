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

package com.axibase.tsd.collector.writer;

import com.axibase.tsd.collector.AtsdUtil;
import org.apache.commons.lang3.StringUtils;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public abstract class BaseHttpAtsdWriter implements WritableByteChannel {
    private static final String DEFAULT_METHOD = "POST";
    private static final int DEFAULT_CHUNK_SIZE = 1024;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private String method = DEFAULT_METHOD;
    protected URI uri;
    protected String credentials;
    private int timeout = DEFAULT_TIMEOUT_MS;
    protected HttpURLConnection connection;
    protected OutputStream outputStream;

    BaseHttpAtsdWriter(URI uri) {
        this.uri = uri.getPath().isEmpty() ? uri.resolve("/api/v1/command") : uri;
        credentials = uri.getUserInfo();
        if (StringUtils.isNotBlank(credentials)) {
            AtsdUtil.logInfo("Connecting to " + uri.getHost() + ":" + uri.getPort());
        } else {
            throw new IllegalStateException("Credentials cannot be empty");
        }
    }

    void setTimeout(int timeout) {
        if (timeout > 0) {
            this.timeout = timeout;
        }
    }

    public int write(ByteBuffer src) throws IOException {
        if (!isOpen()) {
            init();
        }
        return writeBuffer(outputStream, src);
    }

    protected abstract void init() throws IOException;

    public boolean isOpen() {
        return (outputStream != null) && (connection != null);
    }

    public void close() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
            try {
                outputStream.close();
            } catch (IOException e) {
                AtsdUtil.logInfo("Could not close output stream. " + e.getMessage());
            }
            outputStream = null;
        }
        if (connection != null) {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Illegal response code: " + code);
            }
            try {
                connection.disconnect();
            } catch (Exception e) {
                AtsdUtil.logInfo("Could not disconnect. " + e.getMessage());
            }
            connection = null;
        }
    }

    public int getStatusCode() throws IOException {
        int responseCode = -1;
        if (isOpen()) {
            responseCode = connection.getResponseCode();
        }
        return responseCode;
    }


    void initConnection() throws IOException {
        connection.setRequestMethod(method);
        final String encodedAuthorization = DatatypeConverter.printBase64Binary(urlDecode(credentials).getBytes(AtsdUtil.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encodedAuthorization);
        connection.setRequestProperty("Content-Type", "text/plain; charset=\"UTF-8\"");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(DEFAULT_CHUNK_SIZE);
        connection.setUseCaches(false);
        outputStream = connection.getOutputStream();
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, AtsdUtil.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private int writeBuffer(OutputStream outputStream, ByteBuffer buffer) throws IOException {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
//        try {
            outputStream.write(data);
//        } catch (IOException e) {
//            if (e.getMessage().equals("Stream is closed")) {
//                close();
//                init();
//                outputStream.write(data);
//            } else {
//                throw e;
//            }
//        }
        return data.length;
    }
}
