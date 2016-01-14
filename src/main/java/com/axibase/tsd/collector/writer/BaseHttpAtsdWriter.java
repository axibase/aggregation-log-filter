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

import sun.misc.BASE64Encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * @author Nikolay Malevanny.
 */
public abstract class BaseHttpAtsdWriter implements WritableByteChannel {
    public static final String DEFAULT_METHOD = "POST";
    public static final String STREAM_FALSE_PARAM = "stream=false";
    public static final int DEFAULT_CHUNK_SIZE = 1024;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    protected String method = DEFAULT_METHOD;
    protected String url;
    protected String username;
    protected String password;
    protected int timeout = DEFAULT_TIMEOUT_MS;

    public void setUrl(String url) {
        this.url = url;
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
        if (timeout > 0) {
            this.timeout = timeout;
        }
    }

    @Override
    public abstract int write(ByteBuffer src) throws IOException;

    @Override
    public abstract boolean isOpen();

    @Override
    public abstract void close() throws IOException;

    protected void initConnection(HttpURLConnection con) throws IOException {
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

    protected static int writeBuffer(OutputStream outputStream, ByteBuffer buffer) throws IOException {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        System.out.write(data);
        outputStream.write(data);
        return data.length;
    }
}
