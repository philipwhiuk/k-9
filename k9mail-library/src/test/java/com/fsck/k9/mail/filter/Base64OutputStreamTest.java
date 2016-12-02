package com.fsck.k9.mail.filter;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

public class Base64OutputStreamTest {

    @Test
    public void write_with_doEncode_true_encodesData() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Base64OutputStream base64OutputStream = new Base64OutputStream(byteArrayOutputStream, true);
        byte[] inputByteArray = "TestData".getBytes(Charset.defaultCharset());

        base64OutputStream.write(inputByteArray);

        base64OutputStream.flush();
        byte[] outputByteArray = byteArrayOutputStream.toByteArray();
        String outputString = new String(outputByteArray);

        assertEquals("VGVzdERh", outputString);
    }

    @Test
    public void write_with_doEncode_false_decodesData() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Base64OutputStream base64OutputStream = new Base64OutputStream(byteArrayOutputStream, false);
        byte[] inputByteArray = "VGVzdERhdGE=".getBytes(Charset.defaultCharset());

        base64OutputStream.write(inputByteArray);

        base64OutputStream.flush();
        byte[] outputByteArray = byteArrayOutputStream.toByteArray();
        String outputString = new String(outputByteArray);

        assertEquals("TestData", outputString);
    }
}
