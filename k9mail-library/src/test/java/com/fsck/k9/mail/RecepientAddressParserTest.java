package com.fsck.k9.mail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public class RecepientAddressParserTest {

    @Mock Address mockReplyTo;
    @Mock Address mockFrom;

    @Test
    public void providesReplyToIfPresent() throws MessagingException {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getReplyTo()).thenReturn(new Address[]{mockReplyTo});
        when(mockMessage.getFrom()).thenReturn(new Address[]{mockFrom});
        when(mockMessage.getHeader("List-Post")).thenReturn(new String[]{"<mailto:list@host.com>"});
        Address[] replyAddresses = RecepientAddressParser.parseReplyAddresses(mockMessage);
        assertEquals(mockReplyTo, replyAddresses[0]);
    }

    @Test
    public void canParseListPost() throws MessagingException {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getReplyTo()).thenReturn(new Address[]{});
        when(mockMessage.getHeader("List-Post")).thenReturn(new String[]{"<mailto:list@host.com>"});
        Address[] replyAddresses = RecepientAddressParser.parseReplyAddresses(mockMessage);
        assertEquals("list@host.com", replyAddresses[0].getAddress());
    }
    @Test
    public void canParseListPostWithSubject() throws MessagingException {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getReplyTo()).thenReturn(new Address[]{});
        when(mockMessage.getHeader("List-Post")).thenReturn(new String[]{"<mailto:list@host.com?subj=MySubj>"});
        Address[] replyAddresses = RecepientAddressParser.parseReplyAddresses(mockMessage);
        assertEquals("list@host.com", replyAddresses[0].getAddress());
    }
    @Test
    public void canParseListPostWithName() throws MessagingException {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getReplyTo()).thenReturn(new Address[]{});
        when(mockMessage.getHeader("List-Post")).thenReturn(new String[]{"<mailto:list@host.com> (Postings are Moderated)"});
        Address[] replyAddresses = RecepientAddressParser.parseReplyAddresses(mockMessage);
        assertEquals("list@host.com", replyAddresses[0].getAddress());
    }
    @Test
    public void canParseNOInListPost() throws MessagingException {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getReplyTo()).thenReturn(new Address[]{});
        when(mockMessage.getHeader("List-Post")).thenReturn(new String[]{"NO"});
        when(mockMessage.getFrom()).thenReturn(new Address[]{mockFrom});
        Address[] replyAddresses = RecepientAddressParser.parseReplyAddresses(mockMessage);
        assertEquals(mockFrom, replyAddresses[0]);
    }
}
