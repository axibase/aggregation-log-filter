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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/**
 * A client to a ATSD server via TCP.
 */
public class TcpAtsdWriter extends AbstractAtsdWriter {
    private Socket client;
    private DataOutputStream dOut;
    private WritableByteChannel channel;


    public TcpAtsdWriter() {
    }

    public void connect() throws IllegalStateException, IOException {
        if (isConnected()) {
            final String msg = "Already connected";
            AtsdUtil.logInfo(msg);
            throw new IllegalStateException(msg);
        }
        java.net.InetSocketAddress address = getAddress();
        if (address.getAddress() == null) {
            AtsdUtil.logInfo("Illegal address: " + address);
            throw new java.net.UnknownHostException(address.getHostName());
        }
        AtsdUtil.logInfo("Connecting to: " + getAddress());
        client = new Socket(address.getHostName(), address.getPort());
        client.setSoTimeout(5000);
        dOut = new DataOutputStream(client.getOutputStream());
        channel = Channels.newChannel(dOut);

    }

    public boolean isConnected() {
        return client != null
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
            AtsdUtil.logInfo("Could not write messages", e);
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
            dOut.close();
            client.close();
        }
    }
}