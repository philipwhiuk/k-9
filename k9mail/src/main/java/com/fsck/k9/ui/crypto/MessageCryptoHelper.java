package com.fsck.k9.ui.crypto;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.fsck.k9.K9;
import com.fsck.k9.crypto.CryptoMethod;
import com.fsck.k9.crypto.MessageDecryptVerifier;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MessageExtractor;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.SizeAware;
import com.fsck.k9.mail.internet.TextBody;
import com.fsck.k9.mailstore.CryptoResultAnnotation;
import com.fsck.k9.mailstore.CryptoResultAnnotation.CryptoError;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.MessageHelper;
import com.fsck.k9.mailstore.MimePartStreamParser;
import com.fsck.k9.mailstore.util.FileFactory;
import com.fsck.k9.provider.DecryptedFileProvider;
import org.apache.commons.io.IOUtils;
import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpDecryptionResult;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.OpenPgpDataSink;
import org.openintents.openpgp.util.OpenPgpApi.OpenPgpDataSource;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpUtils;
import org.openintents.smime.ISMimeService;
import org.openintents.smime.SMimeDecryptionResult;
import org.openintents.smime.SMimeError;
import org.openintents.smime.SMimeSignatureResult;
import org.openintents.smime.util.SMimeApi;
import org.openintents.smime.util.SMimeApi.SMimeDataSink;
import org.openintents.smime.util.SMimeApi.SMimeDataSource;
import org.openintents.smime.util.SMimeServiceConnection;


public class MessageCryptoHelper {
    private static final int INVALID_OPENPGP_RESULT_CODE = -1;
    private static final int INVALID_SMIME_RESULT_CODE = -1;
    private static final MimeBodyPart NO_REPLACEMENT_PART = null;
    public static final int REQUEST_CODE_USER_INTERACTION = 124;
    public static final int PROGRESS_SIZE_THRESHOLD = 4096;


    private final Context context;
    private final String openPgpProviderPackage;
    private final String sMimeProviderPackage;
    private final Object callbackLock = new Object();

    @Nullable
    private MessageCryptoCallback callback;

    private LocalMessage currentMessage;
    private OpenPgpDecryptionResult cachedDecryptionResult;
    private MessageCryptoAnnotations queuedResult;
    private PendingIntent queuedPendingIntent;


    private MessageCryptoAnnotations messageAnnotations;
    private Deque<CryptoPart> partsToDecryptOrVerify = new ArrayDeque<>();
    private CryptoPart currentCryptoPart;
    private Intent currentCryptoResult;
    private Intent userInteractionResultIntent;
    private boolean secondPassStarted;
    private OpenPgpApi.CancelableBackgroundOperation cancelablePgpBackgroundOperation;
    private SMimeApi.CancelableBackgroundOperation cancelableSMimeBackgroundOperation;
    private boolean isCancelled;

    private OpenPgpApi openPgpApi;
    private OpenPgpServiceConnection openPgpServiceConnection;

    private SMimeApi sMimeApi;
    private SMimeServiceConnection sMimeServiceConnection;


    public MessageCryptoHelper(Context context, String openPgpProviderPackage, String sMimeProviderPackage) {
        this.context = context.getApplicationContext();
        this.openPgpProviderPackage = openPgpProviderPackage;
        this.sMimeProviderPackage = sMimeProviderPackage;
    }

    public void asyncStartOrResumeProcessingMessage(LocalMessage message, MessageCryptoCallback callback,
            OpenPgpDecryptionResult cachedDecryptionResult) {
        if (this.currentMessage != null) {
            reattachCallback(message, callback);
            return;
        }

        this.messageAnnotations = new MessageCryptoAnnotations();
        this.currentMessage = message;
        this.cachedDecryptionResult = cachedDecryptionResult;
        this.callback = callback;

        runFirstPass();
    }

    private void runFirstPass() {
        List<Part> encryptedParts = MessageDecryptVerifier.findEncryptedParts(currentMessage);
        processFoundEncryptedParts(encryptedParts);

        decryptOrVerifyNextPart();
    }

    private void runSecondPass() {
        List<Part> signedParts = MessageDecryptVerifier.findSignedParts(currentMessage, messageAnnotations);
        processFoundSignedParts(signedParts);

        List<Part> inlineParts = MessageDecryptVerifier.findPgpInlineParts(currentMessage);
        addFoundInlinePgpParts(inlineParts);

        decryptOrVerifyNextPart();
    }

    private void processFoundEncryptedParts(List<Part> foundParts) {
        for (Part part : foundParts) {
            if (!MessageHelper.isCompletePartAvailable(part)) {
                addPgpErrorAnnotation(part, CryptoError.OPENPGP_ENCRYPTED_BUT_INCOMPLETE, MessageHelper.createEmptyPart());
                continue;
            }
            if (MessageDecryptVerifier.isPgpMimeEncryptedOrSignedPart(part)) {
                CryptoPart cryptoPart = new CryptoPart(CryptoPartType.PGP_ENCRYPTED, part);
                partsToDecryptOrVerify.add(cryptoPart);
                continue;
            }
            if (MessageDecryptVerifier.isSMimeEncryptedOrSignedPart(part)) {
                CryptoPart cryptoPart = new CryptoPart(CryptoPartType.SMIME_ENCRYPTED, part);
                partsToDecryptOrVerify.add(cryptoPart);
                continue;
            }
            addErrorAnnotation(part, CryptoError.ENCRYPTED_BUT_UNSUPPORTED, MessageHelper.createEmptyPart());
        }
    }

