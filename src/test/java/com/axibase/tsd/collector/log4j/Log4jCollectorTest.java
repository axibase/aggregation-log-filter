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

package com.axibase.tsd.collector.log4j;

import com.axibase.tsd.collector.*;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

public class Log4jCollectorTest {
    private static final Logger log = Logger.getLogger(Log4jCollectorTest.class);

    static {
        final URL url = ClassLoader.getSystemClassLoader().getResource("./test.xml");
//        final URL url = ClassLoader.getSystemClassLoader().getResource("./log4j-test.xml");
        DOMConfigurator.configure(url);
//        final URL url = ClassLoader.getSystemClassLoader().getResource("./log4j-test.properties");
//        PropertyConfigurator.configure(url);
    }

    @Test
    public void testTcpSend() throws Exception {
        Log4jCountAppender.clear();
        TcpReceiver tcpReceiver = new TcpReceiver(TestUtils.TEST_TCP_PORT_LOG4J);
        try {
            tcpReceiver.start();

            // 15 > <minIntervalThreshold>10</minIntervalThreshold>
            // 15 > <sendEvery>7</sendEvery>
            // <level>WARN</level>
            for (int i = 0; i < 15; i++) {
                MDC.put("myKey","TEST_KEY_" + i);
                log.warn("test " +  i);
                System.out.println(Thread.currentThread().getName() + " - MDC.get(\"myKey\") = " + MDC.get("myKey"));
                MDC.remove("myKey");
            }
            for (int i = 0; i < 15; i++) {
                log.debug("test " + i);
            }
//            log.error("test " +  0);
            // debug events are not filtered
            Thread.sleep(1100);
            String result = tcpReceiver.getSb().toString();
            System.out.println("tcp result = " + result);
            assertEquals(30, Log4jCountAppender.getCount());
            // check message content
            assertTrue(result.contains("t:ttt=ttt t:ppp=ppp t:mmm=mmm"));
            assertTrue(result.contains("Log4jCollectorTest - test 0 [TEST_KEY_0]\""));
            assertFalse(result.contains("Log4jCollectorTest - test 4"));
            assertTrue(result.contains("Log4jCollectorTest - test 5"));
            assertTrue(result.contains("Log4jCollectorTest - test 7"));
            assertTrue(result.contains("t:level=WARN"));
            assertFalse(result.contains("m:log_event_total_counter=0 t:level=INFO"));
            assertTrue(result.contains("m:log_event_total_counter=0 t:level=TRACE"));
            assertTrue(result.contains("com.axibase.tsd.collector.log4j.Log4jCollectorTest"));
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
