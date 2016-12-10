package com.fsck.k9.mail.store.ews;

import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.ssl.TrustedSocketFactory;
import com.fsck.k9.mail.store.RemoteStore;
import com.fsck.k9.mail.store.StoreConfig;

import java.util.List;

/**
 * Exchange Web Services protocol
 */
public class EwsStore extends RemoteStore {
    public EwsStore(StoreConfig storeConfig, TrustedSocketFactory trustedSocketFactory) {
        super(storeConfig, trustedSocketFactory);
    }

    @Override
    public boolean syncByDeltas() {
        return false;
    }

    @Override
    public Folder<? extends Message> getFolder(String name) {
        throw new UnsupportedOperationException("EWS is not yet implemented");
    }

    @Override
    public List<? extends Folder> getPersonalNamespaces(boolean forceListAll) throws MessagingException {
        throw new UnsupportedOperationException("EWS is not yet implemented");
    }

    @Override
    public void checkSettings() throws MessagingException {
        throw new UnsupportedOperationException("EWS is not yet implemented");
    }

    public static String createUri(ServerSettings server) {
        throw new UnsupportedOperationException("EWS is not yet implemented");
    }
}