    private void processFoundSignedParts(List<Part> foundParts) {
        for (Part part : foundParts) {
            if (!MessageHelper.isCompletePartAvailable(part)) {
                MimeBodyPart replacementPart = getMultipartSignedContentPartIfAvailable(part);
                addErrorAnnotation(part, CryptoError.OPENPGP_SIGNED_BUT_INCOMPLETE, replacementPart);
                continue;
            }
            if (MessageDecryptVerifier.isPgpMimeEncryptedOrSignedPart(part)) {
                CryptoPart cryptoPart = new CryptoPart(CryptoPartType.PGP_SIGNED, part);
                partsToDecryptOrVerify.add(cryptoPart);
                continue;
            }
            if (MessageDecryptVerifier.isSMimeEncryptedOrSignedPart(part)) {
                CryptoPart cryptoPart = new CryptoPart(CryptoPartType.SMIME_SIGNED, part);
                partsToDecryptOrVerify.add(cryptoPart);
                continue;
            }

            MimeBodyPart replacementPart = getMultipartSignedContentPartIfAvailable(part);
            addErrorAnnotation(part, CryptoError.SIGNED_BUT_UNSUPPORTED, replacementPart);
        }
    }

    private void addPgpErrorAnnotation(Part part, CryptoError error, MimeBodyPart replacementPart) {
        CryptoResultAnnotation annotation = CryptoResultAnnotation.createOpenPgpErrorAnnotation(
                error, replacementPart);
        messageAnnotations.put(part, annotation);
    }

    private void addSMimeErrorAnnotation(Part part, CryptoError error, MimeBodyPart replacementPart) {
        CryptoResultAnnotation annotation = CryptoResultAnnotation.createSMimeErrorAnnotation(
                error, replacementPart);
        messageAnnotations.put(part, annotation);
    }

    private void addErrorAnnotation(Part part, CryptoError error, MimeBodyPart replacementPart) {
        CryptoResultAnnotation annotation = CryptoResultAnnotation.createErrorAnnotation(
                error, replacementPart);
        messageAnnotations.put(part, annotation);
    }

    private void addFoundInlinePgpParts(List<Part> foundParts) {
        for (Part part : foundParts) {
            if (!currentMessage.getFlags().contains(Flag.X_DOWNLOADED_FULL)) {
                if (MessageDecryptVerifier.isPartPgpInlineEncrypted(part)) {
                    addPgpErrorAnnotation(part, CryptoError.OPENPGP_ENCRYPTED_BUT_INCOMPLETE, NO_REPLACEMENT_PART);
                } else {
                    MimeBodyPart replacementPart = extractClearsignedTextReplacementPart(part);
                    addPgpErrorAnnotation(part, CryptoError.OPENPGP_SIGNED_BUT_INCOMPLETE, replacementPart);
                }
                continue;
            }

            CryptoPart cryptoPart = new CryptoPart(CryptoPartType.PGP_INLINE, part);
            partsToDecryptOrVerify.add(cryptoPart);
        }
    }

    private void decryptOrVerifyNextPart() {
        if (isCancelled) {
            return;
        }

        if (partsToDecryptOrVerify.isEmpty()) {
            runSecondPassOrReturnResultToFragment();
            return;
        }

        CryptoPart cryptoPart = partsToDecryptOrVerify.peekFirst();
        startDecryptingOrVerifyingPart(cryptoPart);
    }

    private void startDecryptingOrVerifyingPart(CryptoPart cryptoPart) {
        if (CryptoMethod.PGP_MIME.equals(cryptoPart.type.method)) {
            if(!isBoundToPgpProviderService()
                    && canBindToPgpProviderService()) {
                connectToPgpProviderService();
            } else if(isBoundToPgpProviderService()) {
                decryptOrVerifyPart(cryptoPart);
            } else {
                currentCryptoPart = cryptoPart;
                CryptoResultAnnotation errorPart;
                if(cryptoPart.type.isEncrypted())
                    errorPart = CryptoResultAnnotation.createOpenPgpEncryptedUnavailableAnnotation();
                else
                    errorPart = CryptoResultAnnotation.createOpenPgpSignedUnavailableAnnotation();
                addCryptoResultAnnotationToMessage(errorPart);
                onCryptoFinished();
            }
        } else if (CryptoMethod.SMIME.equals(cryptoPart.type.method)) {
            if (!isBoundToSMimeProviderService()
                    && canBindToSMimeProviderService()) {
                connectToSMimeProviderService();
            } else if (isBoundToSMimeProviderService()) {
                decryptOrVerifyPart(cryptoPart);
            } else {
                currentCryptoPart = cryptoPart;
                CryptoResultAnnotation errorPart;
                if(cryptoPart.type.isEncrypted())
                    errorPart = CryptoResultAnnotation.createSMimeEncryptedUnavailableAnnotation();
                else
                    errorPart = CryptoResultAnnotation.createSMimeSignedUnavailableAnnotation();
                addCryptoResultAnnotationToMessage(errorPart);
                onCryptoFinished();
            }
        } else {
            throw new IllegalArgumentException(
                    "Unhandled crypto method: " + cryptoPart.type.method);
        }
    }

