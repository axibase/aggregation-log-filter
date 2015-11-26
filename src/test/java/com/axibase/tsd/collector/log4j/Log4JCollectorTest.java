package com.axibase.tsd.collector.log4j;

import com.axibase.tsd.collector.TcpReceiver;
import com.axibase.tsd.collector.TestUtils;
import com.axibase.tsd.collector.logback.CountAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

/**
 * @author Nikolay Malevanny.
 */
public class Log4JCollectorTest {
    private static final Logger log = Logger.getLogger(Log4JCollectorTest.class);

    static {
        final URL url = ClassLoader.getSystemClassLoader().getResource("./log4j-test.properties");
        PropertyConfigurator.configure(url);
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
                log.warn("test " +  i);
            }
            for (int i = 0; i < 15; i++) {
                log.debug("test " + i);
            }
            // debug events are not filtered
            Thread.sleep(1100);
            String result = tcpReceiver.getSb().toString();
            System.out.println("tcp result = " + result);
            assertEquals(30, Log4jCountAppender.getCount());
            // check message content
            assertTrue(result.contains("t:ttt=ttt t:ppp=ppp t:mmm=mmm"));
            assertTrue(result.contains("m:\"test 0\""));
            assertFalse(result.contains("m:\"test 4\""));
            assertTrue(result.contains("m:\"test 5\""));
            assertTrue(result.contains("m:\"test 7\""));
            assertTrue(result.contains("t:level=WARN"));
            assertFalse(result.contains("m:log_event_total_counter=0 t:level=INFO"));
            assertTrue(result.contains("m:log_event_total_counter=0 t:level=TRACE"));
            assertTrue(result.contains("com.axibase.tsd.collector.log4j.Log4JCollectorTest"));
            assertFalse(result.contains("t:level=DEBUG"));
            // check series content
            assertTrue(result.contains("m:log_event_counter="));
            assertTrue(result.contains("m:log_event_total_rate="));
            assertTrue(result.contains("m:log_event_total_counter="));
        } finally {
            tcpReceiver.stop();
        }
    }

}