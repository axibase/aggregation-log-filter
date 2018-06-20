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

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AtsdUtilTest {

    @Test
    public void testFilter() {
        Map<String, String> map = new HashMap<>();
        map.put("ATSD_HOME", "v1");
        map.put("atsd_home", "v2");
        map.put("Empty", "");
        assertFalse(AtsdUtil.filterProperties(map).containsKey("Empty"));
        assertTrue(AtsdUtil.filterProperties(map).containsKey("ATSD_HOME"));
        assertEquals("v2", AtsdUtil.filterProperties(map).get("ATSD_HOME"));

        Properties props = System.getProperties();
        props.put("ATSD_HOME", "v1");
        props.put("atsd_home", "v2");
        props.put("Empty", "");
        props.put("Bool", true);
        System.setProperties(props);
        assertFalse(AtsdUtil.filterProperties(props).containsKey("Empty"));
        assertTrue(AtsdUtil.filterProperties(props).containsKey("ATSD_HOME"));
        assertEquals("v2", AtsdUtil.filterProperties(props).get("ATSD_HOME"));
    }

    @Test
    public void testSanitizeEntity() {
        String entityName = "just test";
        assertEquals("just_test", AtsdUtil.sanitizeEntity(entityName));
    }

    @Test
    public void testSanitizeMessage() {
        assertEquals("\"\"", AtsdUtil.escapeCSV(null));
        assertEquals("\"\"", AtsdUtil.escapeCSV(""));
        assertEquals("\"\"", AtsdUtil.escapeCSV("  "));
        assertEquals("\"\"", AtsdUtil.escapeCSV("\n"));
        assertEquals("\"\"", AtsdUtil.escapeCSV("\r"));
        assertEquals("\"\"", AtsdUtil.escapeCSV("\r\n"));
        assertEquals("aaa", AtsdUtil.escapeCSV(" aaa "));
        assertEquals("\"\"\"\"", AtsdUtil.escapeCSV("\""));
    }

    @Test
    public void testSanitizeValue() {
        String longString = StringUtils.repeat("a", 1500);
        assertEquals(1000, AtsdUtil.sanitizeValue(longString).length());
        assertEquals("\\n", AtsdUtil.sanitizeValue("\r"));
        assertEquals("\\n", AtsdUtil.sanitizeValue("\n"));
        assertEquals("\\n", AtsdUtil.sanitizeValue("\n\r"));
    }
}