    private boolean isBoundToPgpProviderService() {
        return openPgpApi != null;
    }

    private boolean canBindToPgpProviderService() { return openPgpProviderPackage != null; }

    private void connectToPgpProviderService() {
        openPgpServiceConnection = new OpenPgpServiceConnection(context, openPgpProviderPackage,
                new OpenPgpServiceConnection.OnBound() {
                    @Override
                    public void onBound(IOpenPgpService2 service) {
                        openPgpApi = new OpenPgpApi(context, service);

                        decryptOrVerifyNextPart();
                    }

                    @Override
                    public void onError(Exception e) {
                        // TODO actually handle (hand to ui, offer retry?)
                        Log.e(K9.LOG_TAG, "Couldn't connect to OpenPgpService", e);
                    }
                });
        openPgpServiceConnection.bindToService();
    }

    private boolean isBoundToSMimeProviderService() {
        return sMimeApi != null;
    }

    private boolean canBindToSMimeProviderService() { return sMimeProviderPackage != null; }

    private void connectToSMimeProviderService() {
        sMimeServiceConnection = new SMimeServiceConnection(context, sMimeProviderPackage,
                new SMimeServiceConnection.OnBound() {
                    @Override
                    public void onBound(ISMimeService service) {
                        sMimeApi = new SMimeApi(context, service);

                        decryptOrVerifyNextPart();
                    }

                    @Override
                    public void onError(Exception e) {
                        // TODO actually handle (hand to ui, offer retry?)
                        Log.e(K9.LOG_TAG, "Couldn't connect to SMimeService", e);
                    }
                });
        sMimeServiceConnection.bindToService();
    }

    private void decryptOrVerifyPart(CryptoPart cryptoPart) {
        currentCryptoPart = cryptoPart;
        Intent decryptIntent = userInteractionResultIntent;
        userInteractionResultIntent = null;
        if (decryptIntent == null && cryptoPart.type.method.equals(CryptoMethod.PGP_MIME)) {
            decryptIntent = getPgpDecryptionIntent();
        }
        if (decryptIntent == null && cryptoPart.type.method.equals(CryptoMethod.SMIME)) {
            decryptIntent = getSMimeDecryptionIntent();
        }
        decryptVerify(decryptIntent);
    }

    @NonNull
    private Intent getPgpDecryptionIntent() {
        Intent decryptIntent = new Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        Address[] from = currentMessage.getFrom();
        if (from.length > 0) {
            decryptIntent.putExtra(OpenPgpApi.EXTRA_SENDER_ADDRESS, from[0].getAddress());
        }

        decryptIntent.putExtra(OpenPgpApi.EXTRA_DECRYPTION_RESULT, cachedDecryptionResult);

        return decryptIntent;
    }

    @NonNull
    private Intent getSMimeDecryptionIntent() {
        Intent decryptIntent = new Intent(SMimeApi.ACTION_DECRYPT_VERIFY);

        Address[] from = currentMessage.getFrom();
        if (from.length > 0) {
            decryptIntent.putExtra(SMimeApi.EXTRA_SENDER_ADDRESS, from[0].getAddress());
        }

        decryptIntent.putExtra(SMimeApi.EXTRA_DECRYPTION_RESULT, cachedDecryptionResult);

        return decryptIntent;
    }

    private void decryptVerify(Intent intent) {
        try {
            CryptoPartType cryptoPartType = currentCryptoPart.type;
            switch (cryptoPartType) {
                case PGP_SIGNED: {
                    callAsyncDetachedPgpVerify(intent);
                    return;
                }
                case PGP_ENCRYPTED: {
                    callAsyncPgpDecrypt(intent);
                    return;
                }
                case PGP_INLINE: {
                    callAsyncPgpInlineOperation(intent);
                    return;
                }
                case SMIME_SIGNED: {
                    callAsyncDetachedSMimeVerify(intent);
                    return;
                }
                case SMIME_ENCRYPTED: {
                    callAsyncSMimeDecrypt(intent);
                    return;
                }
            }

            throw new IllegalStateException("Unknown crypto part type: " + cryptoPartType);
        } catch (IOException e) {
            Log.e(K9.LOG_TAG, "IOException", e);
        } catch (MessagingException e) {
            Log.e(K9.LOG_TAG, "MessagingException", e);
        }
    }

