package com.fsck.k9.mailstore;


import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeBodyPart;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageHelperTest {

    @Test
    public void isCompletePartAvailable__returnsTrueForPartWithBody() {
        Part part = mock(Part.class);
        Body body = mock(Body.class);
        when(part.getBody()).thenReturn(body);

        boolean result = MessageHelper.isCompletePartAvailable(part);

        assertTrue(result);
    }

    @Test
    public void isCompletePartAvailable__returnsFalseForPartWithNoBody() {
        Part part = mock(Part.class);

        boolean result = MessageHelper.isCompletePartAvailable(part);

        assertFalse(result);
    }

    @Test
    public void isCompletePartAvailable__returnsTrueForPartWithMultipartWithNoParts() {
        Part part = mock(Part.class);
        Multipart multipart = mock(Multipart.class);
        when(part.getBody()).thenReturn(multipart);
        when(multipart.getBodyParts()).thenReturn(Collections.<BodyPart>emptyList());

        boolean result = MessageHelper.isCompletePartAvailable(part);

        assertTrue(result);
    }

    @Test
    public void isCompletePartAvailable__returnsTrueForPartWithMultipartWithBodyPartWithBody() {
        Part part = mock(Part.class);
        Multipart multipart = mock(Multipart.class);
        when(part.getBody()).thenReturn(multipart);
        BodyPart subPart = mock(BodyPart.class);
        when(subPart.getBody()).thenReturn(mock(Body.class));
        when(multipart.getBodyParts()).thenReturn(Collections.singletonList(subPart));

        boolean result = MessageHelper.isCompletePartAvailable(part);

        assertTrue(result);
    }

    @Test
    public void isCompletePartAvailable__returnsFalseForPartWithMultipartWithBodyPartWithNoBody() {
        Part part = mock(Part.class);
        Multipart multipart = mock(Multipart.class);
        when(part.getBody()).thenReturn(multipart);
        BodyPart subPart = mock(BodyPart.class);
        when(multipart.getBodyParts()).thenReturn(Collections.singletonList(subPart));

        boolean result = MessageHelper.isCompletePartAvailable(part);

        assertFalse(result);
    }

    @Test
    public void createEmptyPart__returnsPartWithNoBody() {
        MimeBodyPart part = MessageHelper.createEmptyPart();

        assertNull(part.getBody());
    }

    @Test
    public void createEmptyPart__returnsNewPart() {
        MimeBodyPart part = MessageHelper.createEmptyPart();
        MimeBodyPart part2 = MessageHelper.createEmptyPart();

        assertNotSame(part, part2);
    }
}
