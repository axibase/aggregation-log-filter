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

import javax.net.SocketFactory;
import java.net.InetSocketAddress;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

public abstract class AbstractAtsdWriter implements WritableByteChannel {
    protected final SocketFactory socketFactory = SocketFactory.getDefault();
    protected static final Charset charset= AtsdUtil.UTF_8;
    private InetSocketAddress address;
    private String host;
    private int port;

    public InetSocketAddress getAddress() {
        if (address == null) {
            address = new InetSocketAddress(host, port);
        }
        return address;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "AbstractAtsdSender{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
