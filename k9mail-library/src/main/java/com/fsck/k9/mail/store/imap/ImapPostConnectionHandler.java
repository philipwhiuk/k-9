package com.fsck.k9.mail.store.imap;


import java.io.IOException;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import android.net.NetworkInfo;
import android.os.Build.VERSION;

import com.fsck.k9.mail.BuildConfig;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.NetworkType;
import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZOutputStream;
import timber.log.Timber;


class ImapPostConnectionHandler {

    void configureConnection(ImapConnection imapConnection) throws IOException, MessagingException {
        fetchAndSendIDIfRequested(imapConnection);
        enableCompressionIfRequested(imapConnection);
        retrievePathPrefixIfNecessary(imapConnection);
        retrievePathDelimiterIfNecessary(imapConnection);
    }

    private void fetchAndSendIDIfRequested(ImapConnection imapConnection) throws IOException, MessagingException {
        if (imapConnection.hasCapability(Capabilities.ID)) {
            fetchAndSendID(imapConnection);
        }
    }

    private void fetchAndSendID(ImapConnection imapConnection) throws IOException, MessagingException {
        try {
            List<ImapResponse> responses;

            if (imapConnection.settings.shouldIdentifyClient()) {
                responses = imapConnection.executeSimpleCommand(Commands.ID + " (" +
                        "\"name\" \"K-9\" " +
                        "\"version\" \""+ BuildConfig.VERSION_NAME+"\" " +
                        "\"vendor\" \"K-9 Dog Walkers\" " +
                        "\"contact\" \"k-9-dev@googlegroups.com\" " +
                        "\"os\" \"Android\" " +
                        "\"os-version\" \""+ VERSION.SDK_INT+"\")");
            } else {
                responses = imapConnection.executeSimpleCommand(Commands.ID + " NIL");
            }

            if (responses.size() >= 0) {
                ImapResponse response = responses.get(0);
                if (!response.isTagged() && !"NIL".equals(response.get(1))) {
                    ImapList idData = response.getKeyedList("ID");
                    Timber.d("Server identified as: " + idData.getKeyedString("name"),
                            " - support url:" + idData.getKeyedString("support-url"));
                }
            }

        } catch (NegativeImapResponseException e) {
            Timber.d(e, "Unable to identify client/server: ");
        }
    }

    private void enableCompressionIfRequested(ImapConnection imapConnection) throws IOException, MessagingException {
        if (imapConnection.hasCapability(Capabilities.COMPRESS_DEFLATE) && shouldEnableCompression(imapConnection)) {
            enableCompression(imapConnection);
        }
    }

    private boolean shouldEnableCompression(ImapConnection imapConnection) {
        boolean useCompression = true;

        NetworkInfo networkInfo = imapConnection.connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            int type = networkInfo.getType();
            if (K9MailLib.isDebug()) {
                Timber.d("On network type %s", type);
            }

            NetworkType networkType = NetworkType.fromConnectivityManagerType(type);
            useCompression = imapConnection.settings.useCompression(networkType);
        }

        if (K9MailLib.isDebug()) {
            Timber.d("useCompression: %b", useCompression);
        }

        return useCompression;
    }

    private void enableCompression(ImapConnection imapConnection) throws IOException, MessagingException {
        try {
            imapConnection.executeSimpleCommand(Commands.COMPRESS_DEFLATE);
        } catch (NegativeImapResponseException e) {
            Timber.d(e, "Unable to negotiate compression: ");
            return;
        }

        try {
            InflaterInputStream input = new InflaterInputStream(imapConnection.socket.getInputStream(), new Inflater(true));
            ZOutputStream output = new ZOutputStream(imapConnection.socket.getOutputStream(), JZlib.Z_BEST_SPEED, true);
            output.setFlushMode(JZlib.Z_PARTIAL_FLUSH);

            imapConnection.setUpStreamsAndParser(input, output);

            if (K9MailLib.isDebug()) {
                Timber.i("Compression enabled for %s", imapConnection.getLogId());
            }
        } catch (IOException e) {
            imapConnection.close();
            Timber.e(e, "Error enabling compression");
        }
    }

    private void retrievePathPrefixIfNecessary(ImapConnection imapConnection) throws IOException, MessagingException {
        if (imapConnection.settings.getPathPrefix() != null) {
            return;
        }

        if (imapConnection.hasCapability(Capabilities.NAMESPACE)) {
            if (K9MailLib.isDebug()) {
                Timber.i("pathPrefix is unset and server has NAMESPACE capability");
            }
            handleNamespace(imapConnection);
        } else {
            if (K9MailLib.isDebug()) {
                Timber.i("pathPrefix is unset but server does not have NAMESPACE capability");
            }
            imapConnection.settings.setPathPrefix("");
        }
    }

    private void handleNamespace(ImapConnection imapConnection) throws IOException, MessagingException {
        List<ImapResponse> responses = imapConnection.executeSimpleCommand(Commands.NAMESPACE);

        NamespaceResponse namespaceResponse = NamespaceResponse.parse(responses);
        if (namespaceResponse != null) {
            String prefix = namespaceResponse.getPrefix();
            String hierarchyDelimiter = namespaceResponse.getHierarchyDelimiter();

            imapConnection.settings.setPathPrefix(prefix);
            imapConnection.settings.setPathDelimiter(hierarchyDelimiter);
            imapConnection.settings.setCombinedPrefix(null);

            if (K9MailLib.isDebug()) {
                Timber.d("Got path '%s' and separator '%s'", prefix, hierarchyDelimiter);
            }
        }
    }

    private void retrievePathDelimiterIfNecessary(ImapConnection imapConnection) throws IOException, MessagingException {
        if (imapConnection.settings.getPathDelimiter() == null) {
            retrievePathDelimiter(imapConnection);
        }
    }

    private void retrievePathDelimiter(ImapConnection imapConnection) throws IOException, MessagingException {
        List<ImapResponse> listResponses;
        try {
            listResponses = imapConnection.executeSimpleCommand(Commands.LIST + " \"\" \"\"");
        } catch (NegativeImapResponseException e) {
            Timber.d(e, "Error getting path delimiter using LIST command");
            return;
        }

        for (ImapResponse response : listResponses) {
            if (imapConnection.isListResponse(response)) {
                String hierarchyDelimiter = response.getString(2);
                imapConnection.settings.setPathDelimiter(hierarchyDelimiter);
                imapConnection.settings.setCombinedPrefix(null);

                if (K9MailLib.isDebug()) {
                    Timber.d("Got path delimiter '%s' for %s", imapConnection.settings.getPathDelimiter(), imapConnection.getLogId());
                }

                break;
            }
        }
    }
}
