package com.fsck.k9.message;


import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

import com.fsck.k9.Account.QuoteStyle;
import com.fsck.k9.Identity;
import com.fsck.k9.activity.compose.ComposeCryptoStatus;
import com.fsck.k9.activity.compose.ComposeCryptoStatus.ComposeCryptoStatusBuilder;
import com.fsck.k9.activity.compose.RecipientPresenter.CryptoMode;
import com.fsck.k9.activity.compose.RecipientPresenter.CryptoProviderState;
import com.fsck.k9.activity.misc.Attachment;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.BoundaryGenerator;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.fsck.k9.mail.internet.MessageIdGenerator;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.mail.internet.TextBody;
import com.fsck.k9.message.MessageBuilder.Callback;
import com.fsck.k9.view.RecipientSelectView.Recipient;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.util.MimeUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.openintents.smime.util.SMimeApi;
import org.openintents.smime.util.SMimeApi.SMimeDataSource;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@RunWith(RobolectricTestRunner.class)
@Config(manifest = "src/main/AndroidManifest.xml", sdk = 21)
public class SMimeMessageBuilderTest {
    public static final long TEST_SIGN_CERTIFICATE_ID = 123L;
    public static final long TEST_SELF_ENCRYPT_CERTIFICATE_ID = 234L;
    public static final String TEST_MESSAGE_TEXT = "message text with a ☭ CCCP symbol";


    private ComposeCryptoStatusBuilder cryptoStatusBuilder = createDefaultComposeCryptoStatusBuilder();
    private SMimeApi sMimeApi = mock(SMimeApi.class);
    private SMimeMessageBuilder sMimeMessageBuilder = createDefaultSMimeMessageBuilder(sMimeApi);


    @Test(expected = AssertionError.class)
    public void build__withDisabledCrypto__shouldError() throws MessagingException {
        sMimeMessageBuilder.setCryptoStatus(cryptoStatusBuilder.setCryptoMode(CryptoMode.DISABLE).build());

        sMimeMessageBuilder.buildAsync(mock(Callback.class));
    }

    @Test
    public void build__withCryptoProviderNotOk__shouldThrow() throws MessagingException {
        cryptoStatusBuilder.setCryptoMode(CryptoMode.SIGN_ONLY);
        CryptoProviderState[] cryptoProviderStates = {
                CryptoProviderState.LOST_CONNECTION, CryptoProviderState.UNCONFIGURED,
                CryptoProviderState.UNINITIALIZED, CryptoProviderState.ERROR
        };

        for (CryptoProviderState state : cryptoProviderStates) {
            cryptoStatusBuilder.setCryptoProviderState(state);
            sMimeMessageBuilder.setCryptoStatus(cryptoStatusBuilder.build());

            Callback mockCallback = mock(Callback.class);
            sMimeMessageBuilder.buildAsync(mockCallback);

            verify(mockCallback).onMessageBuildException(any(MessagingException.class));
            verifyNoMoreInteractions(mockCallback);
        }
    }

    @Test
    public void buildSign__withNoDetachedSignatureInResult__shouldThrow() throws MessagingException {
        cryptoStatusBuilder.setCryptoMode(CryptoMode.SIGN_ONLY);
        sMimeMessageBuilder.setCryptoStatus(cryptoStatusBuilder.build());

        Intent returnIntent = new Intent();
        returnIntent.putExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_SUCCESS);
        when(sMimeApi.executeApi(any(Intent.class), any(SMimeDataSource.class), any(OutputStream.class)))
                .thenReturn(returnIntent);

        Callback mockCallback = mock(Callback.class);
        sMimeMessageBuilder.buildAsync(mockCallback);

