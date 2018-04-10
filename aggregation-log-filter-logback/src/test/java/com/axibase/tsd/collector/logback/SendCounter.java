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

package com.axibase.tsd.collector.logback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class SendCounter implements WritableByteChannel {
    private static AtomicInteger count = new AtomicInteger();

    public static long getCount() {
        return count.get();
    }

    public static void clear() {
        System.out.println("SendCounter.clear");
        count.set(0);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        byte[] array = new byte[src.remaining()];
        src.duplicate().get(array);
        System.out.printf("count:%s%n", new String(array, StandardCharsets.UTF_8));
        count.incrementAndGet();
        return 1;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }
}
