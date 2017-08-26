package com.fsck.k9.mail.store.imap;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import android.net.NetworkInfo;
import android.os.Build.VERSION;

import com.fsck.k9.mail.BuildConfig;
import com.fsck.k9.mail.CertificateValidationException;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.NetworkType;
import com.fsck.k9.mail.filter.PeekableInputStream;
import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZOutputStream;
import javax.net.ssl.SSLException;
import timber.log.Timber;

import static com.fsck.k9.mail.ConnectionSecurity.STARTTLS_REQUIRED;
import static com.fsck.k9.mail.K9MailLib.DEBUG_PROTOCOL_IMAP;


class ImapConnectionOpener {

    private final ImapAuthenticator imapAuthenticator;
    private final ImapPostConnectionHandler imapPostConnectionHandler;

    ImapConnectionOpener(ImapAuthenticator imapAuthenticator, ImapPostConnectionHandler imapPostConnectionHandler) {
        this.imapAuthenticator = imapAuthenticator;
        this.imapPostConnectionHandler = imapPostConnectionHandler;
    }

    void openConnection(ImapConnection imapConnection) throws IOException, MessagingException {

        imapConnection.open = true;
        boolean authSuccess = false;
        imapConnection.nextCommandTag = 1;

        adjustDNSCacheTTL(imapConnection);

        try {
            imapConnection.socket = connect(imapConnection);
            configureSocket(imapConnection);
            setUpStreamsAndParserFromSocket(imapConnection, imapConnection.socket);

            readInitialResponse(imapConnection);
            requestCapabilitiesIfNecessary(imapConnection);

            upgradeToTlsIfNecessary(imapConnection);

            List<ImapResponse> responses = imapAuthenticator.authenticate(imapConnection);
            authSuccess = true;

            extractOrRequestCapabilities(imapConnection, responses);

            imapPostConnectionHandler.configureConnection(imapConnection);

        } catch (SSLException e) {
            handleSslException(e);
        } catch (ConnectException e) {
            handleConnectException(imapConnection, e);
        } catch (GeneralSecurityException e) {
            throw new MessagingException("Unable to open connection to IMAP server due to security error.", e);
        } finally {
            if (!authSuccess) {
                Timber.e("Failed to login, closing connection for %s", imapConnection.getLogId());
                imapConnection.close();
            }
        }
    }

    private void adjustDNSCacheTTL(ImapConnection imapConnection) {
        try {
            Security.setProperty("networkaddress.cache.ttl", "0");
        } catch (Exception e) {
            Timber.w(e, "Could not set DNS ttl to 0 for %s", imapConnection.getLogId());
        }

        try {
            Security.setProperty("networkaddress.cache.negative.ttl", "0");
        } catch (Exception e) {
            Timber.w(e, "Could not set DNS negative ttl to 0 for %s", imapConnection.getLogId());
        }
    }

    private void handleSslException(SSLException e) throws CertificateValidationException, SSLException {
        if (e.getCause() instanceof CertificateException) {
            throw new CertificateValidationException(e.getMessage(), e);
        } else {
            throw e;
        }
    }

    private void handleConnectException(ImapConnection imapConnection, ConnectException e) throws ConnectException {
        String message = e.getMessage();
        String[] tokens = message.split("-");

        if (tokens.length > 1 && tokens[1] != null) {
            Timber.e(e, "Stripping host/port from ConnectionException for %s", imapConnection.getLogId());
            throw new ConnectException(tokens[1].trim());
        } else {
            throw e;
        }
    }

    private Socket connect(ImapConnection imapConnection) throws GeneralSecurityException, MessagingException, IOException {
        Exception connectException = null;

        InetAddress[] inetAddresses = InetAddress.getAllByName(imapConnection.settings.getHost());
        for (InetAddress address : inetAddresses) {
            try {
                return connectToAddress(imapConnection, address);
            } catch (IOException e) {
                Timber.w(e, "Could not connect to %s", address);
                connectException = e;
            }
        }

        throw new MessagingException("Cannot connect to host", connectException);
    }

    private Socket connectToAddress(ImapConnection imapConnection, InetAddress address) throws NoSuchAlgorithmException, KeyManagementException,
            MessagingException, IOException {

        String host = imapConnection.settings.getHost();
        int port = imapConnection.settings.getPort();
        String clientCertificateAlias = imapConnection.settings.getClientCertificateAlias();

        if (K9MailLib.isDebug() && DEBUG_PROTOCOL_IMAP) {
            Timber.d("Connecting to %s as %s", host, address);
        }

        SocketAddress socketAddress = new InetSocketAddress(address, port);

        Socket socket;
        if (imapConnection.settings.getConnectionSecurity() == ConnectionSecurity.SSL_TLS_REQUIRED) {
            socket = imapConnection.socketFactory.createSocket(null, host, port, clientCertificateAlias);
        } else {
            socket = new Socket();
        }

        socket.connect(socketAddress, imapConnection.socketConnectTimeout);

        return socket;
    }

