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
import java.nio.channels.DatagramChannel;

/**
 * A client to a ATSD server via UDP.
 */
public class UdpAtsdWriter extends AbstractAtsdWriter {
    private DatagramChannel datagramChannel = null;

    public UdpAtsdWriter() {
    }

    public void connect() throws IllegalStateException, IOException {
        if (isConnected()) {
            throw new IllegalStateException("Already connected");
        }

        if (datagramChannel != null) {
            datagramChannel.close();
        }

        datagramChannel = DatagramChannel.open();
    }

    public boolean isConnected() {
        return datagramChannel != null && !datagramChannel.socket().isClosed();
    }

    @Override
    public int write(ByteBuffer message) throws IOException {
        try {
            if (!isConnected()) {
                connect();
            }
            return datagramChannel.send(message, getAddress());
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public boolean isOpen() {
        return isConnected();
    }

    @Override
    public void close() throws IOException {

    }
}
