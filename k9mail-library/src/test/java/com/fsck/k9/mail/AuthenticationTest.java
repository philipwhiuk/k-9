package com.fsck.k9.mail;


import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;

public class AuthenticationTest {

    @Test
    public void computeMd5_encodesDataCorrectly()
            throws UnsupportedEncodingException, MessagingException {
        String encodedData = Authentication.computeCramMd5("userA", "passwordB", "nonceC");
        assertEquals("dXNlckEgYTU5YzRhZWY3MDMwNTQwZjNlNzQ3OTQwMDI4ZTYzNTY=", encodedData);
    }

    @Test
    public void computeXoauth_encodesDataCorrectly() throws UnsupportedEncodingException {
        String encodedData = Authentication.computeXoauth("userA", "passwordB");
        assertEquals("dXNlcj11c2VyQQFhdXRoPUJlYXJlciBwYXNzd29yZEIBAQ==", encodedData);
    }
}