    private void configureSocket(ImapConnection imapConnection) throws SocketException {
        imapConnection.socket.setSoTimeout(imapConnection.socketReadTimeout);
    }

    private void setUpStreamsAndParserFromSocket(ImapConnection imapConnection, Socket socket) throws IOException {
        imapConnection.setUpStreamsAndParser(socket.getInputStream(), socket.getOutputStream());
    }

    private void readInitialResponse(ImapConnection imapConnection) throws IOException {
        ImapResponse initialResponse = imapConnection.responseParser.readResponse();
        if (K9MailLib.isDebug() && DEBUG_PROTOCOL_IMAP) {
            Timber.v("%s <<< %s", imapConnection.getLogId(), initialResponse);
        }
        extractCapabilities(imapConnection, Collections.singletonList(initialResponse));
    }

    private List<ImapResponse> extractCapabilities(ImapConnection imapConnection, List<ImapResponse> responses) {
        CapabilityResponse capabilityResponse = CapabilityResponse.parse(responses);
        if (capabilityResponse != null) {
            Set<String> receivedCapabilities = capabilityResponse.getCapabilities();
            if (K9MailLib.isDebug()) {
                Timber.d("Saving %s capabilities for %s", receivedCapabilities, imapConnection.getLogId());
            }
            imapConnection.capabilities = receivedCapabilities;
        }
        return responses;
    }

    private List<ImapResponse> extractOrRequestCapabilities(ImapConnection imapConnection, List<ImapResponse> responses)
            throws IOException, MessagingException {
        CapabilityResponse capabilityResponse = CapabilityResponse.parse(responses);
        if (capabilityResponse != null) {
            Set<String> receivedCapabilities = capabilityResponse.getCapabilities();
            Timber.d("Saving %s capabilities for %s", receivedCapabilities, imapConnection.getLogId());
            imapConnection.capabilities = receivedCapabilities;
        } else {
            Timber.i("Did not get capabilities in post-auth banner, requesting CAPABILITY for %s", imapConnection.getLogId());
            requestCapabilities(imapConnection);
        }

        return responses;
    }

    private void requestCapabilitiesIfNecessary(ImapConnection imapConnection) throws IOException, MessagingException {
        if (!imapConnection.capabilities.isEmpty()) {
            return;
        }
        if (K9MailLib.isDebug()) {
            Timber.i("Did not get capabilities in banner, requesting CAPABILITY for %s", imapConnection.getLogId());
        }
        requestCapabilities(imapConnection);
    }

    private void requestCapabilities(ImapConnection imapConnection) throws IOException, MessagingException {
        List<ImapResponse> responses = extractCapabilities(imapConnection, imapConnection.executeSimpleCommand(Commands.CAPABILITY));
        if (responses.size() != 2) {
            throw new MessagingException("Invalid CAPABILITY response received");
        }
    }

    private void upgradeToTlsIfNecessary(ImapConnection imapConnection) throws IOException, MessagingException, GeneralSecurityException {
        if (imapConnection.settings.getConnectionSecurity() == STARTTLS_REQUIRED) {
            upgradeToTls(imapConnection);
        }
    }

    private void upgradeToTls(ImapConnection imapConnection) throws IOException, MessagingException, GeneralSecurityException {
        if (!imapConnection.hasCapability(Capabilities.STARTTLS)) {
            /*
             * This exception triggers a "Certificate error"
             * notification that takes the user to the incoming
             * server settings for review. This might be needed if
             * the account was configured with an obsolete
             * "STARTTLS (if available)" setting.
             */
            throw new CertificateValidationException("STARTTLS connection security not available");
        }

        startTLS(imapConnection);
    }

    private void startTLS(ImapConnection imapConnection) throws IOException, MessagingException, GeneralSecurityException {
        imapConnection.executeSimpleCommand(Commands.STARTTLS);

        String host = imapConnection.settings.getHost();
        int port = imapConnection.settings.getPort();
        String clientCertificateAlias = imapConnection.settings.getClientCertificateAlias();

        imapConnection.socket = imapConnection.socketFactory.createSocket(imapConnection.socket, host, port, clientCertificateAlias);
        configureSocket(imapConnection);
        setUpStreamsAndParserFromSocket(imapConnection, imapConnection.socket);

        // Per RFC 2595 (3.1):  Once TLS has been started, reissue CAPABILITY command
        if (K9MailLib.isDebug()) {
            Timber.i("Updating capabilities after STARTTLS for %s", imapConnection.getLogId());
        }

        requestCapabilities(imapConnection);
    }
}
