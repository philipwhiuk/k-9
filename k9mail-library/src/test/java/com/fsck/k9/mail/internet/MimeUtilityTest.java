package com.fsck.k9.mail.internet;


import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.Part;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "src/main/AndroidManifest.xml", sdk = 21)
public class MimeUtilityTest {
    @Test
    public void testGetHeaderParameter() {
        String result;

        /* Test edge cases */
        result = MimeUtility.getHeaderParameter(";", null);
        assertEquals(null, result);

        result = MimeUtility.getHeaderParameter("name", "name");
        assertEquals(null, result);

        result = MimeUtility.getHeaderParameter("name=", "name");
        assertEquals("", result);

        result = MimeUtility.getHeaderParameter("name=\"", "name");
        assertEquals("\"", result);

        /* Test expected cases */
        result = MimeUtility.getHeaderParameter("name=value", "name");
        assertEquals("value", result);

        result = MimeUtility.getHeaderParameter("name = value", "name");
        assertEquals("value", result);

        result = MimeUtility.getHeaderParameter("name=\"value\"", "name");
        assertEquals("value", result);

        result = MimeUtility.getHeaderParameter("name = \"value\"", "name");
        assertEquals("value", result);

        result = MimeUtility.getHeaderParameter("name=\"\"", "name");
        assertEquals("", result);

        result = MimeUtility.getHeaderParameter("name=\"ABC; DEF.pdf\"", "name");
        assertEquals("ABC; DEF.pdf", result);

        result = MimeUtility.getHeaderParameter("name=\"ABC; DEF.pdf\"; charset=\"windows-1251\"", "name");
        assertEquals("ABC; DEF.pdf", result);

        result = MimeUtility.getHeaderParameter("name=\"ABC; DEF.pdf\"; charset=\"windows-1251\"", "charset");
        assertEquals("windows-1251", result);

        result = MimeUtility.getHeaderParameter("name=\"ABC; DEF.pdf\"; name2=\"GHI; JKL.pdf\"", "name");
        assertEquals("ABC; DEF.pdf", result);

        result = MimeUtility.getHeaderParameter("name=\"ABC; DEF.pdf\"; name2=\"GHI; JKL.pdf\"", "name2");
        assertEquals("GHI; JKL.pdf", result);

        result = MimeUtility.getHeaderParameter("attachment;\n filename=\"k9small.png\";\n size=2250", "size");
        assertEquals("2250", result);

        result = MimeUtility.getHeaderParameter("text/html ; charset=\"windows-1251\"", null);
        assertEquals("text/html", result);

        result = MimeUtility.getHeaderParameter("text/HTML ; charset=\"windows-1251\"", null);
        assertEquals("text/HTML", result);
    }

    @Test
    public void isMultipart_withLowerCaseMultipart_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isMultipart("multipart/mixed"));
    }

    @Test
    public void isMultipart_withUpperCaseMultipart_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isMultipart("MULTIPART/ALTERNATIVE"));
    }

    @Test
    public void isMultipart_withMixedCaseMultipart_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isMultipart("Multipart/Alternative"));
    }

    @Test
    public void isMultipart_withoutMultipart_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isMultipart("message/rfc822"));
    }

    @Test
    public void isMultipart_withNullArgument_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isMultipart(null));
    }

    @Test
    public void isMessage_withLowerCaseMessage_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isMessage("message/rfc822"));
    }

    @Test
    public void isMessage_withUpperCaseMessage_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isMessage("MESSAGE/RFC822"));
    }

    @Test
    public void isMessage_withMixedCaseMessage_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isMessage("Message/Rfc822"));
    }

    @Test
    public void isMessage_withoutMessageRfc822_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isMessage("Message/Partial"));
    }

    @Test
    public void isMessage_withoutMessage_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isMessage("multipart/mixed"));
    }

    @Test
    public void isMessage_withNullArgument_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isMessage(null));
    }

    @Test
    public void isSameMimeType_withSameTypeAndCase_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isSameMimeType("text/plain", "text/plain"));
    }

    @Test
    public void isSameMimeType_withSameTypeButMixedCase_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isSameMimeType("text/plain", "Text/Plain"));
    }

    @Test
    public void isSameMimeType_withSameTypeAndLowerAndUpperCase_shouldReturnTrue() throws Exception {
        assertTrue(MimeUtility.isSameMimeType("TEXT/PLAIN", "text/plain"));
    }

    @Test
    public void isSameMimeType_withDifferentType_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isSameMimeType("text/plain", "text/html"));
    }

    @Test
    public void isSameMimeType_withFirstArgumentBeingNull_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isSameMimeType(null, "text/html"));
    }

    @Test
    public void isSameMimeType_withSecondArgumentBeingNull_shouldReturnFalse() throws Exception {
        assertFalse(MimeUtility.isSameMimeType("text/html", null));
    }

    @Test
    public void findFirstPartByMimeType_returnsNullWhenPartDoesNotMatch() throws MessagingException {
        Part part = new MimeBodyPart();
        part.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "text/plain");
        assertNull(MimeUtility.findFirstPartByMimeType(part, "text/html"));
    }

    @Test
    public void findFirstPartByMimeType_returnsPartWhenPartMatches() throws MessagingException {
        Part part = new MimeBodyPart();
        part.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "text/html");
        assertEquals(part, MimeUtility.findFirstPartByMimeType(part, "text/html"));
    }

    @Test
    public void findFirstPartByMimeType_returnsNestedPartWhenNestedPartMatches() throws MessagingException {
        Part part = new MimeBodyPart();
        Multipart multipart = new MimeMultipart("abcd");
        part.setBody(multipart);
        BodyPart nestedPart = new MimeBodyPart();
        nestedPart.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "text/html");
        multipart.addBodyPart(nestedPart);
        assertEquals(nestedPart, MimeUtility.findFirstPartByMimeType(part, "text/html"));
    }

    @Test
    public void getMimeTypeByExtension_returnsCorrectType() {
        assertEquals("text/html", MimeUtility.getMimeTypeByExtension("index.html"));
    }

    @Test
    public void getExtensionByMimeType_returnsCorrectExtension() {
        assertEquals("html", MimeUtility.getExtensionByMimeType("text/html"));
    }

    @Test
    public void getEncodingForType_withNull_returnsBase64() {
        assertEquals("base64", MimeUtility.getEncodingforType(null));
    }

    @Test
    public void getEncodingForType_withMessage_returns8Bit() {
        assertEquals("8bit", MimeUtility.getEncodingforType("message/rfc822"));
    }

    @Test
    public void getEncodingForType_withSignedMessage_returns7bit() {
        assertEquals("7bit", MimeUtility.getEncodingforType("multipart/signed"));
    }

    @Test
    public void getEncodingForType_withOtherMultipart_returns8Bit() {
        assertEquals("8bit", MimeUtility.getEncodingforType("multipart/digest"));
    }

    @Test
    public void getEncodingForType_withOther_returnsBase64() {
        assertEquals("base64", MimeUtility.getEncodingforType("unknown"));
    }

    @Test
    public void isDefaultMimeType_returnsTrueForOctetStream() {
        assertTrue(MimeUtility.isDefaultMimeType("application/octet-stream"));
    }

    @Test
    public void isDefaultMimeType_returnsFalseForOtherMimeType() {
        assertFalse(MimeUtility.isDefaultMimeType("text/html"));
    }
}
