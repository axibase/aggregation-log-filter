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

import static org.junit.Assert.assertEquals;

public class AtsdUtilTest {

    @Test
    public void testSanitizeEntity() throws Exception {
        String entityName = "just test";
        assertEquals("just_test", AtsdUtil.sanitizeEntity(entityName));
    }

    @Test
    public void testSanitizeMessage() throws Exception {
        assertEquals("\"\"", AtsdUtil.sanitizeMessage(null));
        assertEquals("\"\"", AtsdUtil.sanitizeMessage(""));
        assertEquals("\"\"", AtsdUtil.sanitizeMessage("  "));
        assertEquals("\"\"", AtsdUtil.sanitizeMessage("\n"));
        assertEquals("\"\"", AtsdUtil.sanitizeMessage("\r\n"));
        assertEquals("aaa", AtsdUtil.sanitizeMessage(" aaa "));
    }
}