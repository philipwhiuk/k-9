package com.fsck.k9.mail.store.imap;


import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import com.fsck.k9.mail.Authentication;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.CertificateValidationException;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.filter.Base64;
import com.fsck.k9.mail.oauth.OAuth2TokenProvider;
import com.fsck.k9.mail.oauth.XOAuth2ChallengeParser;
import timber.log.Timber;


public class ImapAuthenticator {

    List<ImapResponse> authenticate(ImapConnection imapConnection) throws MessagingException, IOException {
        switch (imapConnection.settings.getAuthType()) {
            case XOAUTH2:
                if (imapConnection.oauthTokenProvider == null) {
                    throw new MessagingException("No OAuthToken Provider available.");
                } else if (imapConnection.hasCapability(Capabilities.AUTH_XOAUTH2) && imapConnection.hasCapability(Capabilities.SASL_IR)) {
                    return authXoauth2withSASLIR(imapConnection);
                } else {
                    throw new MessagingException("Server doesn't support SASL XOAUTH2.");
                }
            case CRAM_MD5: {
                if (imapConnection.hasCapability(Capabilities.AUTH_CRAM_MD5)) {
                    return authCramMD5(imapConnection);
                } else {
                    throw new MessagingException("Server doesn't support encrypted passwords using CRAM-MD5.");
                }
            }
            case PLAIN: {
                if (imapConnection.hasCapability(Capabilities.AUTH_PLAIN)) {
                    return saslAuthPlainWithLoginFallback(imapConnection);
                } else if (!imapConnection.hasCapability(Capabilities.LOGINDISABLED)) {
                    return login(imapConnection);
                } else {
                    throw new MessagingException("Server doesn't support unencrypted passwords using AUTH=PLAIN " +
                            "and LOGIN is disabled.");
                }
            }
            case EXTERNAL: {
                if (imapConnection.hasCapability(Capabilities.AUTH_EXTERNAL)) {
                    return saslAuthExternal(imapConnection);
                } else {
                    // Provide notification to user of a problem authenticating using client certificates
                    throw new CertificateValidationException(CertificateValidationException.Reason.MissingCapability);
                }
            }
            default: {
                throw new MessagingException("Unhandled authentication method found in the server settings (bug).");
            }
        }
    }

    private List<ImapResponse> authXoauth2withSASLIR(ImapConnection imapConnection) throws IOException, MessagingException {
        imapConnection.retryXoauth2WithNewToken = true;
        try {
            return attemptXOAuth2(imapConnection);
        } catch (NegativeImapResponseException e) {
            //TODO: Check response code so we don't needlessly invalidate the token.
            imapConnection.oauthTokenProvider.invalidateToken(imapConnection.settings.getUsername());

            if (!imapConnection.retryXoauth2WithNewToken) {
                throw handlePermanentXoauth2Failure(e);
            } else {
                return handleTemporaryXoauth2Failure(imapConnection, e);
            }
        }
    }

    private AuthenticationFailedException handlePermanentXoauth2Failure(NegativeImapResponseException e) {
        Timber.v(e, "Permanent failure during XOAUTH2");
        return new AuthenticationFailedException(e.getMessage(), e);
    }

    private List<ImapResponse> handleTemporaryXoauth2Failure(ImapConnection imapConnection, NegativeImapResponseException e) throws IOException, MessagingException {
        //We got a response indicating a retry might suceed after token refresh
        //We could avoid this if we had a reasonable chance of knowing
        //if a token was invalid before use (e.g. due to expiry). But we don't
        //This is the intended behaviour per AccountManager

        Timber.v(e, "Temporary failure - retrying with new token");
        try {
            return attemptXOAuth2(imapConnection);
        } catch (NegativeImapResponseException e2) {
            //Okay, we failed on a new token.
            //Invalidate the token anyway but assume it's permanent.
            Timber.v(e, "Authentication exception for new token, permanent error assumed");
            imapConnection.oauthTokenProvider.invalidateToken(imapConnection.settings.getUsername());
            throw handlePermanentXoauth2Failure(e2);
        }
    }

    private List<ImapResponse> attemptXOAuth2(final ImapConnection imapConnection) throws MessagingException, IOException {
        String token = imapConnection.oauthTokenProvider.getToken(imapConnection.settings.getUsername(), OAuth2TokenProvider.OAUTH2_TIMEOUT);
        String authString = Authentication.computeXoauth(imapConnection.settings.getUsername(), token);
        String tag = imapConnection.sendSaslIrCommand(Commands.AUTHENTICATE_XOAUTH2, authString, true);

        return imapConnection.responseParser.readStatusResponse(tag, Commands.AUTHENTICATE_XOAUTH2, imapConnection.getLogId(),
                new UntaggedHandler() {
                    @Override
                    public void handleAsyncUntaggedResponse(ImapResponse response) throws IOException {
                        handleXOAuthUntaggedResponse(imapConnection, response);
                    }
                });
    }

