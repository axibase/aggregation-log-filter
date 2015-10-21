package com.axibase.tsd.collector;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Nikolay Malevanny.
 */
public class SendMessageTriggerTest {
    @Test
    public void testOnEvent() throws Exception {
        SendMessageTrigger<String> trigger = new MockSendMessageTrigger();
        trigger.setEvery(1);
        trigger.setSkipMultiplier(2.0d);
        trigger.setResetPeriodSeconds(1); // 1000 ms
        assertTrue(trigger.onEvent("1")); // cnt=1 --> true
        assertTrue(trigger.onEvent("1")); // cnt=2 (2-1=1) --> true
        assertTrue(trigger.onEvent("2")); // cnt=1 --> true
        assertFalse(trigger.onEvent("1")); // cnt=3
        assertTrue(trigger.onEvent("1")); // cnt=4 (4-2=2) --> true
        assertFalse(trigger.onEvent("1")); // cnt=5
        assertFalse(trigger.onEvent("1")); // cnt=6
        assertFalse(trigger.onEvent("1")); // cnt=7
        assertTrue(trigger.onEvent("1")); // cnt=8 (8-4=4) --> true
        assertFalse(trigger.onEvent("1")); // cnt=9
        Thread.sleep(550); // < 1000ms
        assertFalse(trigger.onEvent("1")); // cnt=10
        Thread.sleep(550); // > 1000ms
        assertTrue(trigger.onEvent("1")); // cnt=1 --> true
    }

    private static class MockSendMessageTrigger extends SendMessageTrigger<String> {
        @Override
        public String resolveKey(String event) {
            return event;
        }
    }
}