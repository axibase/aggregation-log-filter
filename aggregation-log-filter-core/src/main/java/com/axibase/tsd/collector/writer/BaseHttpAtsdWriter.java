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
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public abstract class BaseHttpAtsdWriter implements WritableByteChannel {
    public static final String DEFAULT_METHOD = "POST";
    public static final int DEFAULT_CHUNK_SIZE = 1024;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    protected String method = DEFAULT_METHOD;
    protected URI uri;
    protected String credentials;
    protected int timeout = DEFAULT_TIMEOUT_MS;

    public BaseHttpAtsdWriter(URI uri) {
        this.uri = uri.getPath().isEmpty() ? uri.resolve("/api/v1/command") : uri;
        credentials = uri.getUserInfo();
        if (StringUtils.isNotBlank(credentials)) {
            AtsdUtil.logInfo("Connecting to /" + uri.getHost() + ":" + uri.getPort());
        }else{
            AtsdUtil.logError("Credentials are blank.");
        }
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

    public abstract int getStatusCode() throws IOException;

    protected void initConnection(HttpURLConnection con) throws IOException {
        con.setRequestMethod(method);
        if (StringUtils.isNotBlank(credentials)) {
            final String encodedAuthorization = DatatypeConverter.printBase64Binary(urlDecode(credentials).getBytes(AtsdUtil.UTF_8));
            con.setRequestProperty("Authorization", "Basic " + encodedAuthorization);
        }
        con.setRequestProperty("Content-Type", "text/plain; charset=\"UTF-8\"");
        con.setConnectTimeout(timeout);
        con.setReadTimeout(timeout);
        con.setDoOutput(true);
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, AtsdUtil.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected static int writeBuffer(OutputStream outputStream, ByteBuffer buffer) throws IOException {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        outputStream.write(data);
        return data.length;
    }
}
