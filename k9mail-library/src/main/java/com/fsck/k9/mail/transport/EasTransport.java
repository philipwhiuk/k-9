package com.fsck.k9.mail.transport;


import android.util.Log;

import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Transport;
import com.fsck.k9.mail.store.StoreConfig;
import com.fsck.k9.mail.store.eas.EasHttpClientFactory;
import com.fsck.k9.mail.store.eas.EasStore;

import java.util.Collections;

import static com.fsck.k9.mail.K9MailLib.LOG_TAG;

/**
 * Transport for Exchange Active Sync
 */
public class EasTransport extends Transport {
    private EasStore store;

    public EasTransport(StoreConfig storeConfig) throws MessagingException {
        store = new EasStore(storeConfig, new EasHttpClientFactory());

        if (K9MailLib.isDebug()) {
            Log.d(LOG_TAG, ">>> New WebDavTransport creation complete");
        }
    }

    @Override
    public void open() throws MessagingException {
        if (K9MailLib.isDebug()) {
            Log.d(LOG_TAG, ">>> open called on WebDavTransport ");
        }

        store.getHttpClient();
    }

    @Override
    public void sendMessage(Message message) throws MessagingException {
        store.sendMessages(Collections.singletonList(message));
    }

    @Override
    public void close() {
    }
}
