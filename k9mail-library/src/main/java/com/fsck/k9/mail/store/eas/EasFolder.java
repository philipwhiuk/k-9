package com.fsck.k9.mail.store.eas;

import android.util.Log;

import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ByteArrayEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A EAS Folder
 */
public class EasFolder extends Folder<EasMessage> {
    private EasStore mStore;
    private String mName;
    private String mServerId;
    private int mType;
    private String mParentId = null;
    private boolean mIsOpen = false;
    private String mSyncKey = null;


    public EasFolder(EasStore store, String name, String serverId, int type) {
        mStore = store;
        mName = name;
        mServerId = serverId;
        mType = type;
    }

    protected EasStore getStore() {
        return mStore;
    }

    public String getParentId() {
        return mParentId;
    }

    public void setParentId(String parentId) {
        mParentId = parentId;
    }

    public String getSyncKey() throws MessagingException {
        if (mSyncKey == null) {
            Log.d(K9MailLib.LOG_TAG, "Reset SyncKey to 0");
            setSyncKey(INITIAL_SYNC_KEY);
        }
        return mSyncKey;
    }

    public void setSyncKey(String key) throws MessagingException {
        mSyncKey = key;
    }

    @Override
    public void open(int mode) throws MessagingException {
        mIsOpen = true;
    }

    @Override
    public Map<String, String> copyMessages(Message[] messages, Folder folder) throws MessagingException {
        moveOrCopyMessages(messages, folder.getName(), false);
        return null;
    }

    @Override
    public Map<String, String> moveMessages(Message[] messages, Folder folder) throws MessagingException {
        moveOrCopyMessages(messages, folder.getName(), true);
        return null;
    }

    @Override
    public void delete(Message[] msgs, String trashFolderName) throws MessagingException {
        String[] uids = new String[msgs.length];

        for (int i = 0, count = msgs.length; i < count; i++) {
            uids[i] = msgs[i].getUid();
        }

        deleteServerMessages(uids);
    }

    private void moveOrCopyMessages(Message[] messages, String folderName, boolean isMove)
            throws MessagingException {
        // EASTODO
    }

    @Override
    public int getMessageCount() throws MessagingException {
        return -1;
    }

    @Override
    public int getUnreadMessageCount() throws MessagingException {
        return -1;
    }

    @Override
    public int getFlaggedMessageCount() throws MessagingException {
        return -1;
    }

    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public int getMode() {
        return Folder.OPEN_MODE_RW;
    }

