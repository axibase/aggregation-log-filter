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
        trigger.setSendMultiplier(3.0d);
        trigger.setResetIntervalSeconds(1); // 1000 ms
        assertTrue(trigger.onEvent("1")); // cnt=1 --> true
        assertFalse(trigger.onEvent("1")); // cnt=2
        assertTrue(trigger.onEvent("2")); // cnt=1 --> true
        assertTrue(trigger.onEvent("1")); // cnt=3 (1*3=3) --> true
        assertFalse(trigger.onEvent("1")); // cnt=4
        assertFalse(trigger.onEvent("1")); // cnt=5
        assertFalse(trigger.onEvent("1")); // cnt=6
        assertFalse(trigger.onEvent("1")); // cnt=7
        assertFalse(trigger.onEvent("1")); // cnt=8
        assertTrue(trigger.onEvent("1")); // cnt=9 (3*3=9) --> true
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