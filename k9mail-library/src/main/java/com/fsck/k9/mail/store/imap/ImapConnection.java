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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Build.VERSION;

import com.fsck.k9.mail.Authentication;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.BuildConfig;
import com.fsck.k9.mail.CertificateValidationException;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.NetworkType;
import com.fsck.k9.mail.filter.Base64;
import com.fsck.k9.mail.filter.PeekableInputStream;
import com.fsck.k9.mail.oauth.OAuth2TokenProvider;
import com.fsck.k9.mail.oauth.XOAuth2ChallengeParser;
import com.fsck.k9.mail.ssl.TrustedSocketFactory;
import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZOutputStream;
import javax.net.ssl.SSLException;
import org.apache.commons.io.IOUtils;
import timber.log.Timber;

import static com.fsck.k9.mail.ConnectionSecurity.STARTTLS_REQUIRED;
import static com.fsck.k9.mail.K9MailLib.DEBUG_PROTOCOL_IMAP;
import static com.fsck.k9.mail.store.RemoteStore.SOCKET_CONNECT_TIMEOUT;
import static com.fsck.k9.mail.store.RemoteStore.SOCKET_READ_TIMEOUT;
import static com.fsck.k9.mail.store.imap.ImapResponseParser.equalsIgnoreCase;


/**
 * A cacheable class that stores the details for a single IMAP connection.
 */
class ImapConnection {
    private static final int BUFFER_SIZE = 1024;
    private static final ImapConnectionOpener connectionOpener
            = new ImapConnectionOpener(new ImapAuthenticator(), new ImapPostConnectionHandler());


    final ConnectivityManager connectivityManager;
    final OAuth2TokenProvider oauthTokenProvider;
    final TrustedSocketFactory socketFactory;
    final int socketConnectTimeout;
    final int socketReadTimeout;

    Socket socket;
    private PeekableInputStream inputStream;
    OutputStream outputStream;
    ImapResponseParser responseParser;
    int nextCommandTag;
    Set<String> capabilities = new HashSet<>();
    ImapSettings settings;
    private Exception stacktraceForClose;
    boolean open = false;
    boolean retryXoauth2WithNewToken = true;


    public ImapConnection(ImapSettings settings, TrustedSocketFactory socketFactory,
            ConnectivityManager connectivityManager, OAuth2TokenProvider oauthTokenProvider) {
        this.settings = settings;
        this.socketFactory = socketFactory;
        this.connectivityManager = connectivityManager;
        this.oauthTokenProvider = oauthTokenProvider;
        this.socketConnectTimeout = SOCKET_CONNECT_TIMEOUT;
        this.socketReadTimeout = SOCKET_READ_TIMEOUT;
    }

    ImapConnection(ImapSettings settings, TrustedSocketFactory socketFactory,
            ConnectivityManager connectivityManager, OAuth2TokenProvider oauthTokenProvider,
            int socketConnectTimeout, int socketReadTimeout) {
        this.settings = settings;
        this.socketFactory = socketFactory;
        this.connectivityManager = connectivityManager;
        this.oauthTokenProvider = oauthTokenProvider;
        this.socketConnectTimeout = socketConnectTimeout;
        this.socketReadTimeout = socketReadTimeout;
    }

    public void open() throws IOException, MessagingException {
        if (open) {
            return;
        } else if (stacktraceForClose != null) {
            throw new IllegalStateException("open() called after close(). " +
                    "Check wrapped exception to see where close() was called.", stacktraceForClose);
        }

        connectionOpener.openConnection(this);

    }

    boolean isConnected() {
        return inputStream != null && outputStream != null && socket != null &&
                socket.isConnected() && !socket.isClosed();
    }

    boolean isListResponse(ImapResponse response) {
        boolean responseTooShort = response.size() < 4;
        if (responseTooShort) {
            return false;
        }

        boolean isListResponse = equalsIgnoreCase(response.get(0), Responses.LIST);
        boolean hierarchyDelimiterValid = response.get(2) instanceof String;

        return isListResponse && hierarchyDelimiterValid;
    }

    boolean hasCapability(String capability) {
        return capabilities.contains(capability.toUpperCase(Locale.US));
    }

    boolean isIdleCapable() {
        if (K9MailLib.isDebug()) {
            Timber.v("Connection %s has %d capabilities", getLogId(), capabilities.size());
        }

        return capabilities.contains(Capabilities.IDLE);
    }

