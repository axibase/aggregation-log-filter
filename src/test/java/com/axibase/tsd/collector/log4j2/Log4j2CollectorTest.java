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

package com.axibase.tsd.collector.log4j2;

import com.axibase.tsd.collector.TcpReceiver;
import com.axibase.tsd.collector.TestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.Test;

import static org.junit.Assert.*;

public class Log4j2CollectorTest {
    private static final Logger log = LogManager.getLogger(Log4j2CollectorTest.class);

    static {
        System.setProperty("log4j.configurationFile", "classpath:log4j2-test.xml");
    }

    @Test
    public void testTcpSend() throws Exception {
        Log4j2CountAppender.clear();
        TcpReceiver tcpReceiver = new TcpReceiver(TestUtils.TEST_TCP_PORT_LOG4J);
        try {
            tcpReceiver.start();

            // 15 > <minIntervalThreshold>10</minIntervalThreshold>
            // 15 > <sendEvery>7</sendEvery>
            // <level>WARN</level>
            for (int i = 0; i < 15; i++) {
                ThreadContext.put("myKey", "TEST_KEY_" + i);
                log.warn("test " +  i);
                System.out.println(Thread.currentThread().getName()
                        + " - ThreadContext.get(\"myKey\") = " + ThreadContext.get("myKey"));
                ThreadContext.remove("myKey");
            }
            for (int i = 0; i < 15; i++) {
                log.debug("test " + i);
            }
//            log.error("test " +  0);
            // debug events are not filtered
            Thread.sleep(1100);
            String result = tcpReceiver.getSb().toString();
            System.out.println("tcp result = " + result);
            assertEquals(30, Log4j2CountAppender.getCount());
            // check message content
            assertTrue(result.contains("t:ttt=ttt t:ppp=ppp t:mmm=mmm"));
            assertTrue(result.contains("Log4j2CollectorTest - test 0 [TEST_KEY_0]\""));
            assertFalse(result.contains("Log4j2CollectorTest - test 4"));
            assertTrue(result.contains("Log4j2CollectorTest - test 5"));
            assertTrue(result.contains("Log4j2CollectorTest - test 7"));
            assertTrue(result.contains("t:level=WARN"));
            assertFalse(result.contains("m:log_event_total_counter=0 t:level=INFO"));
            assertTrue(result.contains("m:log_event_total_counter=0 t:level=TRACE"));
            assertTrue(result.contains("com.axibase.tsd.collector.log4j2.Log4j2CollectorTest"));
            assertFalse(result.contains("t:level=DEBUG"));
            // check series content
            assertTrue(result.contains("m:log_event_counter="));
//            assertTrue(result.contains("m:log_event_total_rate="));
            assertTrue(result.contains("m:log_event_total_counter="));
        } finally {
            tcpReceiver.stop();
        }
    }

}
