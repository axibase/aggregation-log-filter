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

import com.axibase.tsd.collector.TcpReceiver;
import com.axibase.tsd.collector.TestUtils;
import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Ignore("needed  configuration with CountAppender")
public class CollectorTest extends TestCase {
    private static final Logger log = LoggerFactory.getLogger(CollectorTest.class);
    private static final Logger tcpSendLog = LoggerFactory.getLogger("test.tcp.write");
    private static final Logger udpSendLog = LoggerFactory.getLogger("test.udp.write");
    private static final Logger httpSendLog = LoggerFactory.getLogger("test.http.write");

    @Override
    public void setUp() throws Exception {
    }

    @Test
    public void testDecide() throws Exception {
        CountAppender.clear();
        SendCounter.clear();
        // 15 > <minIntervalThreshold>10</minIntervalThreshold>
        // 15 > <sendEvery>7</sendEvery>
        for (int i = 0; i < 15; i++) {
            log.error("test {}", i);
        }
        for (int i = 0; i < 15; i++) {
            log.info("test {}", i);
        }
        Thread.sleep(700);
        assertEquals(30, CountAppender.getCount());
        // 3 -- message
        // 3 -- series
        assertEquals(6, SendCounter.getCount());
    }

    @Test
    public void testTcpSend() throws Exception {
        CountAppender.clear();
        TcpReceiver tcpReceiver = new TcpReceiver(TestUtils.TEST_TCP_PORT_LOGBACK);
        try {
            tcpReceiver.start();

            // 15 > <minIntervalThreshold>10</minIntervalThreshold>
            // 15 > <sendEvery>7</sendEvery>
            // <level>WARN</level>
            for (int i = 0; i < 15; i++) {
                MDC.put("myKey", "key-" + i);
                tcpSendLog.warn("test {}", i);
                MDC.remove("myKey");
            }
            for (int i = 0; i < 15; i++) {
                tcpSendLog.debug("test {}", i);
            }
            // debug events are not filtered
            Thread.sleep(1100);
            String result = tcpReceiver.getSb().toString();
            System.out.println("tcp result = " + result);
            assertEquals(30, CountAppender.getCount());
            // check message content
            assertTrue(result.contains("t:ttt2=\"k=1;k2=2;k3=3\""));
            assertTrue(result.contains("test.tcp.write - test 0 [key-0]\""));
            assertFalse(result.contains("test.tcp.write - test 4 [key-4]\""));
            assertTrue(result.contains("test.tcp.write - test 5 [key-5]\""));
            assertTrue(result.contains("test.tcp.write - test 7 [key-7]\""));
            assertTrue(result.contains("t:level=WARN"));
            assertFalse(result.contains("m:log_event_sum_counter=0 t:level=INFO"));
            assertTrue(result.contains("m:log_event_sum_counter=0 t:level=TRACE"));
            assertTrue(result.contains("test.tcp.write"));
            assertFalse(result.contains("t:level=DEBUG"));
            // check series content
            assertTrue(result.contains("m:log_event_counter="));
//            assertTrue(result.contains("m:log_event_sum_rate="));
            assertTrue(result.contains("m:log_event_sum_counter="));
        } finally {
            tcpReceiver.stop();
        }
    }

    @Test
    public void testUdpSend() throws Exception {
        CountAppender.clear();
        UdpReceiver udpReceiver = new UdpReceiver();
        try {
            udpReceiver.start();

            // 15 > <minIntervalThreshold>10</minIntervalThreshold>
            // 15 > <sendEvery>7</sendEvery>
            // <level>WARN</level>
            for (int i = 0; i < 15; i++) {
                try {
                    try {
                        throw new NullPointerException("null");
                    } catch (NullPointerException e) {
                        throw new RuntimeException("test", e);
                    }
                } catch (Exception e) {
                    udpSendLog.warn("test " + i, e);
                }
            }
            for (int i = 0; i < 15; i++) {
                udpSendLog.debug("test {}", i);
            }
            Thread.sleep(700);
            // debug events are not filtered
            assertEquals(30, CountAppender.getCount());
            String result = udpReceiver.sb.toString();
            System.out.println("udp result = " + result);
            assertTrue(result.contains("m:\"test 6"));
            assertTrue(result.contains("com.axibase.tsd.collector.logback.CollectorTest.testUdpSend"));
            assertTrue(result.contains("sun.reflect.NativeMethodAccessorImpl.invoke"));
            assertTrue(result.contains("junit.framework.TestResult$1.protect"));
            assertTrue(result.contains("t:level=WARN"));
            assertFalse(result.contains("t:level=DEBUG"));
            assertTrue(result.contains("m:log_event_counter"));
//            assertTrue(result.contains("m:log_event_total_rate"));
            assertTrue(result.contains("m:log_event_total_counter"));
        } finally {
            udpReceiver.stop();
        }
    }

    @Ignore
    @Test
    public void loadTcpSend() throws Exception {
        long st = System.currentTimeMillis();
        int cnt = 1000000;
        for (int i = 0; i < cnt; i++) {
            if (i % (cnt / 5) == 0) {
                System.out.println("i = " + i);
            }
//                tcpSendLog.info("test " + i, new NullPointerException("test"));
            tcpSendLog.info("test " + i);
        }
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
        Thread.sleep(1100);
    }

    @Ignore
    @Test
    public void loadUdpSend() throws Exception {
        long st = System.currentTimeMillis();
        for (int i = 0; i < 10000000; i++) {
            udpSendLog.warn("test {}", i);
        }
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }

    @Ignore
    @Test
    public void loadHttpSend() throws Exception {
        long st = System.currentTimeMillis();
        Exception exception = new RuntimeException("test");
        for (int i = 0; i < 1000000; i++) {
            httpSendLog.warn("test {}", i, exception);
//                if (i % 1000 == 0) {
//                    LockSupport.parkNanos(1);
//                    Thread.sleep(1000);
//                }
        }
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
        Thread.sleep(1100);
    }

    private static class UdpReceiver {

        private DatagramChannel datagramChannel;
        private StringBuilder sb;

        void start() throws Exception {
            sb = new StringBuilder();
            datagramChannel = DatagramChannel.open();
            datagramChannel.socket().bind(new InetSocketAddress(TestUtils.TEST_UDP_PORT));
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println("start UDP receiving");
                    while (datagramChannel.isOpen()) {
                        try {
                            ByteBuffer bb = ByteBuffer.allocate(TestUtils.BUFFER_SIZE);
                            datagramChannel.receive(bb);
                            bb.flip();
                            CharBuffer cb = TestUtils.UTF8_CHARSET.decode(bb);
                            sb.append(cb);
                            bb.clear();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.println("stop UDP receiving");
                }
            });
        }

        void stop() throws Exception {
            System.out.println("close datagram channel");
            if (datagramChannel != null) {
                datagramChannel.close();
            }
        }
    }
}
