package com.fsck.k9.mail.store;

import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mail.oauth.OAuth2TokenProvider;
import com.fsck.k9.mail.store.imap.ImapStore;
import com.fsck.k9.mail.store.imap.ImapStoreSettings;
import com.fsck.k9.mail.store.pop3.Pop3Store;
import com.fsck.k9.mail.store.webdav.WebDavStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by philip on 28/09/2016.
 */

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "src/main/AndroidManifest.xml", sdk = 21)
public class RemoteStoreTest {
    @Test
    public void getInstance_withImapUri_providesImapStore() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getStoreUri()).thenReturn("imap://user@localhost/");
        OAuth2TokenProvider oauth2TokenProvider = mock(OAuth2TokenProvider.class);

        Store store = RemoteStore.getInstance(
                RuntimeEnvironment.application, storeConfig, oauth2TokenProvider);

        assertEquals(ImapStore.class, store.getClass());
    }
    @Test
    public void getInstance_withWebDavUri_providesWebDavStore() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getStoreUri()).thenReturn("webdav://user@localhost/");
        OAuth2TokenProvider oauth2TokenProvider = mock(OAuth2TokenProvider.class);

        Store store = RemoteStore.getInstance(
                RuntimeEnvironment.application, storeConfig, oauth2TokenProvider);

        assertEquals(WebDavStore.class, store.getClass());
    }
    @Test
    public void getInstance_withPop3Uri_providesPop3Store() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getStoreUri()).thenReturn("pop3://user@localhost/");
        OAuth2TokenProvider oauth2TokenProvider = mock(OAuth2TokenProvider.class);

        Store store = RemoteStore.getInstance(
                RuntimeEnvironment.application, storeConfig, oauth2TokenProvider);

        assertEquals(Pop3Store.class, store.getClass());
    }

    @Test
    public void getInstance_providesSameStoreForMultipleRequests() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getStoreUri()).thenReturn("imap://user@localhost/");
        OAuth2TokenProvider oauth2TokenProvider = mock(OAuth2TokenProvider.class);

        Store store = RemoteStore.getInstance(
                RuntimeEnvironment.application, storeConfig, oauth2TokenProvider);

        Store store2 = RemoteStore.getInstance(
                RuntimeEnvironment.application, storeConfig, oauth2TokenProvider);

        assertEquals(store, store2);
    }

    @Test
    public void removeInstances_meansGetReturnsNewInstance() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getStoreUri()).thenReturn("imap://user@localhost/");
        OAuth2TokenProvider oauth2TokenProvider = mock(OAuth2TokenProvider.class);

        Store store = RemoteStore.getInstance(
                RuntimeEnvironment.application, storeConfig, oauth2TokenProvider);

        RemoteStore.removeInstance(storeConfig);

        Store store2 = RemoteStore.getInstance(
                RuntimeEnvironment.application, storeConfig, oauth2TokenProvider);

        assertNotEquals(store, store2);
    }

    @Test
    public void decodeUri_forImapUri_ReturnsImapServerSettings() {
        assertEquals(ServerSettings.Type.IMAP,
                RemoteStore.decodeStoreUri("imap://user@localhost/").type);
    }

    @Test
    public void decodeUri_forPop3Uri_ReturnsPop3ServerSettings() {
        assertEquals(ServerSettings.Type.POP3,
                RemoteStore.decodeStoreUri("pop3://user@localhost/").type);
    }

    @Test
    public void decodeUri_forWebDavUri_ReturnsWebDavServerSettings() {
        assertEquals(ServerSettings.Type.WebDAV,
                RemoteStore.decodeStoreUri("webdav://user@localhost/").type);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeUri_forUnknownUri_ThrowsException() {
        RemoteStore.decodeStoreUri("unknown://user@localhost/");
    }

    @Test
    public void createStoreUri_forImapStore_ReturnsImapUri() {
        ServerSettings settings = new ServerSettings(
                ServerSettings.Type.IMAP, "localhost", ServerSettings.Type.IMAP.defaultPort,
                ConnectionSecurity.NONE, AuthType.PLAIN, "user", "password", null);
        assertEquals(
                "imap://PLAIN:user:password@localhost:143/1%7C",
                RemoteStore.createStoreUri(settings));
    }

    @Test
    public void createStoreUri_forWebDavStore_ReturnsWebDavUri() {
        ServerSettings settings = new ServerSettings(
                ServerSettings.Type.WebDAV, "localhost", ServerSettings.Type.WebDAV.defaultPort,
                ConnectionSecurity.NONE, AuthType.PLAIN, "user", "password", null);
        assertEquals(
                "webdav://user:password@localhost:80/%7C%7C",
                RemoteStore.createStoreUri(settings));
    }

    @Test
    public void createStoreUri_forPop3Store_ReturnsPop3Uri() {
        ServerSettings settings = new ServerSettings(
                ServerSettings.Type.POP3, "localhost", ServerSettings.Type.POP3.defaultPort,
                ConnectionSecurity.NONE, AuthType.PLAIN, "user", "password", null);
        assertEquals(
                "pop3://PLAIN:user:password@localhost:110",
                RemoteStore.createStoreUri(settings));
    }
}
