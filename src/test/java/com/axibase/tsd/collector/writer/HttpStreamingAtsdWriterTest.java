package com.axibase.tsd.collector.writer;

import com.axibase.tsd.collector.AtsdUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

/**
 * @author Nikolay Malevanny.
 */
public class HttpStreamingAtsdWriterTest {
    public static final long M = 1000 * 1000L;

    @Test
    public void testBadAddress() throws Exception {
        HttpStreamingAtsdWriter writer = new HttpStreamingAtsdWriter();
        writer.setUrl("http://localhost:0");
        writer.setTimeout(300);
        writer.write(ByteBuffer.wrap("test\n".getBytes(AtsdUtil.UTF_8)));
        assertFalse(writer.isConnected());
    }

    @Ignore
    @Test
    public void ping() throws Exception {
        long st = System.currentTimeMillis();
        HttpStreamingAtsdWriter writer = new HttpStreamingAtsdWriter();
        writer.setUrl("http://localhost:8088/api/v1/command/");
        writer.setUsername("axibase");
        writer.setPassword("11111");
        writer.setTimeout(1000);
        for (long i = 0; i < 1E7; i++) {
            writer.write(ByteBuffer.wrap("ping\n".getBytes(AtsdUtil.UTF_8)));
        }
        assertTrue(writer.isConnected());
        assertEquals(0, writer.getSkippedCount());
        writer.close();
        assertFalse(writer.isConnected());
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }

    @Ignore
    @Test
    public void uploadMetrics() throws Exception {
        long st = System.currentTimeMillis();
        HttpStreamingAtsdWriter writer = new HttpStreamingAtsdWriter();
        writer.setUrl("http://localhost:8088/api/v1/command/");
        writer.setUsername("axibase");
        writer.setPassword("11111");
        writer.setTimeout(10000);
        long cnt = 10 * M;
        for (long i = 0; i < cnt; i++) {
            ByteBuffer data = ByteBuffer.wrap(("series e:test_entity ms:" + (st - (cnt - i) * 1000) +
                    " m:t_metric=" + i +
                    "\n").getBytes(AtsdUtil.UTF_8));
            writer.write(data);
        }
        assertTrue(writer.isConnected());
        assertEquals(0, writer.getSkippedCount());
        writer.close();
        assertFalse(writer.isConnected());
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }

    @Ignore
    @Test
    public void uploadMetricsSsl() throws Exception {
        long st = System.currentTimeMillis();
        HttpStreamingAtsdWriter writer = new HttpStreamingAtsdWriter();
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
        assertTrue(writer.isConnected());
        assertEquals(0, writer.getSkippedCount());
        writer.close();
        assertFalse(writer.isConnected());
        System.out.println("time: " + (System.currentTimeMillis() - st) + " ms");
    }
}