    private void handleXOAuthUntaggedResponse(ImapConnection imapConnection, ImapResponse response) throws IOException {
        if (response.isString(0)) {
            imapConnection.retryXoauth2WithNewToken = XOAuth2ChallengeParser
                    .shouldRetry(response.getString(0), imapConnection.settings.getHost());
        }

        if (response.isContinuationRequested()) {
            imapConnection.outputStream.write("\r\n".getBytes());
            imapConnection.outputStream.flush();
        }
    }

    private List<ImapResponse> authCramMD5(ImapConnection imapConnection) throws MessagingException, IOException {
        String command = Commands.AUTHENTICATE_CRAM_MD5;
        String tag = imapConnection.sendCommand(command, false);

        ImapResponse response = imapConnection.readContinuationResponse(tag);
        if (response.size() != 1 || !(response.get(0) instanceof String)) {
            throw new MessagingException("Invalid Cram-MD5 nonce received");
        }

        byte[] b64Nonce = response.getString(0).getBytes();
        byte[] b64CRAM = Authentication.computeCramMd5Bytes(imapConnection.settings.getUsername(), imapConnection.settings.getPassword(), b64Nonce);

        imapConnection.outputStream.write(b64CRAM);
        imapConnection.outputStream.write('\r');
        imapConnection.outputStream.write('\n');
        imapConnection.outputStream.flush();

        try {
            return imapConnection.responseParser.readStatusResponse(tag, command, imapConnection.getLogId(), null);
        } catch (NegativeImapResponseException e) {
            throw handleAuthenticationFailure(imapConnection, e);
        }
    }

    private List<ImapResponse> saslAuthPlainWithLoginFallback(ImapConnection imapConnection) throws IOException, MessagingException {
        try {
            return saslAuthPlain(imapConnection);
        } catch (AuthenticationFailedException e) {
            if (!imapConnection.isConnected()) {
                throw e;
            }

            return login(imapConnection);
        }
    }

    private List<ImapResponse> saslAuthPlain(ImapConnection imapConnection) throws IOException, MessagingException {
        String command = Commands.AUTHENTICATE_PLAIN;
        String tag = imapConnection.sendCommand(command, false);

        imapConnection.readContinuationResponse(tag);

        String credentials = "\000" + imapConnection.settings.getUsername() + "\000" + imapConnection.settings.getPassword();
        byte[] encodedCredentials = Base64.encodeBase64(credentials.getBytes());

        imapConnection.outputStream.write(encodedCredentials);
        imapConnection.outputStream.write('\r');
        imapConnection.outputStream.write('\n');
        imapConnection.outputStream.flush();

        try {
            return imapConnection.responseParser.readStatusResponse(tag, command, imapConnection.getLogId(), null);
        } catch (NegativeImapResponseException e) {
            throw handleAuthenticationFailure(imapConnection, e);
        }
    }

    private List<ImapResponse> login(ImapConnection imapConnection) throws IOException, MessagingException {
        /*
         * Use quoted strings which permit spaces and quotes. (Using IMAP
         * string literals would be better, but some servers are broken
         * and don't parse them correctly.)
         */

        // escape double-quotes and backslash characters with a backslash
        Pattern p = Pattern.compile("[\\\\\"]");
        String replacement = "\\\\$0";
        String username = p.matcher(imapConnection.settings.getUsername()).replaceAll(replacement);
        String password = p.matcher(imapConnection.settings.getPassword()).replaceAll(replacement);

        try {
            String command = String.format(Commands.LOGIN + " \"%s\" \"%s\"", username, password);
            return imapConnection.executeSimpleCommand(command, true);
        } catch (NegativeImapResponseException e) {
            throw handleAuthenticationFailure(imapConnection, e);
        }
    }

    private List<ImapResponse> saslAuthExternal(ImapConnection imapConnection) throws IOException, MessagingException {
        try {
            String command = Commands.AUTHENTICATE_EXTERNAL + " " + Base64.encode(imapConnection.settings.getUsername());
            return imapConnection.executeSimpleCommand(command, false);
        } catch (NegativeImapResponseException e) {
            /*
             * Provide notification to the user of a problem authenticating
             * using client certificates. We don't use an
             * AuthenticationFailedException because that would trigger a
             * "Username or password incorrect" notification in
             * AccountSetupCheckSettings.
             */
            throw new CertificateValidationException(e.getMessage());
        }
    }

    private MessagingException handleAuthenticationFailure(ImapConnection imapConnection, NegativeImapResponseException e) {
        ImapResponse lastResponse = e.getLastResponse();
        String responseCode = ResponseCodeExtractor.getResponseCode(lastResponse);

        // If there's no response code we simply assume it was an authentication failure.
        if (responseCode == null || responseCode.equals(ResponseCodeExtractor.AUTHENTICATION_FAILED)) {
            if (e.wasByeResponseReceived()) {
                imapConnection.close();
            }

            return new AuthenticationFailedException(e.getMessage());
        } else {
            imapConnection.close();
            return e;
        }
    }

}