    public void close() {
        open = false;
        stacktraceForClose = new Exception();

        IOUtils.closeQuietly(inputStream);
        IOUtils.closeQuietly(outputStream);
        IOUtils.closeQuietly(socket);

        inputStream = null;
        outputStream = null;
        socket = null;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    String getLogId() {
        return "conn" + hashCode();
    }

    void setUpStreamsAndParser(InputStream input, OutputStream output) {
        inputStream = new PeekableInputStream(new BufferedInputStream(input, ImapConnection.BUFFER_SIZE));
        responseParser = new ImapResponseParser(inputStream);
        outputStream = new BufferedOutputStream(output, ImapConnection.BUFFER_SIZE);
    }

    public List<ImapResponse> executeSimpleCommand(String command) throws IOException, MessagingException {
        return executeSimpleCommand(command, false);
    }

    public List<ImapResponse> executeSimpleCommand(String command, boolean sensitive) throws IOException,
            MessagingException {
        String commandToLog = command;

        if (sensitive && !K9MailLib.isDebugSensitive()) {
            commandToLog = "*sensitive*";
        }

        String tag = sendCommand(command, sensitive);

        try {
            return responseParser.readStatusResponse(tag, commandToLog, getLogId(), null);
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    List<ImapResponse> readStatusResponse(String tag, String commandToLog, UntaggedHandler untaggedHandler)
            throws IOException, NegativeImapResponseException {
        return responseParser.readStatusResponse(tag, commandToLog, getLogId(), untaggedHandler);
    }

    String sendSaslIrCommand(String command, String initialClientResponse, boolean sensitive)
            throws IOException, MessagingException {
        try {
            open();

            String tag = Integer.toString(nextCommandTag++);
            String commandToSend = tag + " " + command + " " + initialClientResponse + "\r\n";
            outputStream.write(commandToSend.getBytes());
            outputStream.flush();

            if (K9MailLib.isDebug() && DEBUG_PROTOCOL_IMAP) {
                if (sensitive && !K9MailLib.isDebugSensitive()) {
                    Timber.v("%s>>> [Command Hidden, Enable Sensitive Debug Logging To Show]", getLogId());
                } else {
                    Timber.v("%s>>> %s %s %s", getLogId(), tag, command, initialClientResponse);
                }
            }

            return tag;
        } catch (IOException | MessagingException e) {
            close();
            throw e;
        }
    }

    String sendCommand(String command, boolean sensitive) throws MessagingException, IOException {
        try {
            open();

            String tag = Integer.toString(nextCommandTag++);
            String commandToSend = tag + " " + command + "\r\n";
            outputStream.write(commandToSend.getBytes());
            outputStream.flush();

            if (K9MailLib.isDebug() && DEBUG_PROTOCOL_IMAP) {
                if (sensitive && !K9MailLib.isDebugSensitive()) {
                    Timber.v("%s>>> [Command Hidden, Enable Sensitive Debug Logging To Show]", getLogId());
                } else {
                    Timber.v("%s>>> %s %s", getLogId(), tag, command);
                }
            }

            return tag;
        } catch (IOException | MessagingException e) {
            close();
            throw e;
        }
    }

    void sendContinuation(String continuation) throws IOException {
        outputStream.write(continuation.getBytes());
        outputStream.write('\r');
        outputStream.write('\n');
        outputStream.flush();

        if (K9MailLib.isDebug() && DEBUG_PROTOCOL_IMAP) {
            Timber.v("%s>>> %s", getLogId(), continuation);
        }
    }

    ImapResponse readResponse() throws IOException, MessagingException {
        return readResponse(null);
    }

    ImapResponse readResponse(ImapResponseCallback callback) throws IOException {
        try {
            ImapResponse response = responseParser.readResponse(callback);

            if (K9MailLib.isDebug() && DEBUG_PROTOCOL_IMAP) {
                Timber.v("%s<<<%s", getLogId(), response);
            }

            return response;
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    void setReadTimeout(int millis) throws SocketException {
        Socket sock = socket;
        if (sock != null) {
            sock.setSoTimeout(millis);
        }
    }

    ImapResponse readContinuationResponse(String tag) throws IOException, MessagingException {
        ImapResponse response;
        do {
            response = readResponse();

            String responseTag = response.getTag();
            if (responseTag != null) {
                if (responseTag.equalsIgnoreCase(tag)) {
                    throw new MessagingException("Command continuation aborted: " + response);
                } else {
                    Timber.w("After sending tag %s, got tag response from previous command %s for %s",
                            tag, response, getLogId());
                }
            }
        } while (!response.isContinuationRequested());

        return response;
    }
}
