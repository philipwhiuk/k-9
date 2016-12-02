package com.fsck.k9.mail.transport;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.StoreConfig;

import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebDavTransportTest {

    @Test
    public void WebDavTransport__canCreateFromStoreConfigWithValidStoreUri() throws MessagingException {
        new WebDavTransport(createStoreConfigWithStoreUri(
                "webdav://user:password:CRAM_MD5@server:123456"));
    }

    @Test(expected = MessagingException.class)
    public void WebDavTransport__throwsMessagingExceptionWithSmtpUri() throws MessagingException {
        new WebDavTransport(createStoreConfigWithStoreUri(
                "smtp://user:password:CRAM_MD5@server:123456"));
    }

    @Test(expected = MessagingException.class)
    public void WebDavTransport__throwsMessagingExceptionWithImapUri() throws MessagingException {
        new WebDavTransport(createStoreConfigWithStoreUri(
                "imap://user:password:CRAM_MD5@server:123456"));
    }

    private StoreConfig createStoreConfigWithStoreUri(String value) {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getStoreUri()).thenReturn(value);
        return storeConfig;
    }
}
