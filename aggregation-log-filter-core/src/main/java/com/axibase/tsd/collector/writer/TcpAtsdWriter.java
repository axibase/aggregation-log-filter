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

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/**
 * A client to a ATSD server via TCP.
 */
public class TcpAtsdWriter extends AbstractAtsdWriter {
    private Socket client;
    private DataOutputStream dataOut;
    private WritableByteChannel channel;

    public TcpAtsdWriter(String host, int port) {
        super(host, port);
    }

    @Override
    protected int getDefaultPort() {
        return 8081;
    }

    private void connect() throws IllegalStateException, IOException {
        InetSocketAddress address = getAddress();
        if (address.getAddress() == null) {
            AtsdUtil.logError("Illegal address: " + address);
            throw new UnknownHostException(address.getHostName());
        }
        client = new Socket();
        client.connect(address, 5000);
        client.setSoTimeout(5000);
        dataOut = new DataOutputStream(client.getOutputStream());
        channel = Channels.newChannel(dataOut);
    }

    private boolean isConnected() {
        return (client != null)
                && client.isConnected()
                && !client.isClosed();
    }

    @Override
    public int write(ByteBuffer message) throws IOException {
        int cnt = 0;
        if (!isConnected()) {
            connect();
        }
        try {
            while (message.hasRemaining()) {
                cnt += channel.write(message);
            }
        } catch (IOException e) {
            AtsdUtil.logError("Could not write messages using TCP", e);
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
            channel.close();
            dataOut.close();
            client.close();
        }
    }
}