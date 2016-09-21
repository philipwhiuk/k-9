package com.fsck.k9.mailstore;

import com.fsck.k9.mail.MessagingException;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BinaryMemoryBodyTest {

    @Test
    public void writeTo_writesDataToProvidedStream() throws IOException, MessagingException {
        byte[] data = new byte[1];
        BinaryMemoryBody body = new BinaryMemoryBody(data, "UTF-8");
        OutputStream outputStream = mock(OutputStream.class);

        body.writeTo(outputStream);

        verify(outputStream).write(data);
    }

    @Test
    public void getSize_returnsLengthOfData() throws IOException, MessagingException {
        byte[] data = new byte[1];
        BinaryMemoryBody body = new BinaryMemoryBody(data, "UTF-8");

        assertEquals(1, body.getSize());
    }

    @Test(expected = RuntimeException.class)
    public void setEncoding_throwsRuntimeException() throws IOException, MessagingException {
        byte[] data = new byte[1];
        BinaryMemoryBody body = new BinaryMemoryBody(data, "UTF-8");

        body.setEncoding("UTF-32");
    }
}