    @Override
    public String getRemoteName() {
        return mServerId;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public void close() {
        mIsOpen = false;
    }

    @Override
    public boolean create(Folder.FolderType type) throws MessagingException {
        return true;
    }

    @Override
    public void delete(boolean recursive) throws MessagingException {
        // EASTODO
        throw new Error("EasFolder.delete() not implemeneted");
    }

    @Override
    public Message getMessage(String uid) throws MessagingException {
        return new EasMessage(uid, this);
    }

    @Override
    public Message[] getMessages(int start, int end, Date earliestDate, MessageRetrievalListener listener)
            throws MessagingException {
        try {
            List<EasMessage> messages = getMessagesInternal(null, null, null, start, end);
            return messages.toArray(EMPTY_MESSAGE_ARRAY);
        } catch (IOException e) {
            throw new MessagingException("getMessages call failed", e);
        }
    }

    private List<EasMessage> getMessagesInternal(Message[] messages, FetchProfile fp, MessageRetrievalListener listener,
                                                 int start, int end) throws IOException, MessagingException {
        List<EasMessage> easMessages = new ArrayList<EasMessage>();
        Boolean moreAvailable = true;
        while (moreAvailable && easMessages.isEmpty()) {
            Serializer s = new Serializer();
            String syncKey = getSyncKey();

            s.start(Tags.SYNC_SYNC)
                    .start(Tags.SYNC_COLLECTIONS)
                    .start(Tags.SYNC_COLLECTION)
                    .data(Tags.SYNC_CLASS, "Email")
                    .data(Tags.SYNC_SYNC_KEY, syncKey)
                    .data(Tags.SYNC_COLLECTION_ID, mServerId);

            // Start with the default timeout
            int timeout = COMMAND_TIMEOUT;

            // EAS doesn't allow GetChanges in an initial sync; sending other options
            // appears to cause the server to delay its response in some cases, and this delay
            // can be long enough to result in an IOException and total failure to sync.
            // Therefore, we don't send any options with the initial sync.
            if (!syncKey.equals(INITIAL_SYNC_KEY)) {
                boolean fetchBodySane = (fp != null) && fp.contains(FetchProfile.Item.BODY_SANE);
                boolean fetchBody = (fp != null) && fp.contains(FetchProfile.Item.BODY);

                s.tag(Tags.SYNC_DELETES_AS_MOVES);

                // If messages is null, we only want to sync changes.
                if (messages == null) {
                    s.tag(Tags.SYNC_GET_CHANGES);
                }

                // Only fetch 10 messages at a time.
                s.data(Tags.SYNC_WINDOW_SIZE, Integer.toString(EMAIL_WINDOW_SIZE));

                // Handle options
                s.start(Tags.SYNC_OPTIONS);
                if (messages == null) {
                    // Set the time frame appropriately (EAS calls this a "filter") for all but Contacts.
                    s.data(Tags.SYNC_FILTER_TYPE, getEmailFilter());
                }
                // Enable MimeSupport
                s.data(Tags.SYNC_MIME_SUPPORT, "2");
                // Set the truncation amount for all classes.
                if (getProtocolVersionDouble() >= Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE) {
                    s.start(Tags.BASE_BODY_PREFERENCE)
                            // HTML for email; plain text for everything else.
                            .data(Tags.BASE_TYPE, Eas.BODY_PREFERENCE_MIME);

                    if (!fetchBody) {
                        String truncationSize = "0";
                        if (fetchBodySane) {
                            truncationSize = Eas.EAS12_TRUNCATION_SIZE;
                        }
                        s.data(Tags.BASE_TRUNCATION_SIZE, truncationSize);
                    }

                    s.end();
                } else {
                    String syncTruncation = "0";
                    if (fetchBody) {
                        syncTruncation = "8";
                    } else if (fetchBodySane) {
                        syncTruncation = "7";
                    }
                    s.data(Tags.SYNC_MIME_TRUNCATION, syncTruncation);
                }
                s.end();
            } else {
                // Use enormous timeout for initial sync, which empirically can take a while longer.
                timeout = 120 * 1000;
            }

            if (messages != null) {
                s.start(Tags.SYNC_COMMANDS);
                for (Message msg : messages) {
                    s.start(Tags.SYNC_FETCH);
                    s.data(Tags.SYNC_SERVER_ID, msg.getUid());
                    s.end();
                }
                s.end();
            }

            s.end().end().end().done();

            HttpResponse resp = sendHttpClientPost("Sync", new ByteArrayEntity(s.toByteArray()), timeout);
            try {
                int code = resp.getStatusLine().getStatusCode();
                if (code == HttpStatus.SC_OK) {
                    InputStream is = resp.getEntity().getContent();
                    if (is != null) {
                        EasEmailSyncParser parser = new EasEmailSyncParser(is, this, mAccount);
                        moreAvailable = parser.parse();
                        easMessages.addAll(parser.getMessages());

                        // If we got a new sync key from the server, make sure to update our member.
                        String newKey = parser.getNewSyncKey();
                        if (newKey != null) {
                            setSyncKey(newKey);
                        }
                    } else {
                        Log.d(K9MailLib.LOG_TAG, "Empty input stream in sync command response");
                    }
                } else {
                    if (isProvisionError(code)) {
                        throw new MessagingException("Provision error received while downloading messages");
                    } else if (isAuthError(code)) {
                        throw new MessagingException("Authentication error received while downloading messages");
                    } else {
                        throw new MessagingException("Unknown error received while downloading messages");
                    }
                }
            } finally {
                reclaimConnection(resp);
            }
        }
        return easMessages;
    }

    private String getEmailFilter() {
        String filter = Eas.FILTER_1_WEEK;
//            switch (mAccount.mSyncLookback) {
//                case com.android.email.Account.SYNC_WINDOW_1_DAY: {
//                    filter = Eas.FILTER_1_DAY;
//                    break;
//                }
//                case com.android.email.Account.SYNC_WINDOW_3_DAYS: {
//                    filter = Eas.FILTER_3_DAYS;
//                    break;
//                }
//                case com.android.email.Account.SYNC_WINDOW_1_WEEK: {
//                    filter = Eas.FILTER_1_WEEK;
//                    break;
//                }
//                case com.android.email.Account.SYNC_WINDOW_2_WEEKS: {
//                    filter = Eas.FILTER_2_WEEKS;
//                    break;
//                }
//                case com.android.email.Account.SYNC_WINDOW_1_MONTH: {
        filter = Eas.FILTER_1_MONTH;
//                    break;
//                }
//                case com.android.email.Account.SYNC_WINDOW_ALL: {
//                    filter = Eas.FILTER_ALL;
//                    break;
//                }
//            }
        return filter;
    }

    @Override
    public Message[] getMessages(MessageRetrievalListener listener) throws MessagingException {
        return getMessages(null, listener);
    }

    @Override
    public Message[] getMessages(String[] uids, MessageRetrievalListener listener) throws MessagingException {
        ArrayList<Message> messageList = new ArrayList<Message>();
        Message[] messages;

        if (uids == null ||
                uids.length == 0) {
            return messageList.toArray(EMPTY_MESSAGE_ARRAY);
        }

        for (int i = 0, count = uids.length; i < count; i++) {
            if (listener != null) {
                listener.messageStarted(uids[i], i, count);
            }

            EasMessage message = new EasMessage(uids[i], this);
            messageList.add(message);

            if (listener != null) {
                listener.messageFinished(message, i, count);
            }
        }
        messages = messageList.toArray(EMPTY_MESSAGE_ARRAY);

        return messages;
    }

    @Override
    public void fetch(Message[] messages, FetchProfile fp, MessageRetrievalListener listener)
            throws MessagingException {
        if (messages == null ||
                messages.length == 0) {
            return;
        }

        for (int i = 0, count = messages.length; i < count; i++) {
            if (!(messages[i] instanceof EasMessage)) {
                throw new MessagingException("EasStore fetch called with non-EasMessage");
            }
        }

        boolean fetchBodySane = fp.contains(FetchProfile.Item.BODY_SANE);
        boolean fetchBody = fp.contains(FetchProfile.Item.BODY);
        if (fetchBodySane || fetchBody) {
            try {
                messages = getMessagesInternal(messages, fp, listener, -1, -1).toArray(EMPTY_MESSAGE_ARRAY);
            } catch (IOException e) {
                throw new MessagingException("IO exception while fetching messages", e);
            }
        }

        for (int i = 0, count = messages.length; i < count; i++) {
            EasMessage easMessage = (EasMessage) messages[i];

            if (listener != null) {
                listener.messageStarted(easMessage.getUid(), i, count);
            }

            if (listener != null) {
                listener.messageFinished(easMessage, i, count);
            }
        }
    }

    @Override
    public void setFlags(Message[] messages, Flag[] flags, boolean value)
            throws MessagingException {
        String[] uids = new String[messages.length];

        for (int i = 0, count = messages.length; i < count; i++) {
            uids[i] = messages[i].getUid();
        }

        for (Flag flag : flags) {
            if (flag == Flag.SEEN) {
                markServerMessagesRead(uids, value);
            } else if (flag == Flag.DELETED) {
                deleteServerMessages(uids);
            }
        }
    }

    private void markServerMessagesRead(final String[] uids, final boolean read) throws MessagingException {
        new SyncCommand() {
            @Override
            void prepareCommand(Serializer s) throws IOException {
                s.start(Tags.SYNC_COMMANDS);
                for (String serverId : uids) {
                    s.start(Tags.SYNC_CHANGE)
                            .data(Tags.SYNC_SERVER_ID, serverId)
                            .start(Tags.SYNC_APPLICATION_DATA)
                            .data(Tags.EMAIL_READ, read ? "1" : "0")
                            .end()
                            .end();
                }
                s.end();
            }
        } .send(this);
    }

    private void deleteServerMessages(final String[] uids) throws MessagingException {
        new SyncCommand() {
            @Override
            void prepareCommand(Serializer s) throws IOException {
                s.tag(Tags.SYNC_DELETES_AS_MOVES);

                s.start(Tags.SYNC_COMMANDS);
                for (String serverId : uids) {
                    s.start(Tags.SYNC_DELETE)
                            .data(Tags.SYNC_SERVER_ID, serverId)
                            .end();
                }
                s.end();
            }
        } .send(this);
    }

    @Override
    public Map<String, String> appendMessages(Message[] messages) throws MessagingException {
        // EASTODO
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof EasFolder) {
            return mServerId.equals(((EasFolder)o).mServerId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mServerId.hashCode();
    }

    @Override
    public String getUidFromMessageId(Message message) throws MessagingException {
        Log.e(K9MailLib.LOG_TAG,
                "Unimplemented method getUidFromMessageId in WebDavStore.WebDavFolder could lead to duplicate messages "
                        + " being uploaded to the Sent folder");
        return null;
    }

    @Override
    public void setFlags(Flag[] flags, boolean value) throws MessagingException {
        Log.e(K9MailLib.LOG_TAG,
                "Unimplemented method setFlags(Flag[], boolean) breaks markAllMessagesAsRead and EmptyTrash");
        // Try to make this efficient by not retrieving all of the messages
    }

    @Override
    public String getPushState() {
        return mSyncKey;
    }

    @Override
    public void setPushState(String state) {
        mSyncKey = state;
    }

    @Override
    public String getNewPushState(String oldPushState, Message message) {
        return getPushState();
    }
}
