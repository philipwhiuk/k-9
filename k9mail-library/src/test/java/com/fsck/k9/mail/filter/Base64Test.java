package com.fsck.k9.mail.filter;


import org.junit.Test;

public class Base64Test {

    @Test(expected = Base64.DecoderException.class)
    public void decode_withNonByteArraythrowsDecoderException() throws Base64.DecoderException {
        new Base64().decode(new Object());
    }

    @Test(expected = Base64.EncoderException.class)
    public void encode_withNonByteArraythrowsEncoderException() throws Base64.EncoderException {
        new Base64().encode(new Object());
    }
}
