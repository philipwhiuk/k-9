package com.fsck.k9.mail.filter;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class Base64Test {

    @Test
    public void encodingNull_isNull() {
        assertNull(Base64.encode((String)null));
    }

    @Test
    public void decodingNull_isNull() {
        assertNull(Base64.decode((String)null));
    }

    @Test
    public void encodingAndDecodingIsSymmetric() {
        assertEquals("a", Base64.decode(Base64.encode("a")));
    }

    @Test
    public void encodingIsConsistent() {
        assertEquals(Base64.encode("a"), Base64.encode("a"));
    }

    @Test
    public void encodingMatchesExpectedOutput() {
        assertEquals("VGVzdCB0ZXh0", Base64.encode("Test text"));
    }

}
