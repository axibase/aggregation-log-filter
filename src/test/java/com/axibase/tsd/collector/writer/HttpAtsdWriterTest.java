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

import com.axibase.tsd.collector.*;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class HttpAtsdWriterTest {
    public static final long M = 1000 * 1000L;

    @Test
    public void testBadAddress() throws Exception {
        HttpAtsdWriter writer = new HttpAtsdWriter();
        try {
            writer.setUrl("http:/localhost:0");
            writer.setTimeout(300);
            writer.write(ByteBuffer.wrap("test\n".getBytes(AtsdUtil.UTF_8)));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("protocol = http host = null", e.getMessage());
            // ok
        }
    }

    @Ignore
    @Test
    public void ping() throws Exception {
        long st = System.currentTimeMillis();
        HttpAtsdWriter writer = new HttpAtsdWriter();
        writer.setUrl("http://localhost:8088/api/v1/command/");
        writer.setUsername("axibase");
        writer.setPassword("11111");
        writer.setTimeout(1000);
        for (long i = 0; i < 1E7; i++) {
            final String cmd = "ping\n";
            final int cnt = writer.write(ByteBuffer.wrap(cmd.getBytes(AtsdUtil.UTF_8)));
            assertEquals(cmd.length(), cnt);
        }
        writer.close();
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }

    @Ignore
    @Test
    public void uploadMetrics() throws Exception {
        long st = System.currentTimeMillis();
        HttpAtsdWriter writer = new HttpAtsdWriter();
        writer.setUrl("http://localhost:8088/api/v1/command/");
        writer.setUsername("axibase");
        writer.setPassword("11111");
        writer.setTimeout(30000);
        long cnt = M/10;
        for (long i = 0; i < cnt; i++) {
            ByteBuffer data = ByteBuffer.wrap(("series e:test_entity ms:" + (st - (cnt - i) * 1000) +
                    " m:t_metric=" + i +
                    "\n").getBytes(AtsdUtil.UTF_8));
            writer.write(data);
        }
        writer.close();
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }

    @Ignore
    @Test
    public void uploadMetricsSsl() throws Exception {
        long st = System.currentTimeMillis();
        HttpAtsdWriter writer = new HttpAtsdWriter();
        writer.setUrl("https://localhost:8443/api/v1/command/");
        writer.setUsername("axibase");
        writer.setPassword("11111");
        writer.setTimeout(10000);
        long cnt = M/10;
        for (long i = 0; i < cnt; i++) {
            ByteBuffer data = ByteBuffer.wrap(("series e:test_entity ms:" + (st - (cnt - i) * 1000) +
                    " m:sec_metric=" + i +
                    "\n").getBytes(AtsdUtil.UTF_8));
            writer.write(data);
            Thread.sleep(100);
        }
        writer.close();
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }
}