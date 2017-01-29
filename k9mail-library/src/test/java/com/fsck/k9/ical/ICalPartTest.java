package com.fsck.k9.ical;


import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ICalPartTest {

    @Test
    public void getUnderlyingPart_returnsUnderlyingPart() throws MessagingException {
        Part underlyingPart = mock(Part.class);
        ICalPart iCalPart = new ICalPart(underlyingPart);

        assertEquals(underlyingPart, iCalPart.getPart());
    }
}
