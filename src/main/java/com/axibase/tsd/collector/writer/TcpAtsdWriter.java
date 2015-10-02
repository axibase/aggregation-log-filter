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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * A client to a ATSD server via TCP.
 */
public class TcpAtsdWriter extends AbstractAtsdWriter {
    private SocketChannel client;

    public TcpAtsdWriter() {
    }

    public void connect() throws IllegalStateException, IOException {
        if (isConnected()) {
            throw new IllegalStateException("Already connected");
        }
        java.net.InetSocketAddress address = getAddress();
        if (address.getAddress() == null) {
            throw new java.net.UnknownHostException(address.getHostName());
        }
        System.out.println("Connecting to: " + getAddress());
        client = SocketChannel.open(address);
    }

    public boolean isConnected() {
        return client != null
                && client.socket().isConnected()
                && !client.socket().isClosed();
    }

    @Override
    public int write(ByteBuffer message) throws IOException {
        int cnt = 0;
        if (!isConnected()) {
            connect();
        }
        try {
            while (message.hasRemaining()) {
                client.write(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
            close();
            throw e;
        }
        return cnt;
    }

    @Override
    public boolean isOpen() {
        return isConnected();
    }

    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }
}