        verify(mockCallback).onMessageBuildException(any(MessagingException.class));
        verifyNoMoreInteractions(mockCallback);
    }

    @Test
    public void buildSign__withDetachedSignatureInResult__shouldSucceed() throws MessagingException {
        cryptoStatusBuilder.setCryptoMode(CryptoMode.SIGN_ONLY);
        sMimeMessageBuilder.setCryptoStatus(cryptoStatusBuilder.build());

        ArgumentCaptor<Intent> capturedApiIntent = ArgumentCaptor.forClass(Intent.class);

        Intent returnIntent = new Intent();
        returnIntent.putExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_SUCCESS);
        returnIntent.putExtra(SMimeApi.RESULT_DETACHED_SIGNATURE, new byte[] { 1, 2, 3 });
        when(sMimeApi.executeApi(capturedApiIntent.capture(), any(SMimeDataSource.class), any(OutputStream.class)))
                .thenReturn(returnIntent);

        Callback mockCallback = mock(Callback.class);
        sMimeMessageBuilder.buildAsync(mockCallback);

        Intent expectedIntent = new Intent(SMimeApi.ACTION_DETACHED_SIGN);
        expectedIntent.putExtra(SMimeApi.EXTRA_SIGN_CERTIFICATE_ID, TEST_SIGN_CERTIFICATE_ID);
        expectedIntent.putExtra(SMimeApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        assertIntentEqualsActionAndExtras(expectedIntent, capturedApiIntent.getValue());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mockCallback).onMessageBuildSuccess(captor.capture(), eq(false));
        verifyNoMoreInteractions(mockCallback);

        MimeMessage message = captor.getValue();
        Assert.assertEquals("message must be multipart/signed", "multipart/signed", message.getMimeType());

        MimeMultipart multipart = (MimeMultipart) message.getBody();
        Assert.assertEquals("multipart/signed must consist of two parts", 2, multipart.getCount());

        BodyPart contentBodyPart = multipart.getBodyPart(0);
        Assert.assertEquals("first part must have content type text/plain",
                "text/plain", MimeUtility.getHeaderParameter(contentBodyPart.getContentType(), null));
        Assert.assertTrue("signed message body must be TextBody", contentBodyPart.getBody() instanceof TextBody);
        Assert.assertEquals(MimeUtil.ENC_QUOTED_PRINTABLE, ((TextBody) contentBodyPart.getBody()).getEncoding());
        assertContentOfBodyPartEquals("content must match the message text", contentBodyPart, TEST_MESSAGE_TEXT);

        BodyPart signatureBodyPart = multipart.getBodyPart(1);
        Assert.assertEquals("second part must be pgp signature",
                "application/pgp-signature", signatureBodyPart.getContentType());
        assertContentOfBodyPartEquals("content must match the supplied detached signature",
                signatureBodyPart, new byte[] { 1, 2, 3 });
    }

    @Test
    public void buildSign__withUserInteractionResult__shouldReturnUserInteraction() throws MessagingException {
        cryptoStatusBuilder.setCryptoMode(CryptoMode.SIGN_ONLY);
        sMimeMessageBuilder.setCryptoStatus(cryptoStatusBuilder.build());

        Intent returnIntent = mock(Intent.class);
        when(returnIntent.getIntExtra(eq(SMimeApi.RESULT_CODE), anyInt()))
                .thenReturn(SMimeApi.RESULT_CODE_USER_INTERACTION_REQUIRED);
        final PendingIntent mockPendingIntent = mock(PendingIntent.class);
        when(returnIntent.getParcelableExtra(eq(SMimeApi.RESULT_INTENT)))
                .thenReturn(mockPendingIntent);

        when(sMimeApi.executeApi(any(Intent.class), any(SMimeDataSource.class), any(OutputStream.class)))
                .thenReturn(returnIntent);

        Callback mockCallback = mock(Callback.class);
        sMimeMessageBuilder.buildAsync(mockCallback);

        ArgumentCaptor<PendingIntent> captor = ArgumentCaptor.forClass(PendingIntent.class);
        verify(mockCallback).onMessageBuildReturnPendingIntent(captor.capture(), anyInt());
        verifyNoMoreInteractions(mockCallback);

        PendingIntent pendingIntent = captor.getValue();
        Assert.assertSame(pendingIntent, mockPendingIntent);
    }

    @Test
    public void buildSign__withReturnAfterUserInteraction__shouldSucceed() throws MessagingException {
        cryptoStatusBuilder.setCryptoMode(CryptoMode.SIGN_ONLY);
        sMimeMessageBuilder.setCryptoStatus(cryptoStatusBuilder.build());

        int returnedRequestCode;
        {
            Intent returnIntent = spy(new Intent());
            returnIntent.putExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_USER_INTERACTION_REQUIRED);

            PendingIntent mockPendingIntent = mock(PendingIntent.class);
            when(returnIntent.getParcelableExtra(eq(SMimeApi.RESULT_INTENT)))
                    .thenReturn(mockPendingIntent);

            when(sMimeApi.executeApi(any(Intent.class), any(SMimeDataSource.class), any(OutputStream.class)))
                    .thenReturn(returnIntent);

            Callback mockCallback = mock(Callback.class);
            sMimeMessageBuilder.buildAsync(mockCallback);

            verify(returnIntent).getIntExtra(eq(SMimeApi.RESULT_CODE), anyInt());
            ArgumentCaptor<PendingIntent> piCaptor = ArgumentCaptor.forClass(PendingIntent.class);
            ArgumentCaptor<Integer> rcCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(mockCallback).onMessageBuildReturnPendingIntent(piCaptor.capture(), rcCaptor.capture());
            verifyNoMoreInteractions(mockCallback);

            returnedRequestCode = rcCaptor.getValue();
            Assert.assertSame(mockPendingIntent, piCaptor.getValue());
        }

        {
            Intent returnIntent = spy(new Intent());
            returnIntent.putExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_SUCCESS);

            Intent mockReturnIntent = mock(Intent.class);
            when(sMimeApi.executeApi(same(mockReturnIntent), any(SMimeDataSource.class), any(OutputStream.class)))
                    .thenReturn(returnIntent);

            Callback mockCallback = mock(Callback.class);
            sMimeMessageBuilder.onActivityResult(returnedRequestCode, Activity.RESULT_OK, mockReturnIntent, mockCallback);
            verify(sMimeApi).executeApi(same(mockReturnIntent), any(SMimeDataSource.class), any(OutputStream.class));
            verify(returnIntent).getIntExtra(eq(SMimeApi.RESULT_CODE), anyInt());
        }
    }

    @Test
    public void buildEncrypt__withoutRecipients__shouldThrow() throws MessagingException {
        cryptoStatusBuilder
                .setCryptoMode(CryptoMode.OPPORTUNISTIC)
                .setRecipients(new ArrayList<Recipient>());
        sMimeMessageBuilder.setCryptoStatus(cryptoStatusBuilder.build());

        Intent returnIntent = spy(new Intent());
        returnIntent.putExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_SUCCESS);
        when(sMimeApi.executeApi(any(Intent.class), any(SMimeDataSource.class), any(OutputStream.class)))
                .thenReturn(returnIntent);

        Callback mockCallback = mock(Callback.class);
        sMimeMessageBuilder.buildAsync(mockCallback);

        verify(mockCallback).onMessageBuildException(any(MessagingException.class));
        verifyNoMoreInteractions(mockCallback);
    }

    @Test
    public void buildEncrypt__shouldSucceed() throws MessagingException {
        ComposeCryptoStatus cryptoStatus = cryptoStatusBuilder
                .setCryptoMode(CryptoMode.PRIVATE)
                .setRecipients(Collections.singletonList(new Recipient("test", "test@example.org", "labru", -1, "key")))
                .build();
        sMimeMessageBuilder.setCryptoStatus(cryptoStatus);
        ArgumentCaptor<Intent> capturedApiIntent = ArgumentCaptor.forClass(Intent.class);
        Intent returnIntent = new Intent();returnIntent.putExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_SUCCESS);

        when(sMimeApi.executeApi(capturedApiIntent.capture(),
                any(SMimeDataSource.class), any(OutputStream.class))).thenReturn(returnIntent);

        Callback mockCallback = mock(Callback.class);
        sMimeMessageBuilder.buildAsync(mockCallback);

        Intent expectedApiIntent = new Intent(SMimeApi.ACTION_SIGN_AND_ENCRYPT);
        expectedApiIntent.putExtra(SMimeApi.EXTRA_SIGN_CERTIFICATE_ID, TEST_SIGN_CERTIFICATE_ID);
        expectedApiIntent.putExtra(SMimeApi.EXTRA_CERTIFICATE_IDS, new long[] {TEST_SELF_ENCRYPT_CERTIFICATE_ID});
        expectedApiIntent.putExtra(SMimeApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        expectedApiIntent.putExtra(SMimeApi.EXTRA_ENCRYPT_OPPORTUNISTIC, false);
        expectedApiIntent.putExtra(SMimeApi.EXTRA_USER_IDS, cryptoStatus.getRecipientAddresses());
        assertIntentEqualsActionAndExtras(expectedApiIntent, capturedApiIntent.getValue());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mockCallback).onMessageBuildSuccess(captor.capture(), eq(false));
        verifyNoMoreInteractions(mockCallback);

        MimeMessage message = captor.getValue();

        Assert.assertEquals("message must be multipart/encrypted", "multipart/encrypted", message.getMimeType());

        MimeMultipart multipart = (MimeMultipart) message.getBody();
        Assert.assertEquals("multipart/encrypted must consist of two parts", 2, multipart.getCount());

        BodyPart dummyBodyPart = multipart.getBodyPart(0);
        Assert.assertEquals("first part must be pgp encrypted dummy part",
                "application/pgp-encrypted", dummyBodyPart.getContentType());
        assertContentOfBodyPartEquals("content must match the supplied detached signature",
                dummyBodyPart, "Version: 1");

        BodyPart encryptedBodyPart = multipart.getBodyPart(1);
        Assert.assertEquals("second part must be octet-stream of encrypted data",
                "application/octet-stream", encryptedBodyPart.getContentType());
        Assert.assertTrue("message body must be BinaryTempFileBody",
                encryptedBodyPart.getBody() instanceof BinaryTempFileBody);
        Assert.assertEquals(MimeUtil.ENC_7BIT, ((BinaryTempFileBody) encryptedBodyPart.getBody()).getEncoding());
    }

    private ComposeCryptoStatusBuilder createDefaultComposeCryptoStatusBuilder() {
        return new ComposeCryptoStatusBuilder()
                .setEnablePgpInline(false)
                .setSigningCertificateId(TEST_SIGN_CERTIFICATE_ID)
                .setSelfEncryptCertificateId(TEST_SELF_ENCRYPT_CERTIFICATE_ID)
                .setRecipients(new ArrayList<Recipient>())
                .setCryptoProviderState(CryptoProviderState.OK);
    }

    private static SMimeMessageBuilder createDefaultSMimeMessageBuilder(SMimeApi sMimeApi) {
        SMimeMessageBuilder builder = new SMimeMessageBuilder(
                RuntimeEnvironment.application, MessageIdGenerator.getInstance(), BoundaryGenerator.getInstance());
        builder.setSMimeApi(sMimeApi);

        Identity identity = new Identity();
        identity.setName("tester");
        identity.setEmail("test@example.org");
        identity.setDescription("test identity");
        identity.setSignatureUse(false);

        builder.setSubject("subject")
                .setSentDate(new Date())
                .setHideTimeZone(false)
                .setTo(new ArrayList<Address>())
                .setCc(new ArrayList<Address>())
                .setBcc(new ArrayList<Address>())
                .setInReplyTo("inreplyto")
                .setReferences("references")
                .setRequestReadReceipt(false)
                .setIdentity(identity)
                .setMessageFormat(SimpleMessageFormat.TEXT)
                .setText(TEST_MESSAGE_TEXT)
                .setAttachments(new ArrayList<Attachment>())
                .setSignature("signature")
                .setQuoteStyle(QuoteStyle.PREFIX)
                .setQuotedTextMode(QuotedTextMode.NONE)
                .setQuotedText("quoted text")
                .setQuotedHtmlContent(new InsertableHtmlContent())
                .setReplyAfterQuote(false)
                .setSignatureBeforeQuotedText(false)
                .setIdentityChanged(false)
                .setSignatureChanged(false)
                .setCursorPosition(0)
                .setMessageReference(null)
                .setDraft(false);

        return builder;
    }

    private static void assertContentOfBodyPartEquals(String reason, BodyPart signatureBodyPart, byte[] expected) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            signatureBodyPart.getBody().writeTo(bos);
            Assert.assertArrayEquals(reason, expected, bos.toByteArray());
        } catch (IOException | MessagingException e) {
            Assert.fail();
        }
    }

    private static void assertContentOfBodyPartEquals(String reason, BodyPart signatureBodyPart, String expected) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream inputStream = MimeUtility.decodeBody(signatureBodyPart.getBody());
            IOUtils.copy(inputStream, bos);
            Assert.assertEquals(reason, expected, new String(bos.toByteArray()));
        } catch (IOException | MessagingException e) {
            Assert.fail();
        }
    }

    private static void assertIntentEqualsActionAndExtras(Intent expected, Intent actual) {
        Assert.assertEquals(expected.getAction(), actual.getAction());

        Bundle expectedExtras = expected.getExtras();
        Bundle intentExtras = actual.getExtras();

        if (expectedExtras.size() != intentExtras.size()) {
            Assert.assertEquals(expectedExtras.size(), intentExtras.size());
        }

        for (String key : expectedExtras.keySet()) {
            Object intentExtra = intentExtras.get(key);
            Object expectedExtra = expectedExtras.get(key);
            if (intentExtra == null) {
                if (expectedExtra == null) {
                    continue;
                }
                Assert.fail("found null for an expected non-null extra: " + key);
            }
            if (intentExtra instanceof long[]) {
                if (!Arrays.equals((long[]) intentExtra, (long[]) expectedExtra)) {
                    Assert.assertArrayEquals((long[]) expectedExtra, (long[]) intentExtra);
                }
            } else {
                if (!intentExtra.equals(expectedExtra)) {
                    Assert.assertEquals(expectedExtra, intentExtra);
                }
            }
        }
    }
}
