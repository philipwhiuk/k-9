package com.fsck.k9.mailstore;


import android.app.PendingIntent;

import com.fsck.k9.mail.internet.MimeBodyPart;

import org.junit.Test;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.smime.SMimeSignatureResult;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class CryptoResultAnnotationTest {

    @Test
    public void hasSignatureResult__trueWithPgpSignatureResult() {
        OpenPgpSignatureResult signatureResult = mock(OpenPgpSignatureResult.class);
        PendingIntent intent = mock(PendingIntent.class);
        MimeBodyPart part = mock(MimeBodyPart.class);
        CryptoResultAnnotation annotation = CryptoResultAnnotation.
                createOpenPgpResultAnnotation(null, signatureResult, intent, part);

        assertTrue(annotation.hasSignatureResult());
    }

    @Test
    public void hasSignatureResult__trueWithSMimeSignatureResult() {
        SMimeSignatureResult signatureResult = mock(SMimeSignatureResult.class);
        PendingIntent intent = mock(PendingIntent.class);
        MimeBodyPart part = mock(MimeBodyPart.class);
        CryptoResultAnnotation annotation = CryptoResultAnnotation.
                createSMimeResultAnnotation(null, signatureResult, intent, part);

        assertTrue(annotation.hasSignatureResult());
    }
}
