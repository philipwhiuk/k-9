package com.fsck.k9.mail;

import com.fsck.k9.mail.oauth.OAuth2TokenProvider;
import com.fsck.k9.mail.store.StoreConfig;
import com.fsck.k9.mail.store.webdav.WebDavStoreSettings;
import com.fsck.k9.mail.transport.SmtpTransport;
import com.fsck.k9.mail.transport.WebDavTransport;

import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransportTest {

    @Test
    public void getInstance_providesCorrectTransportTypeForSmtpURI() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getTransportUri()).thenReturn("smtp://user@localhost/");
        OAuth2TokenProvider oauth2TokenProvider = mock(OAuth2TokenProvider.class);

        Transport transport = Transport.getInstance(RuntimeEnvironment.application, storeConfig,
                oauth2TokenProvider);

        assertEquals(SmtpTransport.class, transport.getClass());
    }

    @Test
    public void getInstance_providesCorrectTransportTypeForWebdavURI() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getTransportUri()).thenReturn("webdav://user@localhost/");
        when(storeConfig.getStoreUri()).thenReturn("webdav://user@localhost/");
        OAuth2TokenProvider oauth2TokenProvider = mock(OAuth2TokenProvider.class);

        Transport transport = Transport.getInstance(RuntimeEnvironment.application, storeConfig,
                oauth2TokenProvider);

        assertEquals(WebDavTransport.class, transport.getClass());
    }

    @Test
    public void decodeTransportUri_forSmtpUri_returnsServerSettings() throws MessagingException {
        ServerSettings settings = Transport.decodeTransportUri("smtp://user@localhost/");
        assertEquals(ServerSettings.class, settings.getClass());
        assertNotNull(settings);
        assertEquals(ServerSettings.Type.SMTP, settings.type);
    }

    @Test
    public void decodeTransportUri_forWebDavUri_returnsServerSettings() throws MessagingException {
        ServerSettings settings = Transport.decodeTransportUri("webdav://user@localhost/");
        assertEquals(WebDavStoreSettings.class, settings.getClass());
        assertNotNull(settings);
        assertEquals(ServerSettings.Type.WebDAV, settings.type);
    }

    @Test
    public void createTransportUri_forSmtpSettings() throws MessagingException {
        ServerSettings settings = new ServerSettings(
                ServerSettings.Type.SMTP, "localhost", ServerSettings.Type.SMTP.defaultPort,
                ConnectionSecurity.NONE, AuthType.PLAIN, "user", "password", null);
        assertEquals("smtp://user:password:PLAIN@localhost:587", Transport.createTransportUri(settings));
    }

    @Test
    public void createTransportUri_forWebDavSettings() throws MessagingException {
        ServerSettings settings = new ServerSettings(ServerSettings.Type.WebDAV,
                "localhost", ServerSettings.Type.WebDAV.defaultPort,
                ConnectionSecurity.NONE, AuthType.PLAIN, "user", "password", null);
        assertEquals("webdav://user:password@localhost:80/%7C%7C", Transport.createTransportUri(settings));
    }
}