    private void callAsyncPgpInlineOperation(Intent intent) throws IOException {
        OpenPgpDataSource dataSource = getDataSourceForPgpEncryptedOrInlineData();
        OpenPgpDataSink<MimeBodyPart> dataSink = getDataSinkForDecryptedInlineData();

        cancelablePgpBackgroundOperation = openPgpApi.executeApiAsync(intent, dataSource, dataSink,
                new OpenPgpApi.IOpenPgpSinkResultCallback<MimeBodyPart>() {
            @Override
            public void onProgress(int current, int max) {
                Log.d(K9.LOG_TAG, "received progress status: " + current + " / " + max);
                callbackProgress(current, max);
            }

            @Override
            public void onReturn(Intent result, MimeBodyPart bodyPart) {
                cancelablePgpBackgroundOperation = null;
                currentCryptoResult = result;
                onPgpCryptoOperationReturned(bodyPart);
            }
        });
    }

    public void cancelIfRunning() {
        detachCallback();
        isCancelled = true;
        if (cancelablePgpBackgroundOperation != null) {
            cancelablePgpBackgroundOperation.cancelOperation();
        }
        if (cancelableSMimeBackgroundOperation != null) {
            cancelableSMimeBackgroundOperation.cancelOperation();
        }
    }

    private OpenPgpDataSink<MimeBodyPart> getDataSinkForDecryptedInlineData() {
        return new OpenPgpDataSink<MimeBodyPart>() {
            @Override
            public MimeBodyPart processData(InputStream is) throws IOException {
                try {
                    ByteArrayOutputStream decryptedByteOutputStream = new ByteArrayOutputStream();
                    IOUtils.copy(is, decryptedByteOutputStream);
                    TextBody body = new TextBody(new String(decryptedByteOutputStream.toByteArray()));
                    return new MimeBodyPart(body, "text/plain");
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "MessagingException", e);
                }

                return null;
            }
        };
    }

    private void callAsyncPgpDecrypt(Intent intent) throws IOException {
        OpenPgpDataSource dataSource = getDataSourceForPgpEncryptedOrInlineData();
        OpenPgpDataSink<MimeBodyPart> openPgpDataSink = getDataSinkForPgpDecryptedData();

        cancelablePgpBackgroundOperation = openPgpApi.executeApiAsync(intent, dataSource, openPgpDataSink,
                new OpenPgpApi.IOpenPgpSinkResultCallback<MimeBodyPart>() {
            @Override
            public void onReturn(Intent result, MimeBodyPart decryptedPart) {
                cancelablePgpBackgroundOperation = null;
                currentCryptoResult = result;
                onPgpCryptoOperationReturned(decryptedPart);
            }

            @Override
            public void onProgress(int current, int max) {
                Log.d(K9.LOG_TAG, "received progress status: " + current + " / " + max);
                callbackProgress(current, max);
            }
        });
    }

    private void callAsyncDetachedPgpVerify(Intent intent) throws IOException, MessagingException {
        OpenPgpDataSource dataSource = getDataSourceForPgpSignedData(currentCryptoPart.part);

        byte[] signatureData = MessageDecryptVerifier.getSignatureData(currentCryptoPart.part);
        intent.putExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE, signatureData);

        openPgpApi.executeApiAsync(intent, dataSource, new OpenPgpApi.IOpenPgpSinkResultCallback<Void>() {
            @Override
            public void onReturn(Intent result, Void dummy) {
                cancelablePgpBackgroundOperation = null;
                currentCryptoResult = result;
                onPgpCryptoOperationReturned(null);
            }

            @Override
            public void onProgress(int current, int max) {
                Log.d(K9.LOG_TAG, "received progress status: " + current + " / " + max);
                callbackProgress(current, max);
            }
        });
    }

    private void callAsyncDetachedSMimeVerify(Intent intent) throws IOException, MessagingException {
        SMimeApi.SMimeDataSource dataSource = getDataSourceForSMimeSignedData(currentCryptoPart.part);

        byte[] signatureData = MessageDecryptVerifier.getSignatureData(currentCryptoPart.part);
        intent.putExtra(SMimeApi.EXTRA_DETACHED_SIGNATURE, signatureData);

        sMimeApi.executeApiAsync(intent, dataSource, new SMimeApi.ISMimeSinkResultCallback<Void>() {
            @Override
            public void onReturn(Intent result, Void dummy) {
                cancelablePgpBackgroundOperation = null;
                currentCryptoResult = result;
                onPgpCryptoOperationReturned(null);
            }

            @Override
            public void onProgress(int current, int max) {
                Log.d(K9.LOG_TAG, "received progress status: " + current + " / " + max);
                callbackProgress(current, max);
            }
        });
    }

    private void callAsyncSMimeDecrypt(Intent intent) throws IOException {
        SMimeApi.SMimeDataSource dataSource = getDataSourceForSMimeEncryptedData();
        SMimeDataSink<MimeBodyPart> sMimeDataSink = getDataSinkForSMimeDecryptedData();

        cancelableSMimeBackgroundOperation = sMimeApi.executeApiAsync(intent, dataSource, sMimeDataSink,
                new SMimeApi.ISMimeSinkResultCallback<MimeBodyPart>() {
                    @Override
                    public void onReturn(Intent result, MimeBodyPart decryptedPart) {
                        cancelableSMimeBackgroundOperation = null;
                        currentCryptoResult = result;
                        onSMimeCryptoOperationReturned(decryptedPart);
                    }

                    @Override
                    public void onProgress(int current, int max) {
                        Log.d(K9.LOG_TAG, "received progress status: " + current + " / " + max);
                        callbackProgress(current, max);
                    }
                });
    }



    private OpenPgpDataSource getDataSourceForPgpSignedData(final Part signedPart) throws IOException {
        return new OpenPgpDataSource() {
            @Override
            public void writeTo(OutputStream os) throws IOException {
                try {
                    Multipart multipartSignedMultipart = (Multipart) signedPart.getBody();
                    BodyPart signatureBodyPart = multipartSignedMultipart.getBodyPart(0);
                    Log.d(K9.LOG_TAG, "signed data type: " + signatureBodyPart.getMimeType());
                    signatureBodyPart.writeTo(os);
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "Exception while writing message to crypto provider", e);
                }
            }
        };
    }

    private OpenPgpDataSource getDataSourceForPgpEncryptedOrInlineData() throws IOException {
        return new OpenPgpApi.OpenPgpDataSource() {
            @Override
            public Long getSizeForProgress() {
                Part part = currentCryptoPart.part;
                CryptoPartType cryptoPartType = currentCryptoPart.type;
                Body body;
                if (cryptoPartType == CryptoPartType.PGP_ENCRYPTED) {
                    Multipart multipartEncryptedMultipart = (Multipart) part.getBody();
                    BodyPart encryptionPayloadPart = multipartEncryptedMultipart.getBodyPart(1);
                    body = encryptionPayloadPart.getBody();
                } else if (cryptoPartType == CryptoPartType.PGP_INLINE) {
                    body = part.getBody();
                } else {
                    throw new IllegalStateException("part to stream must be encrypted or inline!");
                }
                if (body instanceof SizeAware) {
                    long bodySize = ((SizeAware) body).getSize();
                    if (bodySize > PROGRESS_SIZE_THRESHOLD) {
                        return bodySize;
                    }
                }
                return null;
            }

            @Override
            @WorkerThread
            public void writeTo(OutputStream os) throws IOException {
                try {
                    Part part = currentCryptoPart.part;
                    CryptoPartType cryptoPartType = currentCryptoPart.type;
                    if (cryptoPartType == CryptoPartType.PGP_ENCRYPTED) {
                        Multipart multipartEncryptedMultipart = (Multipart) part.getBody();
                        BodyPart encryptionPayloadPart = multipartEncryptedMultipart.getBodyPart(1);
                        Body encryptionPayloadBody = encryptionPayloadPart.getBody();
                        encryptionPayloadBody.writeTo(os);
                    } else if (cryptoPartType == CryptoPartType.PGP_INLINE) {
                        String text = MessageExtractor.getTextFromPart(part);
                        os.write(text.getBytes());
                    } else {
                        throw new IllegalStateException("part to stream must be encrypted or inline!");
                    }
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "MessagingException while writing message to crypto provider", e);
                }
            }
        };
    }

    private OpenPgpDataSink<MimeBodyPart> getDataSinkForPgpDecryptedData() throws IOException {
        return new OpenPgpDataSink<MimeBodyPart>() {
            @Override
            @WorkerThread
            public MimeBodyPart processData(InputStream is) throws IOException {
                try {
                    FileFactory fileFactory =
                            DecryptedFileProvider.getFileFactory(context);
                    return MimePartStreamParser.parse(fileFactory, is);
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "Something went wrong while parsing the decrypted MIME part", e);
                    //TODO: pass error to main thread and display error message to user
                    return null;
                }
            }
        };
    }

    private SMimeDataSource getDataSourceForSMimeSignedData(final Part signedPart) throws IOException {
        return new SMimeDataSource() {
            @Override
            public void writeTo(OutputStream os) throws IOException {
                try {
                    Multipart multipartSignedMultipart = (Multipart) signedPart.getBody();
                    BodyPart signatureBodyPart = multipartSignedMultipart.getBodyPart(0);
                    Log.d(K9.LOG_TAG, "signed data type: " + signatureBodyPart.getMimeType());
                    signatureBodyPart.writeTo(os);
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "Exception while writing message to crypto provider", e);
                }
            }
        };
    }

    private SMimeDataSource getDataSourceForSMimeEncryptedData() throws IOException {
        return new SMimeDataSource() {
            @Override
            public Long getSizeForProgress() {
                Part part = currentCryptoPart.part;
                CryptoPartType cryptoPartType = currentCryptoPart.type;
                Body body;
                if (cryptoPartType == CryptoPartType.SMIME_ENCRYPTED) {
                    Multipart multipartEncryptedMultipart = (Multipart) part.getBody();
                    BodyPart encryptionPayloadPart = multipartEncryptedMultipart.getBodyPart(1);
                    body = encryptionPayloadPart.getBody();
                } else {
                    throw new IllegalStateException("part to stream must be encrypted!");
                }
                if (body instanceof SizeAware) {
                    long bodySize = ((SizeAware) body).getSize();
                    if (bodySize > PROGRESS_SIZE_THRESHOLD) {
                        return bodySize;
                    }
                }
                return null;
            }

            @Override
            @WorkerThread
            public void writeTo(OutputStream os) throws IOException {
                try {
                    Part part = currentCryptoPart.part;
                    CryptoPartType cryptoPartType = currentCryptoPart.type;
                    if (cryptoPartType == CryptoPartType.PGP_ENCRYPTED) {
                        Multipart multipartEncryptedMultipart = (Multipart) part.getBody();
                        BodyPart encryptionPayloadPart = multipartEncryptedMultipart.getBodyPart(1);
                        Body encryptionPayloadBody = encryptionPayloadPart.getBody();
                        encryptionPayloadBody.writeTo(os);
                    } else if (cryptoPartType == CryptoPartType.PGP_INLINE) {
                        String text = MessageExtractor.getTextFromPart(part);
                        os.write(text.getBytes());
                    } else {
                        throw new IllegalStateException("part to stream must be encrypted or inline!");
                    }
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "MessagingException while writing message to crypto provider", e);
                }
            }
        };
    }

    private SMimeDataSink<MimeBodyPart> getDataSinkForSMimeDecryptedData() throws IOException {
        return new SMimeDataSink<MimeBodyPart>() {
            @Override
            @WorkerThread
            public MimeBodyPart processData(InputStream is) throws IOException {
                try {
                    FileFactory fileFactory =
                            DecryptedFileProvider.getFileFactory(context);
                    return MimePartStreamParser.parse(fileFactory, is);
                } catch (MessagingException e) {
                    Log.e(K9.LOG_TAG, "Something went wrong while parsing the decrypted MIME part", e);
                    //TODO: pass error to main thread and display error message to user
                    return null;
                }
            }
        };
    }


    private void onPgpCryptoOperationReturned(MimeBodyPart decryptedPart) {
        if (currentCryptoResult == null) {
            Log.e(K9.LOG_TAG, "Internal error: we should have a result here!");
            return;
        }

        try {
            handlePgpCryptoOperationResult(decryptedPart);
        } finally {
            currentCryptoResult = null;
        }
    }

    private void onSMimeCryptoOperationReturned(MimeBodyPart decryptedPart) {
        if (currentCryptoResult == null) {
            Log.e(K9.LOG_TAG, "Internal error: we should have a result here!");
            return;
        }

        try {
            handleSMimeCryptoOperationResult(decryptedPart);
        } finally {
            currentCryptoResult = null;
        }
    }

    private void handlePgpCryptoOperationResult(MimeBodyPart outputPart) {
        int resultCode = currentCryptoResult.getIntExtra(OpenPgpApi.RESULT_CODE, INVALID_OPENPGP_RESULT_CODE);
        if (K9.DEBUG) {
            Log.d(K9.LOG_TAG, "OpenPGP API decryptVerify result code: " + resultCode);
        }

        switch (resultCode) {
            case INVALID_OPENPGP_RESULT_CODE: {
                Log.e(K9.LOG_TAG, "Internal error: no result code!");
                break;
            }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                handlePgpUserInteractionRequest();
                break;
            }
            case OpenPgpApi.RESULT_CODE_ERROR: {
                handlePgpCryptoOperationError();
                break;
            }
            case OpenPgpApi.RESULT_CODE_SUCCESS: {
                handlePgpCryptoOperationSuccess(outputPart);
                break;
            }
        }
    }

    private void handleSMimeCryptoOperationResult(MimeBodyPart outputPart) {
        int resultCode = currentCryptoResult.getIntExtra(SMimeApi.RESULT_CODE, INVALID_SMIME_RESULT_CODE);
        if (K9.DEBUG) {
            Log.d(K9.LOG_TAG, "OpenPGP API decryptVerify result code: " + resultCode);
        }

        switch (resultCode) {
            case INVALID_SMIME_RESULT_CODE: {
                Log.e(K9.LOG_TAG, "Internal error: no result code!");
                break;
            }
            case SMimeApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                handleSMimeUserInteractionRequest();
                break;
            }
            case SMimeApi.RESULT_CODE_ERROR: {
                handleSMimeCryptoOperationError();
                break;
            }
            case SMimeApi.RESULT_CODE_SUCCESS: {
                handleSMimeCryptoOperationSuccess(outputPart);
                break;
            }
        }
    }

    private void handlePgpUserInteractionRequest() {
        PendingIntent pendingIntent = currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
        if (pendingIntent == null) {
            throw new AssertionError("Expecting PendingIntent on USER_INTERACTION_REQUIRED!");
        }

        callbackPendingIntent(pendingIntent);
    }

    private void handleSMimeUserInteractionRequest() {
        PendingIntent pendingIntent = currentCryptoResult.getParcelableExtra(SMimeApi.RESULT_INTENT);
        if (pendingIntent == null) {
            throw new AssertionError("Expecting PendingIntent on USER_INTERACTION_REQUIRED!");
        }

        callbackPendingIntent(pendingIntent);
    }

    private void handlePgpCryptoOperationError() {
        OpenPgpError error = currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
        if (K9.DEBUG) {
            Log.w(K9.LOG_TAG, "OpenPGP API error: " + error.getMessage());
        }

        onCryptoOperationFailed(error);
    }

    private void handleSMimeCryptoOperationError() {
        SMimeError error = currentCryptoResult.getParcelableExtra(SMimeApi.RESULT_ERROR);
        if (K9.DEBUG) {
            Log.w(K9.LOG_TAG, "SMime API error: " + error.getMessage());
        }

        onCryptoOperationFailed(error);
    }

    private void handlePgpCryptoOperationSuccess(MimeBodyPart outputPart) {
        OpenPgpDecryptionResult decryptionResult =
                currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_DECRYPTION);
        OpenPgpSignatureResult signatureResult =
                currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
        PendingIntent pendingIntent = currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_INTENT);

        CryptoResultAnnotation resultAnnotation = CryptoResultAnnotation.createOpenPgpResultAnnotation(
                decryptionResult, signatureResult, pendingIntent, outputPart);

        onCryptoOperationSuccess(resultAnnotation);
    }

    private void handleSMimeCryptoOperationSuccess(MimeBodyPart outputPart) {
        OpenPgpDecryptionResult decryptionResult =
                currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_DECRYPTION);
        OpenPgpSignatureResult signatureResult =
                currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
        PendingIntent pendingIntent = currentCryptoResult.getParcelableExtra(OpenPgpApi.RESULT_INTENT);

        CryptoResultAnnotation resultAnnotation = CryptoResultAnnotation.createOpenPgpResultAnnotation(
                decryptionResult, signatureResult, pendingIntent, outputPart);

        onCryptoOperationSuccess(resultAnnotation);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (isCancelled) {
            return;
        }

        if (requestCode != REQUEST_CODE_USER_INTERACTION) {
            throw new IllegalStateException("got an activity result that wasn't meant for us. this is a bug!");
        }
        if (resultCode == Activity.RESULT_OK) {
            userInteractionResultIntent = data;
            decryptOrVerifyNextPart();
        } else {
            onPgpCryptoOperationCanceled();
        }
    }

    private void onCryptoOperationSuccess(CryptoResultAnnotation resultAnnotation) {
        addCryptoResultAnnotationToMessage(resultAnnotation);
        onCryptoFinished();
    }

    private void propagateEncapsulatedSignedPart(CryptoResultAnnotation resultAnnotation, Part part) {
        Part encapsulatingPart = messageAnnotations.findKeyForAnnotationWithReplacementPart(part);
        CryptoResultAnnotation encapsulatingPartAnnotation = messageAnnotations.get(encapsulatingPart);

        if (encapsulatingPart != null && resultAnnotation.hasSignatureResult()) {
            CryptoResultAnnotation replacementAnnotation =
                    encapsulatingPartAnnotation.withEncapsulatedResult(resultAnnotation);
            messageAnnotations.put(encapsulatingPart, replacementAnnotation);
        }
    }

    private void onPgpCryptoOperationCanceled() {
        CryptoResultAnnotation errorPart = CryptoResultAnnotation.createOpenPgpCanceledAnnotation();
        addCryptoResultAnnotationToMessage(errorPart);
        onCryptoFinished();
    }

    private void onSMimeCryptoOperationCanceled() {
        CryptoResultAnnotation errorPart = CryptoResultAnnotation.createSMimeCanceledAnnotation();
        addCryptoResultAnnotationToMessage(errorPart);
        onCryptoFinished();
    }

    private void onCryptoOperationFailed(OpenPgpError error) {
        CryptoResultAnnotation annotation;
        if (currentCryptoPart.type == CryptoPartType.PGP_SIGNED) {
            MimeBodyPart replacementPart = getMultipartSignedContentPartIfAvailable(currentCryptoPart.part);
            annotation = CryptoResultAnnotation.createOpenPgpSignatureErrorAnnotation(error, replacementPart);
        } else {
            annotation = CryptoResultAnnotation.createOpenPgpEncryptionErrorAnnotation(error);
        }
        addCryptoResultAnnotationToMessage(annotation);
        onCryptoFinished();
    }

    private void onCryptoOperationFailed(SMimeError error) {
        CryptoResultAnnotation annotation;
        if (currentCryptoPart.type == CryptoPartType.SMIME_SIGNED) {
            MimeBodyPart replacementPart = getMultipartSignedContentPartIfAvailable(currentCryptoPart.part);
            annotation = CryptoResultAnnotation.createSMimeSignatureErrorAnnotation(error, replacementPart);
        } else {
            annotation = CryptoResultAnnotation.createSMimeEncryptionErrorAnnotation(error);
        }
        addCryptoResultAnnotationToMessage(annotation);
        onCryptoFinished();
    }

    private void addCryptoResultAnnotationToMessage(CryptoResultAnnotation resultAnnotation) {
        Part part = currentCryptoPart.part;
        messageAnnotations.put(part, resultAnnotation);

        propagateEncapsulatedSignedPart(resultAnnotation, part);
    }

    private void onCryptoFinished() {
        currentCryptoPart = null;
        partsToDecryptOrVerify.removeFirst();
        decryptOrVerifyNextPart();
    }

    private void runSecondPassOrReturnResultToFragment() {
        if (secondPassStarted) {
            callbackReturnResult();
            return;
        }
        secondPassStarted = true;
        runSecondPass();
    }

    private void cleanupAfterProcessingFinished() {
        partsToDecryptOrVerify = null;
        openPgpApi = null;
        if (openPgpServiceConnection != null) {
            openPgpServiceConnection.unbindFromService();
        }
        openPgpServiceConnection = null;
    }

    public void detachCallback() {
        synchronized (callbackLock) {
            callback = null;
        }
    }

    private void reattachCallback(LocalMessage message, MessageCryptoCallback callback) {
        if (!message.equals(currentMessage)) {
            throw new AssertionError("Callback may only be reattached for the same message!");
        }
        synchronized (callbackLock) {
            if (queuedResult != null) {
                Log.d(K9.LOG_TAG, "Returning cached result to reattached callback");
            }
            this.callback = callback;
            deliverResult();
        }
    }

    private void callbackPendingIntent(PendingIntent pendingIntent) {
        synchronized (callbackLock) {
            queuedPendingIntent = pendingIntent;
            deliverResult();
        }
    }

    private void callbackReturnResult() {
        synchronized (callbackLock) {
            cleanupAfterProcessingFinished();

            queuedResult = messageAnnotations;
            messageAnnotations = null;

            deliverResult();
        }
    }

    private void callbackProgress(int current, int max) {
        synchronized (callbackLock) {
            if (callback != null) {
                callback.onCryptoHelperProgress(current, max);
            }
        }
    }

    // This method must only be called inside a synchronized(callbackLock) block!
    private void deliverResult() {
        if (isCancelled) {
            return;
        }

        if (callback == null) {
            Log.d(K9.LOG_TAG, "Keeping crypto helper result in queue for later delivery");
            return;
        }
        if (queuedResult != null) {
            callback.onCryptoOperationsFinished(queuedResult);
        } else if (queuedPendingIntent != null) {
            callback.startPendingIntentForCryptoHelper(
                    queuedPendingIntent.getIntentSender(), REQUEST_CODE_USER_INTERACTION, null, 0, 0, 0);
            queuedPendingIntent = null;
        }
    }

    private static class CryptoPart {
        public final CryptoPartType type;
        public final Part part;

        CryptoPart(CryptoPartType type, Part part) {
            this.type = type;
            this.part = part;
        }
    }

    private enum CryptoPartType {
        PGP_INLINE(CryptoMethod.PGP_MIME, true),
        PGP_ENCRYPTED(CryptoMethod.PGP_MIME, true),
        PGP_SIGNED(CryptoMethod.PGP_MIME, false),
        SMIME_SIGNED(CryptoMethod.SMIME, false),
        SMIME_ENCRYPTED(CryptoMethod.SMIME, true);

        private final CryptoMethod method;
        private final boolean encrypted;

        CryptoPartType(CryptoMethod method, boolean encrypted) {
            this.method = method;
            this.encrypted = encrypted;
        }

        boolean isEncrypted() {
            return encrypted;
        }
    }

    @Nullable
    private static MimeBodyPart getMultipartSignedContentPartIfAvailable(Part part) {
        MimeBodyPart replacementPart = NO_REPLACEMENT_PART;
        Body body = part.getBody();
        if (body instanceof MimeMultipart) {
            MimeMultipart multipart = ((MimeMultipart) part.getBody());
            if (multipart.getCount() >= 1) {
                replacementPart = (MimeBodyPart) multipart.getBodyPart(0);
            }
        }
        return replacementPart;
    }

    private static MimeBodyPart extractClearsignedTextReplacementPart(Part part) {
        try {
            String clearsignedText = MessageExtractor.getTextFromPart(part);
            String replacementText = OpenPgpUtils.extractClearsignedMessage(clearsignedText);
            if (replacementText == null) {
                Log.e(K9.LOG_TAG, "failed to extract clearsigned text for replacement part");
                return NO_REPLACEMENT_PART;
            }
            return new MimeBodyPart(new TextBody(replacementText), "text/plain");
        } catch (MessagingException e) {
            Log.e(K9.LOG_TAG, "failed to create clearsigned text replacement part", e);
            return NO_REPLACEMENT_PART;
        }
    }

}
