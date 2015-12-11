package com.axibase.tsd.collector;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Nikolay Malevanny.
 */
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