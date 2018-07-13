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
import java.net.InetSocketAddress;
import java.nio.channels.WritableByteChannel;

public abstract class AbstractAtsdWriter implements WritableByteChannel {
    private InetSocketAddress address;
    private final String host;
    private final int port;

    public AbstractAtsdWriter(String host, int port) {
        if (host == null) throw new IllegalStateException("Host can not be null.");
        this.host = host;
        this.port = (port > 0) ? port : getDefaultPort();
        AtsdUtil.logInfo("Connecting to " + this.host + ":" + this.port);
    }

    public InetSocketAddress getAddress() {
        if (address == null) {
            address = new InetSocketAddress(host, port);
        }
        return address;
    }

    protected abstract int getDefaultPort();

    @Override
    public String toString() {
        return "AbstractAtsdWriter{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
