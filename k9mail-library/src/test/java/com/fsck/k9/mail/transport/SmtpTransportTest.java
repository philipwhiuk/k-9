package com.fsck.k9.mail.transport;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.CertificateValidationException;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.filter.Base64;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.oauth.OAuth2TokenProvider;
import com.fsck.k9.mail.ssl.TrustedSocketFactory;
import com.fsck.k9.mail.store.StoreConfig;
import com.fsck.k9.mail.transport.mockServer.MockSmtpServer;
import com.fsck.k9.mail.transport.mockServer.TestMessage;
import com.fsck.k9.mailstore.BinaryMemoryBody;
import com.fsck.k9.testHelpers.TestTrustedSocketFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public class SmtpTransportTest {

    private String host;
    private int port;
    private ConnectionSecurity connectionSecurity;
    private AuthType authenticationType;
    private String username;
    private String password;
    private String clientCertificateAlias;
    private List<String> extensions;
    private StoreConfig storeConfig = mock(StoreConfig.class);
    private OAuth2TokenProvider oAuth2TokenProvider = mock(OAuth2TokenProvider.class);
    private TrustedSocketFactory socketFactory;

    @Before
    public void before() {
        socketFactory = new TestTrustedSocketFactory();
        resetConnectionParameters();
    }

    private void resetConnectionParameters() {
        host = null;
        port = -1;
        username = null;
        password = null;
        authenticationType = null;
        clientCertificateAlias = null;
        connectionSecurity = null;
        extensions = new ArrayList<>();
    }

    private SmtpTransport startServerAndCreateSmtpTransport(MockSmtpServer server)
            throws IOException, MessagingException {
        server.start();
        host = server.getHost();
        port = server.getPort();
        String uri = SmtpTransport.createUri(new ServerSettings(
                ServerSettings.Type.SMTP, host, port, connectionSecurity, authenticationType,
                username, password, clientCertificateAlias));
        when(storeConfig.getTransportUri()).thenReturn(uri);
        return createSmtpTransport(storeConfig, socketFactory, oAuth2TokenProvider);
    }

    private SmtpTransport createSmtpTransport(
            StoreConfig storeConfig, TrustedSocketFactory socketFactory,
            OAuth2TokenProvider oAuth2TokenProvider)
            throws MessagingException {
        return new SmtpTransport(storeConfig, socketFactory, oAuth2TokenProvider);
    }

    private void setupConnectAndPlainAuthentication(MockSmtpServer server) {
        username = "user";
        password = "password";
        authenticationType = AuthType.PLAIN;
        connectionSecurity = ConnectionSecurity.NONE;

        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        for (String extension: extensions) {
            server.output("250-"+extension);
        }
        server.output("250 AUTH LOGIN PLAIN CRAM-MD5");
        server.expect("AUTH PLAIN AHVzZXIAcGFzc3dvcmQ=");
        server.output("235 2.7.0 Authentication successful");
    }

    @Test
    public void SmtpTransport_withValidUri_canBeCreated() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getTransportUri()).thenReturn(
                "smtp://user:password:CRAM_MD5@server:123456");
        TrustedSocketFactory trustedSocketFactory = mock(TrustedSocketFactory.class);
        OAuth2TokenProvider oAuth2TokenProvider = mock(OAuth2TokenProvider.class);

        new SmtpTransport(storeConfig, trustedSocketFactory, oAuth2TokenProvider);
    }

    @Test(expected = MessagingException.class)
    public void SmtpTransport_withInvalidUri_throwsMessagingException()
            throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getTransportUri()).thenReturn("smpt://");
        TrustedSocketFactory trustedSocketFactory = mock(TrustedSocketFactory.class);
        OAuth2TokenProvider oAuth2TokenProvider = mock(OAuth2TokenProvider.class);

        new SmtpTransport(storeConfig, trustedSocketFactory, oAuth2TokenProvider);
    }

    @Test
    public void open_withNoSecurityOrPasswordPlainAuth_connectsToServer_withoutLogin()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        authenticationType = AuthType.PLAIN;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 OK");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityPlainAuth_connectsToServer()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.PLAIN;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH PLAIN LOGIN");
        server.expect("AUTH PLAIN AHVzZXIAcGFzc3dvcmQ=");
        server.output("235 2.7.0 Authentication successful");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityPlainAuth_usesLoginIfPlainUnavailable()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.PLAIN;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH LOGIN");
        server.expect("AUTH LOGIN");
        server.output("250 OK");
        server.expect("dXNlcg==");
        server.output("250 OK");
        server.expect("cGFzc3dvcmQ=");
        server.output("235 2.7.0 Authentication successful");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityPlainAuth_withNeither_throwsException()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.PLAIN;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH");
        server.expect("QUIT");
        server.output("221 BYE");

        try {
            SmtpTransport transport = startServerAndCreateSmtpTransport(server);
            transport.open();
            fail("Exception expected");
        } catch (MessagingException e) {
            assertEquals("Authentication methods SASL PLAIN and LOGIN are unavailable.",
                    e.getMessage());
        }

        server.verifyConnectionClosed();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityCramMd5Auth_connectsToServer()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.CRAM_MD5;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH CRAM-MD5");
        server.expect("AUTH CRAM-MD5");
        server.output(Base64.encode("<24609.1047914046@localhost>"));
        server.expect("dXNlciA3NmYxNWEzZmYwYTNiOGI1NzcxZmNhODZlNTcyMDk2Zg==");
        server.output("235 2.7.0 Authentication successful");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityCramMd5Auth_withNoSupport_throwsException()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.CRAM_MD5;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH PLAIN LOGIN");
        server.expect("QUIT");
        server.output("221 BYE");

        try {
            SmtpTransport transport = startServerAndCreateSmtpTransport(server);
            transport.open();
            fail("Exception expected");
        } catch (MessagingException e) {
            assertEquals("Authentication method CRAM-MD5 is unavailable.",
                    e.getMessage());
        }

        server.verifyConnectionClosed();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityXOAuth2_connectsToServer()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        authenticationType = AuthType.XOAUTH2;
        connectionSecurity = ConnectionSecurity.NONE;
        when(oAuth2TokenProvider.getToken(eq("user"), anyLong())).thenReturn("ac1234ca");

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH XOAUTH2");
        server.expect("AUTH XOAUTH2 dXNlcj11c2VyAWF1dGg9QmVhcmVyIGFjMTIzNGNhAQE=");
        server.output("235 2.7.0 Authentication successful");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityXOAuth2_withNoSupport_throwsException()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        authenticationType = AuthType.XOAUTH2;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH PLAIN LOGIN");
        server.expect("QUIT");
        server.output("221 BYE");

        try {
            SmtpTransport transport = startServerAndCreateSmtpTransport(server);
            transport.open();
            fail("Exception expected");
        } catch (MessagingException e) {
            assertEquals("Authentication method XOAUTH2 is unavailable.",
                    e.getMessage());
        }

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityXOAuth2_fetchesNewTokenOnFailure()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        authenticationType = AuthType.XOAUTH2;
        connectionSecurity = ConnectionSecurity.NONE;

        when(oAuth2TokenProvider.getToken(eq("user"), anyLong()))
                .thenReturn("ac1234ca").thenReturn("bc1234");

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH XOAUTH2");
        server.expect("AUTH XOAUTH2 dXNlcj11c2VyAWF1dGg9QmVhcmVyIGFjMTIzNGNhAQE=");
        server.output("535 5.7.1 Username and Password not accepted.");
        server.expect("AUTH XOAUTH2 dXNlcj11c2VyAWF1dGg9QmVhcmVyIGJjMTIzNAEB");
        server.output("235 2.7.0 Authentication successful");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();

        InOrder inOrder = inOrder(oAuth2TokenProvider);
        inOrder.verify(oAuth2TokenProvider).getToken(eq("user"), anyLong());
        inOrder.verify(oAuth2TokenProvider).invalidateToken("user");
        inOrder.verify(oAuth2TokenProvider).getToken(eq("user"), anyLong());
    }

    @Test
    public void open_withNoSecurityXOAuth2_onSecondFailure_throwsException()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        authenticationType = AuthType.XOAUTH2;
        connectionSecurity = ConnectionSecurity.NONE;

        when(oAuth2TokenProvider.getToken(eq("user"), anyLong()))
                .thenReturn("ac1234ca").thenReturn("bc1234");

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH XOAUTH2");
        server.expect("AUTH XOAUTH2 dXNlcj11c2VyAWF1dGg9QmVhcmVyIGFjMTIzNGNhAQE=");
        server.output("535 5.7.1 Username and Password not accepted.");
        server.expect("AUTH XOAUTH2 dXNlcj11c2VyAWF1dGg9QmVhcmVyIGJjMTIzNAEB");
        server.output("535 5.7.1 Username and Password not accepted.");
        server.expect("QUIT");
        server.output("221 BYE");

        try {
            SmtpTransport transport = startServerAndCreateSmtpTransport(server);
            transport.open();
            fail();
        } catch (AuthenticationFailedException e) {
            assertEquals("Negative SMTP reply: 535 5.7.1 Username and Password not accepted.",
                    e.getMessage());
        }

        server.verifyConnectionClosed();
        server.verifyInteractionCompleted();

        InOrder inOrder = inOrder(oAuth2TokenProvider);
        inOrder.verify(oAuth2TokenProvider).getToken(eq("user"), anyLong());
        inOrder.verify(oAuth2TokenProvider).invalidateToken("user");
        inOrder.verify(oAuth2TokenProvider).getToken(eq("user"), anyLong());
        inOrder.verify(oAuth2TokenProvider).invalidateToken("user");
    }

    @Test
    public void open_withNoSecurityExternalAuth_connectsToServer()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.EXTERNAL;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH EXTERNAL");
        server.expect("AUTH EXTERNAL dXNlcg==");
        server.output("235 2.7.0 Authentication successful");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityExternal_withNoSupport_throwsException()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.EXTERNAL;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH");
        server.expect("QUIT");
        server.output("221 BYE");

        try {
            SmtpTransport transport = startServerAndCreateSmtpTransport(server);
            transport.open();
            fail("Exception expected");
        } catch (CertificateValidationException e) {
            assertEquals(CertificateValidationException.Reason.MissingCapability, e.getReason());
        }

        server.verifyConnectionClosed();
        server.verifyInteractionCompleted();
    }
    @Test
    public void open_withNoSecurityAutomatic_connectsToServerWithCramMD5IfSupported()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.AUTOMATIC;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH CRAM-MD5");
        server.expect("AUTH CRAM-MD5");
        server.output(Base64.encode("<24609.1047914046@localhost>"));
        server.expect("dXNlciA3NmYxNWEzZmYwYTNiOGI1NzcxZmNhODZlNTcyMDk2Zg==");
        server.output("235 2.7.0 Authentication successful");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_withNoSecurityAutomatic_withCramMD5Unsupported_throwsException()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        password = "password";
        authenticationType = AuthType.AUTOMATIC;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("250-localhost Hello client.localhost");
        server.output("250 AUTH PLAIN LOGIN");
        server.expect("QUIT");
        server.output("221 BYE");

        try {
            SmtpTransport transport = startServerAndCreateSmtpTransport(server);
            transport.open();
            fail("Exception expected");
        } catch (MessagingException e) {
            assertEquals("Update your outgoing server authentication setting. AUTOMATIC auth. is unavailable.",
                    e.getMessage());
        }

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void open_triesHELO_whenServerDoesntSupportEHLO()
            throws MessagingException, IOException, InterruptedException {
        username = "user";
        authenticationType = AuthType.PLAIN;
        connectionSecurity = ConnectionSecurity.NONE;

        MockSmtpServer server = new MockSmtpServer();
        server.output("220 localhost Simple Mail Transfer Service Ready");
        server.expect("EHLO localhost");
        server.output("502 5.5.1, Unrecognized command.");
        server.expect("HELO localhost");
        server.output("250 localhost");

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.open();

        server.verifyConnectionStillOpen();
        server.verifyInteractionCompleted();
    }

    @Test
    public void sendMessage_withNoAddressToSendTo_doesntOpenConnection()
            throws MessagingException, IOException, InterruptedException {

        MimeMessage message = new MimeMessage();

        MockSmtpServer server = new MockSmtpServer();
        setupConnectAndPlainAuthentication(server);

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.sendMessage(message);

        server.verifyConnectionNeverCreated();
    }

    @Test
    public void sendMessage_withToAddressToSendTo_opensConnection()
            throws MessagingException, IOException, InterruptedException {

        TestMessage message = new TestMessage();
        message.setFrom(new Address("user@localhost"));
        message.setRecipients(Message.RecipientType.TO, new Address[]{new Address("user2@localhost")});

        MockSmtpServer server = new MockSmtpServer();
        setupConnectAndPlainAuthentication(server);
        server.expect("MAIL FROM:<user@localhost>");
        server.output("250 OK");
        server.expect("RCPT TO:<user2@localhost>");
        server.output("250 OK");
        server.expect("DATA");
        server.output("354 End data with <CR><LF>.<CR><LF>");
        server.expect("");
        server.expect(".");
        server.output("250 OK: queued as 12345");
        server.expect("QUIT");
        server.output("221 BYE");


        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.sendMessage(message);
    }

    @Test
    public void sendMessage_with8BitEncoding_usesEncoding()
            throws MessagingException, IOException, InterruptedException {
        extensions.add("8BITMIME");
        TestMessage message = new TestMessage();
        message.setFrom(new Address("user@localhost"));
        message.setRecipients(Message.RecipientType.TO, new Address[]{new Address("user2@localhost")});

        MockSmtpServer server = new MockSmtpServer();
        setupConnectAndPlainAuthentication(server);
        server.expect("MAIL FROM:<user@localhost> BODY=8BITMIME");
        server.output("250 OK");
        server.expect("RCPT TO:<user2@localhost>");
        server.output("250 OK");
        server.expect("DATA");
        server.output("354 End data with <CR><LF>.<CR><LF>");
        server.expect("");
        server.expect(".");
        server.output("250 OK: queued as 12345");
        server.expect("QUIT");
        server.output("221 BYE");


        SmtpTransport transport = startServerAndCreateSmtpTransport(server);
        transport.sendMessage(message);
    }

    @Test
    public void sendMessage_withMessageTooLarge_throwsException()
            throws MessagingException, IOException, InterruptedException {
        extensions.add("SIZE 1000");
        TestMessage message = new TestMessage();
        message.setFrom(new Address("user@localhost"));
        message.setRecipients(Message.RecipientType.TO, new Address[]{new Address("user2@localhost")});
        message.setAttachmentCount(1);
        message.setBody(new BinaryMemoryBody(new byte[1001], "US-ASCII"));

        MockSmtpServer server = new MockSmtpServer();
        setupConnectAndPlainAuthentication(server);

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);

        try {
            transport.sendMessage(message);
            fail("Expected message too large error");
        } catch (MessagingException e) {
            assertTrue(e.isPermanentFailure());
            assertEquals("Message too large for server", e.getMessage());
        }
    }

    @Test
    public void sendMessage_withNegativeReply_throwsException()
            throws MessagingException, IOException, InterruptedException {
        TestMessage message = new TestMessage();
        message.setFrom(new Address("user@localhost"));
        message.setRecipients(Message.RecipientType.TO, new Address[]{new Address("user2@localhost")});

        MockSmtpServer server = new MockSmtpServer();
        setupConnectAndPlainAuthentication(server);
        server.expect("MAIL FROM:<user@localhost>");
        server.output("250 OK");
        server.expect("RCPT TO:<user2@localhost>");
        server.output("250 OK");
        server.expect("DATA");
        server.output("354 End data with <CR><LF>.<CR><LF>");
        server.expect("");
        server.expect(".");
        server.output("421 4.7.0 Temporary system problem");
        server.expect("QUIT");
        server.output("221 BYE");
        server.closeConnection();

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);

        try {
            transport.sendMessage(message);
            fail("Expected exception");
        } catch (SmtpTransport.NegativeSmtpReplyException e) {
            assertEquals(421, e.getReplyCode());
            assertEquals("4.7.0 Temporary system problem", e.getReplyText());
        }
    }

    @Test
    public void sendMessage_withConnectionClosed_throwsException()
            throws MessagingException, IOException, InterruptedException {

        TestMessage message = new TestMessage();
        message.setFrom(new Address("user@localhost"));
        message.setRecipients(Message.RecipientType.TO, new Address[]{new Address("user2@localhost")});

        MockSmtpServer server = new MockSmtpServer();
        setupConnectAndPlainAuthentication(server);
        server.expect("MAIL FROM:<user@localhost>");
        server.output("250 OK");
        server.expect("RCPT TO:<user2@localhost>");
        server.output("250 OK");
        server.expect("DATA");
        server.output("354 End data with <CR><LF>.<CR><LF>");
        server.expect("");
        server.closeConnection();

        SmtpTransport transport = startServerAndCreateSmtpTransport(server);

        try {
            transport.sendMessage(message);
            fail("Expected exception");
        } catch (MessagingException e) {
            assertTrue(e.isPermanentFailure());
            assertEquals("Unable to send message", e.getMessage());
        }
    }
}
