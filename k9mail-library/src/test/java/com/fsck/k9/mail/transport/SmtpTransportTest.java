package com.fsck.k9.mail.transport;


import android.annotation.SuppressLint;

import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.ssl.TrustedSocketFactory;
import com.fsck.k9.mail.store.StoreConfig;
import com.fsck.k9.mail.transport.mockserver.MockSmtpServer;

import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class SmtpTransportTest {

    private TrustedSocketFactory trustedSocketFactory = new TestTrustedSocketFactory();
    private ConnectionSecurity connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED;
    private AuthType authenticationType;
    private String username;
    private String password;
    private String clientCertificateAlias;

    @Test
    public void decodeUri_canDecodeAuthType() {
        String storeUri = "smtp://user:password:PLAIN@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals(AuthType.PLAIN, result.authenticationType);
    }

    @Test
    public void decodeUri_canDecodeUsername() {
        String storeUri = "smtp://user:password:PLAIN@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals("user", result.username);
    }

    @Test
    public void decodeUri_canDecodePassword() {
        String storeUri = "smtp://user:password:PLAIN@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals("password", result.password);
    }

    @Test
    public void decodeUri_canDecodeHost() {
        String storeUri = "smtp://user:password:PLAIN@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals("server", result.host);
    }

    @Test
    public void decodeUri_canDecodePort() {
        String storeUri = "smtp://user:password:PLAIN@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals(123456, result.port);
    }

    @Test
    public void decodeUri_canDecodeTLS() {
        String storeUri = "smtp+tls+://user:password:PLAIN@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals(ConnectionSecurity.STARTTLS_REQUIRED, result.connectionSecurity);
    }

    @Test
    public void decodeUri_canDecodeSSL() {
        String storeUri = "smtp+ssl+://user:password:PLAIN@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals(ConnectionSecurity.SSL_TLS_REQUIRED, result.connectionSecurity);
    }

    @Test
    public void decodeUri_canDecodeClientCert() {
        String storeUri = "smtp+ssl+://user:clientCert:EXTERNAL@server:123456";

        ServerSettings result = SmtpTransport.decodeUri(storeUri);

        assertEquals("clientCert", result.clientCertificateAlias);
    }

    @Test
    public void createUri_canEncodeSmtpSslUri() {
        ServerSettings serverSettings = new ServerSettings(
                ServerSettings.Type.SMTP, "server", 123456,
                ConnectionSecurity.SSL_TLS_REQUIRED, AuthType.EXTERNAL,
                "user", "password", "clientCert");

        String result = SmtpTransport.createUri(serverSettings);

        assertEquals("smtp+ssl+://user:clientCert:EXTERNAL@server:123456", result);
    }

    @Test
    public void createUri_canEncodeSmtpTlsUri() {
        ServerSettings serverSettings = new ServerSettings(
                ServerSettings.Type.SMTP, "server", 123456,
                ConnectionSecurity.STARTTLS_REQUIRED, AuthType.PLAIN,
                "user", "password", "clientCert");

        String result = SmtpTransport.createUri(serverSettings);

        assertEquals("smtp+tls+://user:password:PLAIN@server:123456", result);
    }

    @Test
    public void createUri_canEncodeSmtpUri() {
        ServerSettings serverSettings = new ServerSettings(
                ServerSettings.Type.SMTP, "server", 123456,
                ConnectionSecurity.NONE, AuthType.CRAM_MD5,
                "user", "password", "clientCert");

        String result = SmtpTransport.createUri(serverSettings);

        assertEquals("smtp://user:password:CRAM_MD5@server:123456", result);
    }

    @Test
    public void open_canCreateConnectionToTrustedSocket() throws Exception {
        MockSmtpServer server = new MockSmtpServer();
        server.output("220 mx1.example.com ESMTP server ready Mon, 01 Jan 2016 01:02:03 +0100");
        server.expect("HELO client.example.com");
        SmtpTransport transport = startServerAndCreateSmtpTransport(server);

        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();

    }

    private SmtpTransport startServerAndCreateSmtpTransport(MockSmtpServer server) throws IOException, MessagingException {
        server.start();
        return createSmtpTransport(new ServerSettings(
                ServerSettings.Type.SMTP, server.getHost(), server.getPort(),
                connectionSecurity,
                authenticationType,
                username,
                password,
                clientCertificateAlias), trustedSocketFactory);
    }

    private SmtpTransport createSmtpTransport(ServerSettings settings, TrustedSocketFactory socketFactory) throws MessagingException {
        StoreConfig config = mock(StoreConfig.class);
        when(config.getTransportUri()).thenReturn(SmtpTransport.createUri(settings));

        return new SmtpTransport(config, socketFactory);
    }

    private SmtpTransport simpleOpen(MockSmtpServer server) throws Exception {
        return simpleOpenWithCapabilities(server, "");
    }

    private SmtpTransport simpleOpenWithCapabilities(MockSmtpServer server, String capabilities) throws Exception {
//        simpleOpenDialog(server, capabilities);

        SmtpTransport smtpTransport = startServerAndCreateSmtpTransport(server);
        smtpTransport.open();

        return smtpTransport;
    }

    private static class TestTrustedSocketFactory implements TrustedSocketFactory {
        @Override
        public Socket createSocket(Socket socket, String host, int port, String clientCertificateAlias)
                throws NoSuchAlgorithmException, KeyManagementException, MessagingException, IOException {

            TrustManager[] trustManagers = new TrustManager[] { new VeryTrustingTrustManager() };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            if(socket == null) {
                return sslSocketFactory.createSocket();
            }

            return sslSocketFactory.createSocket(
                    socket,
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort(),
                    true);
        }
    }

    @SuppressLint("TrustAllX509TrustManager")
    private static class VeryTrustingTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Accept all certificates
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Accept all certificates
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

}
