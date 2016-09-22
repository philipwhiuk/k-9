package com.fsck.k9.mail.store.imap;

import android.test.RenamingDelegatingContext;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.filter.FixedLengthInputStream;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.fsck.k9.mail.internet.MimeMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public class FetchBodyCallbackTest {

    public static final String MESSAGE_SOURCE = "From: from@example.com\r\n" +
            "To: to@example.com\r\n" +
            "Subject: Test Message \r\n" +
            "Date: Thu, 13 Nov 2014 17:09:38 +0100\r\n" +
            "Content-Type: multipart/mixed;\r\n" +
            " boundary=\"----Boundary\"\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "MIME-Version: 1.0\r\n" +
            "\r\n" +
            "This is a multipart MIME message.\r\n" +
            "------Boundary\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Transfer-Encoding: 8bit\r\n" +
            "\r\n" +
            "Testing.\r\n" +
            "This is a text body with some greek characters.\r\n" +
            "αβγδεζηθ\r\n" +
            "End of test.\r\n" +
            "\r\n" +
            "------Boundary\r\n" +
            "Content-Type: text/plain\r\n" +
            "Content-Transfer-Encoding: base64\r\n" +
            "\r\n" +
            "VGhpcyBpcyBhIHRl\r\n" +
            "c3QgbWVzc2FnZQ==\r\n" +
            "\r\n" +
            "------Boundary--\r\n" +
            "Hi, I'm the epilogue";

    @Test
    public void foundLiteral_returns_1_when_literalFound() throws IOException, MessagingException, InterruptedException {
        ImapFolder imapFolder = mock(ImapFolder.class);
        ImapMessage imapMessage = new ImapMessage("msg", imapFolder);
        final ImapResponse imapResponse = mock(ImapResponse.class);
        ImapList imapList = mock(ImapList.class);

        InputStream messageInputStream = new ByteArrayInputStream(MESSAGE_SOURCE.getBytes());
        final FixedLengthInputStream fixedLengthInputStream = new FixedLengthInputStream(messageInputStream, MESSAGE_SOURCE.length());
        final FetchBodyCallback callback = new FetchBodyCallback(Collections.<String, Message>singletonMap("msg", imapMessage));

        when(imapResponse.get(1)).thenReturn("FETCH");
        when(imapResponse.getKeyedValue("FETCH")).thenReturn(imapList);
        when(imapList.getKeyedString("UID")).thenReturn("msg");

        BinaryTempFileBody.setTempDirectory(RuntimeEnvironment.application.getCacheDir());

        assertEquals(1, callback.foundLiteral(imapResponse, fixedLengthInputStream));
    }

    @Test
    public void foundLiteral_returns_null_when_tagNotNull() throws IOException, MessagingException {
        ImapMessage imapMessage = mock(ImapMessage.class);
        ImapResponse imapResponse = mock(ImapResponse.class);
        FixedLengthInputStream fixedLengthInputStream = mock(FixedLengthInputStream.class);
        FetchBodyCallback callback = new FetchBodyCallback(Collections.<String, Message>singletonMap("msg", imapMessage));

        when(imapResponse.getTag()).thenReturn("a");

        assertNull(callback.foundLiteral(imapResponse, fixedLengthInputStream));
    }

    @Test
    public void foundLiteral_returns_null_when_responseNotFetch() throws IOException, MessagingException {
        ImapMessage imapMessage = mock(ImapMessage.class);
        ImapResponse imapResponse = mock(ImapResponse.class);
        FixedLengthInputStream fixedLengthInputStream = mock(FixedLengthInputStream.class);
        FetchBodyCallback callback = new FetchBodyCallback(Collections.<String, Message>singletonMap("msg", imapMessage));

        when(imapResponse.get(1)).thenReturn("MISC");

        assertNull(callback.foundLiteral(imapResponse, fixedLengthInputStream));
    }
}
