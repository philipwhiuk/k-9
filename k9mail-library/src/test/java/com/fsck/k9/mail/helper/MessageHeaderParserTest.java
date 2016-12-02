package com.fsck.k9.mail.helper;


import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.message.MessageHeaderParser;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;

public class MessageHeaderParserTest {

    @Test
    public void parse_setsRawHeadersOnPartFromStream() throws MessagingException {
        String testMessage = "MIME-Version: 1.0";
        Part part = new MimeMessage();
        MessageHeaderParser.parse(part, new ByteArrayInputStream(testMessage.getBytes()));

        assertEquals("1.0", part.getHeader("MIME-Version")[0]);
    }
}
