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

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;

public class HttpsAtsdWriter extends BaseHttpAtsdWriter {
    private HttpsURLConnection connection;
    private OutputStream outputStream;

    @Override
    public int write(ByteBuffer src) throws IOException {
        close();
        init();
        if (outputStream == null) {
            throw new IOException("outputStream has not been initialized properly");
        }
        return writeBuffer(outputStream, src);
    }

    private void init() throws IOException {
        connection = null;
        outputStream = null;

        try {
            connection = (HttpsURLConnection) new URL(url).openConnection();
            initConnection(connection);
            connection.setChunkedStreamingMode(DEFAULT_CHUNK_SIZE);
            connection.setUseCaches(false);

            outputStream = connection.getOutputStream();

        } catch (Throwable e) {
            AtsdUtil.logInfo("Could not write messages. " + e.getMessage());
            close();
        }
    }

    @Override
    public boolean isOpen() {
        return outputStream != null;
    }

    @Override
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
            if (code != HttpsURLConnection.HTTP_OK) {
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

    @Override
    public int getStatusCode() throws IOException {
        int responseCode = -1;
        if (isOpen()) {
            responseCode = connection.getResponseCode();
        }
        init();
        return responseCode;
    }

    @Override
    public void setUrl(String url) {
        url = url.trim();
        super.setUrl(url);
    }
}
