package com.fsck.k9.mail.store.imap;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessageRetrievalListener;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.filter.EOLConvertingOutputStream;
import com.fsck.k9.mail.filter.FixedLengthInputStream;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessageHelper;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.MimeUtility;

import static com.fsck.k9.mail.K9MailLib.LOG_TAG;
import static com.fsck.k9.mail.store.imap.ImapUtility.getLastResponse;


class ImapFolder extends Folder<ImapMessage> {
    private static final ThreadLocal<SimpleDateFormat> RFC3501_DATE = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
        }
    };
    private static final int MORE_MESSAGES_WINDOW_SIZE = 500;
    private static final int FETCH_WINDOW_SIZE = 100;


    protected volatile int messageCount = -1;
    protected volatile long uidNext = -1L;
    protected volatile ImapConnection connection;
    protected ImapStore store = null;
    protected Map<Long, String> msgSeqUidMap = new ConcurrentHashMap<Long, String>();
    private final FolderNameCodec folderNameCodec;
    private final String name;
    private int mode;
    private volatile boolean exists;
    private boolean inSearch = false;
    private boolean canCreateKeywords = false;


    public ImapFolder(ImapStore store, String name) {
        this(store, name, store.getFolderNameCodec());
    }

    ImapFolder(ImapStore store, String name, FolderNameCodec folderNameCodec) {
        super();
        this.store = store;
        this.name = name;
        this.folderNameCodec = folderNameCodec;
    }

    private String getPrefixedName() throws MessagingException {
        String prefixedName = "";

        if (!store.getStoreConfig().getInboxFolderName().equalsIgnoreCase(name)) {
            ImapConnection connection;
            synchronized (this) {
                if (this.connection == null) {
                    connection = store.getConnection();
                } else {
                    connection = this.connection;
                }
            }

            try {
                connection.open();
            } catch (IOException ioe) {
                throw new MessagingException("Unable to get IMAP prefix", ioe);
            } finally {
                if (this.connection == null) {
                    store.releaseConnection(connection);
                }
            }

            prefixedName = store.getCombinedPrefix();
        }

        prefixedName += name;

        return prefixedName;
    }

    private List<ImapResponse> executeSimpleCommand(String command) throws MessagingException, IOException {
        List<ImapResponse> responses = connection.executeSimpleCommand(command);
        return handleUntaggedResponses(responses);
    }

    @Override
    public void open(int mode) throws MessagingException {
        internalOpen(mode);

        if (messageCount == -1) {
            throw new MessagingException("Did not find message count during open");
        }
    }

    protected List<ImapResponse> internalOpen(int mode) throws MessagingException {
        if (isOpen() && this.mode == mode) {
            // Make sure the connection is valid. If it's not we'll close it down and continue
            // on to get a new one.
            try {
                return executeSimpleCommand(Commands.NOOP);
            } catch (IOException ioe) {
                /* don't throw */ //noinspection ThrowableResultOfMethodCallIgnored
                ioExceptionHandler(connection, ioe);
            }
        }

        store.releaseConnection(connection);

        synchronized (this) {
            connection = store.getConnection();
        }

        try {
            msgSeqUidMap.clear();

            String openCommand = mode == OPEN_MODE_RW ? "SELECT" : "EXAMINE";
            String encodedFolderName = folderNameCodec.encode(getPrefixedName());
            String escapedFolderName = ImapUtility.encodeString(encodedFolderName);
            String command = String.format("%s %s", openCommand, escapedFolderName);
            List<ImapResponse> responses = executeSimpleCommand(command);

            /*
             * If the command succeeds we expect the folder has been opened read-write unless we
             * are notified otherwise in the responses.
             */
            this.mode = mode;

            for (ImapResponse response : responses) {
                handlePermanentFlags(response);
            }
            handleSelectOrExamineOkResponse(getLastResponse(responses));

            exists = true;

            return responses;
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        } catch (MessagingException me) {
            Log.e(LOG_TAG, "Unable to open connection for " + getLogId(), me);
            throw me;
        }
    }

    private void handlePermanentFlags(ImapResponse response) {
        PermanentFlagsResponse permanentFlagsResponse = PermanentFlagsResponse.parse(response);
        if (permanentFlagsResponse == null) {
            return;
        }

        Set<Flag> permanentFlags = store.getPermanentFlagsIndex();
        permanentFlags.addAll(permanentFlagsResponse.getFlags());
        canCreateKeywords = permanentFlagsResponse.canCreateKeywords();
    }

    private void handleSelectOrExamineOkResponse(ImapResponse response) {
        SelectOrExamineResponse selectOrExamineResponse = SelectOrExamineResponse.parse(response);
        if (selectOrExamineResponse == null) {
            // This shouldn't happen
            return;
        }

        if (selectOrExamineResponse.hasOpenMode()) {
            mode = selectOrExamineResponse.getOpenMode();
        }
    }

    @Override
    public boolean isOpen() {
        return connection != null;
    }

    @Override
    public int getMode() {
        return mode;
    }

    @Override
    public void close() {
        messageCount = -1;

        if (!isOpen()) {
            return;
        }

        synchronized (this) {
            // If we are mid-search and we get a close request, we gotta trash the connection.
            if (inSearch && connection != null) {
                Log.i(LOG_TAG, "IMAP search was aborted, shutting down connection.");
                connection.close();
            } else {
                store.releaseConnection(connection);
            }

            connection = null;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    private boolean exists(String escapedFolderName) throws MessagingException {
        try {
            // Since we don't care about RECENT, we'll use that for the check, because we're checking
            // a folder other than ourself, and don't want any untagged responses to cause a change
            // in our own fields
            connection.executeSimpleCommand(String.format("STATUS %s (RECENT)", escapedFolderName));
            return true;
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        } catch (NegativeImapResponseException e) {
            return false;
        }
    }

    @Override
    public boolean exists() throws MessagingException {
        if (exists) {
            return true;
        }

        /*
         * This method needs to operate in the unselected mode as well as the selected mode
         * so we must get the connection ourselves if it's not there. We are specifically
         * not calling checkOpen() since we don't care if the folder is open.
         */
        ImapConnection connection;
        synchronized (this) {
            if (this.connection == null) {
                connection = store.getConnection();
            } else {
                connection = this.connection;
            }
        }

        try {
            String encodedFolderName = folderNameCodec.encode(getPrefixedName());
            String escapedFolderName = ImapUtility.encodeString(encodedFolderName);
            connection.executeSimpleCommand(String.format("STATUS %s (UIDVALIDITY)", escapedFolderName));

            exists = true;

            return true;
        } catch (NegativeImapResponseException e) {
            return false;
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        } finally {
            if (this.connection == null) {
                store.releaseConnection(connection);
            }
        }
    }

    @Override
    public boolean create(FolderType type) throws MessagingException {
        /*
         * This method needs to operate in the unselected mode as well as the selected mode
         * so we must get the connection ourselves if it's not there. We are specifically
         * not calling checkOpen() since we don't care if the folder is open.
         */
        ImapConnection connection;
        synchronized (this) {
            if (this.connection == null) {
                connection = store.getConnection();
            } else {
                connection = this.connection;
            }
        }

        try {
            String encodedFolderName = folderNameCodec.encode(getPrefixedName());
            String escapedFolderName = ImapUtility.encodeString(encodedFolderName);
            connection.executeSimpleCommand(String.format("CREATE %s", escapedFolderName));

            return true;
        } catch (NegativeImapResponseException e) {
            return false;
        } catch (IOException ioe) {
            throw ioExceptionHandler(this.connection, ioe);
        } finally {
            if (this.connection == null) {
                store.releaseConnection(connection);
            }
        }
    }

    /**
     * Copies the given messages to the specified folder.
     *
     * <p>
     * <strong>Note:</strong>
     * Only the UIDs of the given {@link Message} instances are used. It is assumed that all
     * UIDs represent valid messages in this folder.
     * </p>
     *
     * @param messages
     *         The messages to copy to the specfied folder.
     * @param folder
     *         The name of the target folder.
     *
     * @return The mapping of original message UIDs to the new server UIDs.
     */
    @Override
    public Map<String, String> copyMessages(List<? extends Message> messages, Folder folder)
            throws MessagingException {

        if (!(folder instanceof ImapFolder)) {
            throw new MessagingException("ImapFolder.copyMessages passed non-ImapFolder");
        }
        if (messages.isEmpty()) {
            return null;
        }
        ImapFolder imapFolder = (ImapFolder) folder;
        checkOpen(); //only need READ access
        String[] uids = new String[messages.size()];
        for (int i = 0, count = messages.size(); i < count; i++) {
            uids[i] = messages.get(i).getUid();
        }
        try {
            return performCopy(imapFolder, uids);
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    /**
     * Actually performs the copy based on message UIDs
     * @return The UID mapping from the copy response
     * @throws MessagingException
     * @throws IOException
     */
    @Nullable
    private Map<String,String> performCopy(ImapFolder imapFolder, String[] uids)
            throws MessagingException, IOException {
        String encodedDestinationFolderName = folderNameCodec.encode(imapFolder.getPrefixedName());
        String escapedDestinationFolderName = ImapUtility.encodeString(encodedDestinationFolderName);

        //TODO: Try to copy/move the messages first and only create the folder if the
        //      operation fails. This will save a roundtrip if the folder already exists.

        createFolderIfNotExists(imapFolder, escapedDestinationFolderName);

        //TODO: Split this into multiple commands if the command exceeds a certain length.
        List<ImapResponse> responses = executeSimpleCommand(String.format("UID COPY %s %s",
                combine(uids, ','), escapedDestinationFolderName));

        // Get the tagged response for the UID COPY command
        ImapResponse response = getLastResponse(responses);

        CopyUidResponse copyUidResponse = CopyUidResponse.parse(response);
        if (copyUidResponse == null) {
            return null;
        }

        return copyUidResponse.getUidMapping();
    }

    private void createFolderIfNotExists(
            ImapFolder imapFolder, String escapedDestinationFolderName) throws MessagingException {
        if (!exists(escapedDestinationFolderName)) {
            if (K9MailLib.isDebug()) {
                Log.i(LOG_TAG, "ImapFolder.copyMessages: attempting to create remote folder '" +
                        escapedDestinationFolderName + "' for " + getLogId());
            }

            imapFolder.create(FolderType.HOLDS_MESSAGES);
        }
    }

    @Override
    public Map<String, String> moveMessages(List<? extends Message> messages, Folder folder) throws MessagingException {
        if (messages.isEmpty()) {
            return null;
        }

        Map<String, String> uidMapping = copyMessages(messages, folder);

        setFlags(messages, Collections.singleton(Flag.DELETED), true);

        return uidMapping;
    }

    @Override
    public void delete(List<? extends Message> messages, String trashFolderName) throws MessagingException {
        if (messages.isEmpty()) {
            return;
        }

        if (trashFolderName == null || getName().equalsIgnoreCase(trashFolderName)) {
            setFlags(messages, Collections.singleton(Flag.DELETED), true);
        } else {
            ImapFolder remoteTrashFolder = getStore().getFolder(trashFolderName);
            String encodedTrashFolderName = folderNameCodec.encode(remoteTrashFolder.getPrefixedName());
            String escapedTrashFolderName = ImapUtility.encodeString(encodedTrashFolderName);

            if (!exists(escapedTrashFolderName)) {
                if (K9MailLib.isDebug()) {
                    Log.i(LOG_TAG, "IMAPMessage.delete: attempting to create remote '" + trashFolderName + "' folder " +
                            "for " + getLogId());
                }
                remoteTrashFolder.create(FolderType.HOLDS_MESSAGES);
            }

            if (exists(escapedTrashFolderName)) {
                if (K9MailLib.isDebug()) {
                    Log.d(LOG_TAG, "IMAPMessage.delete: copying remote " + messages.size() + " messages to '" +
                            trashFolderName + "' for " + getLogId());
                }

                moveMessages(messages, remoteTrashFolder);
            } else {
                throw new MessagingException("IMAPMessage.delete: remote Trash folder " + trashFolderName +
                        " does not exist and could not be created for " + getLogId(), true);
            }
        }
    }

    @Override
    public int getMessageCount() {
        return messageCount;
    }

    private int getRemoteMessageCount(String criteria) throws MessagingException {
        checkOpen();

        try {
            int count = 0;
            int start = 1;

            String command = String.format(Locale.US, "SEARCH %d:* %s", start, criteria);
            List<ImapResponse> responses = executeSimpleCommand(command);

            for (ImapResponse response : responses) {
                if (ImapResponseParser.equalsIgnoreCase(response.get(0), "SEARCH")) {
                    count += response.size() - 1;
                }
            }

            return count;
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    @Override
    public int getUnreadMessageCount() throws MessagingException {
        return getRemoteMessageCount("UNSEEN NOT DELETED");
    }

    @Override
    public int getFlaggedMessageCount() throws MessagingException {
        return getRemoteMessageCount("FLAGGED NOT DELETED");
    }

    protected long getHighestUid() throws MessagingException {
        try {
            String command = "UID SEARCH *:*";
            List<ImapResponse> responses = executeSimpleCommand(command);

            SearchResponse searchResponse = SearchResponse.parse(responses);

            return extractHighestUid(searchResponse);
        } catch (NegativeImapResponseException e) {
            return -1L;
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    private long extractHighestUid(SearchResponse searchResponse) {
        List<Long> uids = searchResponse.getNumbers();
        if (uids.isEmpty()) {
            return -1L;
        }

        if (uids.size() == 1) {
            return uids.get(0);
        }

        Collections.sort(uids, Collections.reverseOrder());

        return uids.get(0);
    }

    @Override
    public void delete(boolean recurse) throws MessagingException {
        throw new Error("ImapStore.delete() not yet implemented");
    }

    @Override
    public ImapMessage getMessage(String uid) throws MessagingException {
        return new ImapMessage(uid, this);
    }

    @Override
    public List<ImapMessage> getMessages(int start, int end, Date earliestDate,
            MessageRetrievalListener<ImapMessage> listener) throws MessagingException {
        return getMessages(start, end, earliestDate, false, listener);
    }

    protected List<ImapMessage> getMessages(final int start, final int end, Date earliestDate,
            final boolean includeDeleted, final MessageRetrievalListener<ImapMessage> listener)
            throws MessagingException {

        if (start < 1 || end < 1 || end < start) {
            throw new MessagingException(String.format(Locale.US, "Invalid message set %d %d", start, end));
        }

        final String dateSearchString = getDateSearchString(earliestDate);

        ImapSearcher searcher = new ImapSearcher() {
            @Override
            public List<ImapResponse> search() throws IOException, MessagingException {
                String command = String.format(Locale.US, "UID SEARCH %d:%d%s%s", start, end, dateSearchString,
                        includeDeleted ? "" : " NOT DELETED");

                return executeSimpleCommand(command);
            }
        };

        return search(searcher, listener);
    }

    private String getDateSearchString(Date earliestDate) {
        if (earliestDate == null) {
            return "";
        }

        return " SINCE " + RFC3501_DATE.get().format(earliestDate);
    }

    @Override
    public boolean areMoreMessagesAvailable(int indexOfOldestMessage, Date earliestDate) throws IOException,
            MessagingException {

        checkOpen();

        if (indexOfOldestMessage == 1) {
            return false;
        }

        String dateSearchString = getDateSearchString(earliestDate);
        int endIndex = indexOfOldestMessage - 1;

        while (endIndex > 0) {
            int startIndex = Math.max(0, endIndex - MORE_MESSAGES_WINDOW_SIZE) + 1;

            if (existsNonDeletedMessageInRange(startIndex, endIndex, dateSearchString)) {
                return true;
            }

            endIndex = endIndex - MORE_MESSAGES_WINDOW_SIZE;
        }

        return false;
    }

    private boolean existsNonDeletedMessageInRange(int startIndex, int endIndex, String dateSearchString)
            throws MessagingException, IOException {

        String command = String.format(Locale.US, "SEARCH %d:%d%s NOT DELETED", startIndex, endIndex, dateSearchString);
        List<ImapResponse> responses = executeSimpleCommand(command);

        for (ImapResponse response : responses) {
            if (response.getTag() == null && ImapResponseParser.equalsIgnoreCase(response.get(0), "SEARCH")) {
                if (response.size() > 1) {
                    return true;
                }
            }
        }

        return false;
    }

    protected List<ImapMessage> getMessages(final List<Long> mesgSeqs, final boolean includeDeleted,
            final MessageRetrievalListener<ImapMessage> listener) throws MessagingException {
        ImapSearcher searcher = new ImapSearcher() {
            @Override
            public List<ImapResponse> search() throws IOException, MessagingException {
                String command = String.format("UID SEARCH %s%s", combine(mesgSeqs.toArray(), ','),
                        includeDeleted ? "" : " NOT DELETED");

                return executeSimpleCommand(command);
            }
        };

        return search(searcher, listener);
    }

    protected List<ImapMessage> getMessagesFromUids(final List<String> mesgUids) throws MessagingException {
        ImapSearcher searcher = new ImapSearcher() {
            @Override
            public List<ImapResponse> search() throws IOException, MessagingException {
                String command = String.format("UID SEARCH UID %s", combine(mesgUids.toArray(), ','));

                return executeSimpleCommand(command);
            }
        };

        return search(searcher, null);
    }

    private List<ImapMessage> search(ImapSearcher searcher, MessageRetrievalListener<ImapMessage> listener)
            throws MessagingException {
        checkOpen();

        List<ImapMessage> messages = new ArrayList<>();
        try {
            List<ImapResponse> responses = searcher.search();

            SearchResponse searchResponse = SearchResponse.parse(responses);
            List<Long> uids = searchResponse.getNumbers();

            // Sort the uids in numerically decreasing order
            // By doing it in decreasing order, we ensure newest messages are dealt with first
            // This makes the most sense when a limit is imposed, and also prevents UI from going
            // crazy adding stuff at the top.
            Collections.sort(uids, Collections.reverseOrder());

            for (int i = 0, count = uids.size(); i < count; i++) {
                String uid = uids.get(i).toString();
                if (listener != null) {
                    listener.messageStarted(uid, i, count);
                }

                ImapMessage message = new ImapMessage(uid, this);
                messages.add(message);

                if (listener != null) {
                    listener.messageFinished(message, i, count);
                }
            }
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }

        return messages;
    }

    @Override
    public void fetch(List<ImapMessage> messages, FetchProfile fetchProfile,
            MessageRetrievalListener<ImapMessage> listener) throws MessagingException {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        checkOpen();

        List<String> uids = new ArrayList<>(messages.size());
        HashMap<String, Message> messageMap = new HashMap<>();
        for (Message message : messages) {
            String uid = message.getUid();
            uids.add(uid);
            messageMap.put(uid, message);
        }

        Set<String> fetchFields = ImapFetchFieldsBuilder.fromFetchProfile(fetchProfile,
                store.getStoreConfig().getMaximumAutoDownloadMessageSize());

        String spaceSeparatedFetchFields = combine(fetchFields.toArray(new String[fetchFields.size()]), ' ');

        for (int windowStart = 0; windowStart < messages.size(); windowStart += (FETCH_WINDOW_SIZE)) {
            int windowEnd = Math.min(windowStart + FETCH_WINDOW_SIZE, messages.size());
            List<String> uidWindow = uids.subList(windowStart, windowEnd);

            try {
                String commaSeparatedUids = combine(uidWindow.toArray(new String[uidWindow.size()]), ',');
                String command = String.format("UID FETCH %s (%s)", commaSeparatedUids, spaceSeparatedFetchFields);
                connection.sendCommand(command, false);

                ImapResponse response;
                int messageNumber = 0;

                ImapResponseCallback callback = null;
                if (fetchProfile.contains(FetchProfile.Item.BODY) ||
                        fetchProfile.contains(FetchProfile.Item.BODY_SANE)) {
                    callback = new FetchBodyCallback(messageMap);
                }

                do {
                    response = connection.readResponse(callback);

                    if (response.getTag() == null && ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {

                        ImapList fetchList = (ImapList) response.getKeyedValue("FETCH");
                        String uid = fetchList.getKeyedString("UID");
                        long msgSeq = response.getLong(0);
                        if (uid != null) {
                            try {
                                msgSeqUidMap.put(msgSeq, uid);
                                if (K9MailLib.isDebug()) {
                                    Log.v(LOG_TAG, "Stored uid '" + uid + "' for msgSeq " + msgSeq + " into map");
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "Unable to store uid '" + uid + "' for msgSeq " + msgSeq);
                            }
                        }

                        Message message = messageMap.get(uid);
                        if (message == null) {
                            if (K9MailLib.isDebug()) {
                                Log.d(LOG_TAG, "Do not have message in messageMap for UID " + uid + " for " +
                                        getLogId());
                            }

                            handleUntaggedResponse(response);
                            continue;
                        }

                        if (listener != null) {
                            listener.messageStarted(uid, messageNumber++, messageMap.size());
                        }

                        ImapMessage imapMessage = (ImapMessage) message;
                        Object body = handleFetchResponse(imapMessage, fetchList);

                        handleParsedBody(imapMessage, body);


                        if (listener != null) {
                            listener.messageFinished(imapMessage, messageNumber, messageMap.size());
                        }
                    } else {
                        handleUntaggedResponse(response);
                    }

                } while (response.getTag() == null);
            } catch (IOException ioe) {
                throw ioExceptionHandler(connection, ioe);
            }
        }
    }

    /**
     * We expect one of 2 types for body:
     *
     * <ul>
     *     <li>A raw string, indicating that we still need to parse the body onto the message</li>
     *     <li>An integer (specifically the value 1) used as a return code from
     *          {@link FetchBodyCallback#foundLiteral(ImapResponse, FixedLengthInputStream)}</li>
     * </ul>
     *
     * @param imapMessage The message to parse the body onto.
     * @param body The body (or placeholder value)
     * @throws IOException
     * @throws MessagingException
     */
    private void handleParsedBody(ImapMessage imapMessage, Object body) throws IOException, MessagingException {
        if (body != null) {
            if (body instanceof String) {
                String bodyString = (String) body;
                InputStream bodyStream = new ByteArrayInputStream(bodyString.getBytes());
                imapMessage.parse(bodyStream);
            } else if (body instanceof Integer) {
                // All the work was done in FetchBodyCallback.foundLiteral()
            } else {
                // This shouldn't happen
                throw new MessagingException("Got FETCH response with bogus parameters");
            }
        }
    }

    @Override
    public void fetchPart(Message message, Part part, MessageRetrievalListener<Message> listener)
            throws MessagingException {
        checkOpen();

        String fetchArgument = buildFetchArgument(part.getServerExtra());

        try {
            String command = String.format("UID FETCH %s (UID %s)", message.getUid(), fetchArgument);
            connection.sendCommand(command, false);

            ImapResponse response;
            int messageNumber = 0;

            ImapResponseCallback callback = new FetchPartCallback(part);

            do {
                response = connection.readResponse(callback);

                if (response.getTag() == null && ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
                    ImapList fetchList = (ImapList) response.getKeyedValue("FETCH");
                    String uid = fetchList.getKeyedString("UID");

                    if (!message.getUid().equals(uid)) {
                        if (K9MailLib.isDebug()) {
                            Log.d(LOG_TAG, "Did not ask for UID " + uid + " for " + getLogId());
                        }

                        handleUntaggedResponse(response);
                        continue;
                    }

                    if (listener != null) {
                        listener.messageStarted(uid, messageNumber++, 1);
                    }

                    ImapMessage imapMessage = (ImapMessage) message;
                    Object body = handleFetchResponse(imapMessage, fetchList);
                    setPartBody(body, part);

                    if (listener != null) {
                        listener.messageFinished(message, messageNumber, 1);
                    }
                } else {
                    handleUntaggedResponse(response);
                }

            } while (response.getTag() == null);
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    private String buildFetchArgument(String partId) {
        if ("TEXT".equalsIgnoreCase(partId)) {
            int maximumAutoDownloadMessageSize = store.getStoreConfig().getMaximumAutoDownloadMessageSize();
            return String.format(Locale.US, "BODY.PEEK[TEXT]<0.%d>", maximumAutoDownloadMessageSize);
        } else {
            return String.format("BODY.PEEK[%s]", partId);
        }

    }

    private void setPartBody(Object body, Part part) throws MessagingException, IOException {
        if (body != null) {
            if (body instanceof Body) {
                // Most of the work for this is done in FetchAttachmentCallback.foundLiteral()
                MimeMessageHelper.setBody(part, (Body) body);
            } else if (body instanceof String) {
                String bodyString = (String) body;
                InputStream bodyStream = new ByteArrayInputStream(bodyString.getBytes());

                String contentTransferEncoding =
                        part.getHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING)[0];
                String contentType = part.getHeader(MimeHeader.HEADER_CONTENT_TYPE)[0];
                MimeMessageHelper.setBody(part, MimeUtility.createBody(bodyStream, contentTransferEncoding,
                        contentType));
            } else {
                // This shouldn't happen
                throw new MessagingException("Got FETCH response with bogus parameters");
            }
        }
    }

    /**
     * @param message Message to populate with details from IMAP FETCH
     * @param fetchList An IMAP LIST response which contains the result of the FETCH
     * @return The body of the part if requested (or null)
     * @throws MessagingException
     */
    private Object handleFetchResponse(ImapMessage message, ImapList fetchList) throws MessagingException {
        Object body = null;
        if (fetchList.containsKey("FLAGS")) {
            handleFlagsFromFetchResponse(message, fetchList.getKeyedList("FLAGS"));
        }
        if (fetchList.containsKey("INTERNALDATE")) {
            Date internalDate = fetchList.getKeyedDate("INTERNALDATE");
            message.setInternalDate(internalDate);
        }
        if (fetchList.containsKey("RFC822.SIZE")) {
            int size = fetchList.getKeyedNumber("RFC822.SIZE");
            message.setSize(size);
        }
        if (fetchList.containsKey("BODYSTRUCTURE")) {
            ImapList bs = fetchList.getKeyedList("BODYSTRUCTURE");
            if (bs != null) {
                try {
                    ImapBodyStructureParser.parseBodyStructure(bs, message, "TEXT");
                } catch (MessagingException e) {
                    if (K9MailLib.isDebug()) {
                        Log.d(LOG_TAG, "Error handling message for " + getLogId(), e);
                    }
                    message.setBody(null);
                }
            }
        }
        if (fetchList.containsKey("BODY")) {
            body = handleBodyFromFetchResponse(message, fetchList);
        }
        return body;
    }

    private void handleFlagsFromFetchResponse(ImapMessage message, ImapList flags) throws MessagingException {
        if (flags != null) {
            for (int i = 0, count = flags.size(); i < count; i++) {
                String flag = flags.getString(i);
                if (flag.equalsIgnoreCase("\\Deleted")) {
                    message.setFlagInternal(Flag.DELETED, true);
                } else if (flag.equalsIgnoreCase("\\Answered")) {
                    message.setFlagInternal(Flag.ANSWERED, true);
                } else if (flag.equalsIgnoreCase("\\Seen")) {
                    message.setFlagInternal(Flag.SEEN, true);
                } else if (flag.equalsIgnoreCase("\\Flagged")) {
                    message.setFlagInternal(Flag.FLAGGED, true);
                } else if (flag.equalsIgnoreCase("$Forwarded")) {
                    message.setFlagInternal(Flag.FORWARDED, true);
                        /* a message contains FORWARDED FLAG -> so we can also create them */
                    store.getPermanentFlagsIndex().add(Flag.FORWARDED);
                }
            }
        }
    }

    private Object handleBodyFromFetchResponse(ImapMessage message, ImapList fetchList) {
        Object body = null;
        int index = fetchList.getKeyIndex("BODY") + 2;
        int size = fetchList.size();
        if (index < size) {
            body = fetchList.getObject(index);

            // Check if there's an origin octet
            if (body instanceof String) {
                String originOctet = (String) body;
                if (originOctet.startsWith("<") && (index + 1) < size) {
                    body = fetchList.getObject(index + 1);
                }
            }
        }
        return body;
    }

    protected List<ImapResponse> handleUntaggedResponses(List<ImapResponse> responses) {
        for (ImapResponse response : responses) {
            handleUntaggedResponse(response);
        }

        return responses;
    }

    protected void handlePossibleUidNext(ImapResponse response) {
        if (ImapResponseParser.equalsIgnoreCase(response.get(0), "OK") && response.size() > 1) {
            Object bracketedObj = response.get(1);
            if (bracketedObj instanceof ImapList) {
                ImapList bracketed = (ImapList) bracketedObj;

                if (bracketed.size() > 1) {
                    Object keyObj = bracketed.get(0);
                    if (keyObj instanceof String) {
                        String key = (String) keyObj;
                        if ("UIDNEXT".equalsIgnoreCase(key)) {
                            uidNext = bracketed.getLong(1);
                            if (K9MailLib.isDebug()) {
                                Log.d(LOG_TAG, "Got UidNext = " + uidNext + " for " + getLogId());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle an untagged response that the caller doesn't care to handle themselves.
     */
    protected void handleUntaggedResponse(ImapResponse response) {
        if (response.getTag() == null && response.size() > 1) {
            if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXISTS")) {
                messageCount = response.getNumber(0);
                if (K9MailLib.isDebug()) {
                    Log.d(LOG_TAG, "Got untagged EXISTS with value " + messageCount + " for " + getLogId());
                }
            }

            handlePossibleUidNext(response);

            if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXPUNGE") && messageCount > 0) {
                messageCount--;
                if (K9MailLib.isDebug()) {
                    Log.d(LOG_TAG, "Got untagged EXPUNGE with messageCount " + messageCount + " for " + getLogId());
                }
            }
        }
    }

    /**
     * Appends the given messages to the selected folder.
     *
     * <p>
     * This implementation also determines the new UIDs of the given messages on the IMAP
     * server and changes the messages' UIDs to the new server UIDs.
     * </p>
     *
     * @param messages
     *         The messages to append to the folder.
     *
     * @return The mapping of original message UIDs to the new server UIDs.
     */
    @Override
    public Map<String, String> appendMessages(List<? extends Message> messages) throws MessagingException {
        open(OPEN_MODE_RW);
        checkOpen();

        try {
            Map<String, String> uidMap = new HashMap<>();
            for (Message message : messages) {
                long messageSize = message.calculateSize();

                String encodeFolderName = folderNameCodec.encode(getPrefixedName());
                String escapedFolderName = ImapUtility.encodeString(encodeFolderName);
                String command = String.format(Locale.US, "APPEND %s (%s) {%d}", escapedFolderName,
                        combineFlags(message.getFlags()), messageSize);
                connection.sendCommand(command, false);

                ImapResponse response;
                do {
                    response = connection.readResponse();

                    handleUntaggedResponse(response);

                    if (response.isContinuationRequested()) {
                        EOLConvertingOutputStream eolOut = new EOLConvertingOutputStream(connection.getOutputStream());
                        message.writeTo(eolOut);
                        eolOut.write('\r');
                        eolOut.write('\n');
                        eolOut.flush();
                    }
                } while (response.getTag() == null);

                if (response.size() > 1) {
                    /*
                     * If the server supports UIDPLUS, then along with the APPEND response it
                     * will return an APPENDUID response code, e.g.
                     *
                     * 11 OK [APPENDUID 2 238268] APPEND completed
                     *
                     * We can use the UID included in this response to update our records.
                     */
                    Object responseList = response.get(1);

                    if (responseList instanceof ImapList) {
                        ImapList appendList = (ImapList) responseList;
                        if (appendList.size() >= 3 && appendList.getString(0).equals("APPENDUID")) {
                            String newUid = appendList.getString(2);

                            if (!TextUtils.isEmpty(newUid)) {
                                message.setUid(newUid);
                                uidMap.put(message.getUid(), newUid);
                                continue;
                            }
                        }
                    }
                }

                /*
                 * This part is executed in case the server does not support UIDPLUS or does
                 * not implement the APPENDUID response code.
                 */
                String newUid = getUidFromMessageId(message);
                if (K9MailLib.isDebug()) {
                    Log.d(LOG_TAG, "Got UID " + newUid + " for message for " + getLogId());
                }

                if (!TextUtils.isEmpty(newUid)) {
                    uidMap.put(message.getUid(), newUid);
                    message.setUid(newUid);
                }
            }

            /*
             * We need uidMap to be null if new UIDs are not available to maintain consistency
             * with the behavior of other similar methods (copyMessages, moveMessages) which
             * return null.
             */
            return (uidMap.isEmpty()) ? null : uidMap;
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    @Override
    public String getUidFromMessageId(Message message) throws MessagingException {
        try {
            /*
            * Try to find the UID of the message we just appended using the
            * Message-ID header.
            */
            String[] messageIdHeader = message.getHeader("Message-ID");

            if (messageIdHeader.length == 0) {
                if (K9MailLib.isDebug()) {
                    Log.d(LOG_TAG, "Did not get a message-id in order to search for UID  for " + getLogId());
                }
                return null;
            }

            String messageId = messageIdHeader[0];
            if (K9MailLib.isDebug()) {
                Log.d(LOG_TAG, "Looking for UID for message with message-id " + messageId + " for " + getLogId());
            }

            String command = String.format("UID SEARCH HEADER MESSAGE-ID %s", ImapUtility.encodeString(messageId));
            List<ImapResponse> responses = executeSimpleCommand(command);

            for (ImapResponse response : responses) {
                if (response.getTag() == null && ImapResponseParser.equalsIgnoreCase(response.get(0), "SEARCH")
                        && response.size() > 1) {
                    return response.getString(1);
                }
            }

            return null;
        } catch (IOException ioe) {
            throw new MessagingException("Could not find UID for message based on Message-ID", ioe);
        }
    }

    @Override
    public void expunge() throws MessagingException {
        open(OPEN_MODE_RW);
        checkOpen();

        try {
            executeSimpleCommand("EXPUNGE");
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    private String combineFlags(Iterable<Flag> flags) {
        List<String> flagNames = new ArrayList<String>();
        for (Flag flag : flags) {
            if (flag == Flag.SEEN) {
                flagNames.add("\\Seen");
            } else if (flag == Flag.DELETED) {
                flagNames.add("\\Deleted");
            } else if (flag == Flag.ANSWERED) {
                flagNames.add("\\Answered");
            } else if (flag == Flag.FLAGGED) {
                flagNames.add("\\Flagged");
            } else if (flag == Flag.FORWARDED
                    && (canCreateKeywords || store.getPermanentFlagsIndex().contains(Flag.FORWARDED))) {
                flagNames.add("$Forwarded");
            }
        }

        return combine(flagNames.toArray(new String[flagNames.size()]), ' ');
    }

    @Override
    public void setFlags(Set<Flag> flags, boolean value) throws MessagingException {
        open(OPEN_MODE_RW);
        checkOpen();

        try {
            String command = String.format("UID STORE 1:* %sFLAGS.SILENT (%s)", value ? "+" : "-", combineFlags(flags));
            executeSimpleCommand(command);
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    @Override
    public String getNewPushState(String oldSerializedPushState, Message message) {
        try {
            String uid = message.getUid();
            long messageUid = Long.parseLong(uid);

            ImapPushState oldPushState = ImapPushState.parse(oldSerializedPushState);

            if (messageUid >= oldPushState.uidNext) {
                long uidNext = messageUid + 1;
                ImapPushState newPushState = new ImapPushState(uidNext);

                return newPushState.toString();
            } else {
                return null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception while updated push state for " + getLogId(), e);
            return null;
        }
    }

    @Override
    public void setFlags(List<? extends Message> messages, final Set<Flag> flags, boolean value)
            throws MessagingException {
        open(OPEN_MODE_RW);
        checkOpen();

        String[] uids = new String[messages.size()];
        for (int i = 0, count = messages.size(); i < count; i++) {
            uids[i] = messages.get(i).getUid();
        }

        try {
            String command = String.format("UID STORE %s %sFLAGS.SILENT (%s)", combine(uids, ','), value ? "+" : "-",
                    combineFlags(flags));
            executeSimpleCommand(command);
        } catch (IOException ioe) {
            throw ioExceptionHandler(connection, ioe);
        }
    }

    private void checkOpen() throws MessagingException {
        if (!isOpen()) {
            throw new MessagingException("Folder " + getPrefixedName() + " is not open.");
        }
    }

    private MessagingException ioExceptionHandler(ImapConnection connection, IOException ioe) {
        Log.e(LOG_TAG, "IOException for " + getLogId(), ioe);

        if (connection != null) {
            connection.close();
        }

        close();

        return new MessagingException("IO Error", ioe);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ImapFolder) {
            ImapFolder otherFolder = (ImapFolder) other;
            return otherFolder.getName().equalsIgnoreCase(getName());
        }

        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    private ImapStore getStore() {
        return store;
    }

    protected String getLogId() {
        String id = store.getStoreConfig().toString() + ":" + getName() + "/" + Thread.currentThread().getName();
        if (connection != null) {
            id += "/" + connection.getLogId();
        }

        return id;
    }

    /**
     * Search the remote ImapFolder.
     * @param queryString String to query for.
     * @param requiredFlags Mandatory flags
     * @param forbiddenFlags Flags to exclude
     * @return List of messages found
     * @throws MessagingException On any error.
     */
    @Override
    public List<ImapMessage> search(final String queryString, final Set<Flag> requiredFlags,
            final Set<Flag> forbiddenFlags) throws MessagingException {

        if (!store.getStoreConfig().allowRemoteSearch()) {
            throw new MessagingException("Your settings do not allow remote searching of this account");
        }

        // Setup the searcher
        final ImapSearcher searcher = new ImapSearcher() {
            @Override
            public List<ImapResponse> search() throws IOException, MessagingException {
                String imapQuery = ImapQueryBuilder.buildQuery(
                        requiredFlags, forbiddenFlags, queryString,
                        store.getStoreConfig().isRemoteSearchFullText());
                return executeSimpleCommand(imapQuery);
            }
        };

        try {
            open(OPEN_MODE_RO);
            checkOpen();

            inSearch = true;

            return search(searcher, null);
        } finally {
            inSearch = false;
        }
    }

    private static String combine(Object[] parts, char separator) {
        if (parts == null) {
            return null;
        }

        return TextUtils.join(String.valueOf(separator), parts);
    }
}
