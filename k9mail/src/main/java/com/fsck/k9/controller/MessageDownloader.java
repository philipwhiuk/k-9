package com.fsck.k9.controller;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.AccountStats;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.activity.MessageReference;
import com.fsck.k9.mail.BodyFactory;
import com.fsck.k9.mail.DefaultBodyFactory;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessageRetrievalListener;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MessageExtractor;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.MessageRemovalListener;
import com.fsck.k9.notification.NotificationController;
import timber.log.Timber;


public class MessageDownloader {

    /**
     * Fetches the messages described by inputMessages from the remote store and writes them to
     * local storage.
     *
     * @param account
     *         The account the remote store belongs to.
     * @param remoteFolder
     *         The remote folder to download messages from.
     * @param localFolder
     *         The {@link LocalFolder} instance corresponding to the remote folder.
     * @param inputMessages
     *         A list of messages objects that store the UIDs of which messages to download.
     * @param flagSyncOnly
     *         Only flags will be fetched from the remote store if this is {@code true}.
     * @param purgeToVisibleLimit
     *         If true, local messages will be purged down to the limit of visible messages.
     *
     * @return The number of downloaded messages that are not flagged as {@link Flag#SEEN}.
     *
     * @throws MessagingException
     */
    public int downloadMessages(
            final IMessageController controller,
            final NotificationController notificationController,
            final Context context, final Account account, final Folder remoteFolder,
            final LocalFolder localFolder, List<Message> inputMessages,
            boolean flagSyncOnly, boolean purgeToVisibleLimit,
            final Set<MessagingListener> listeners) throws MessagingException {

        final Date earliestDate = account.getEarliestPollDate();
        Date downloadStarted = new Date(); // now

        if (earliestDate != null) {
            Timber.d("Only syncing messages after %s", earliestDate);
        }
        final String folder = remoteFolder.getName();

        int unreadBeforeStart = 0;
        try {
            AccountStats stats = account.getStats(context);
            unreadBeforeStart = stats.unreadMessageCount;

        } catch (MessagingException e) {
            Timber.e(e, "Unable to getUnreadMessageCount for account: %s", account);
        }

        List<Message> syncFlagMessages = new ArrayList<>();
        List<Message> unsyncedMessages = new ArrayList<>();
        final AtomicInteger newMessages = new AtomicInteger(0);

        List<Message> messages = new ArrayList<>(inputMessages);

        for (Message message : messages) {
            evaluateMessageForDownload(listeners, message, folder, localFolder, remoteFolder, account, unsyncedMessages,
                    syncFlagMessages, flagSyncOnly);
        }

        final AtomicInteger progress = new AtomicInteger(0);
        final int todo = unsyncedMessages.size() + syncFlagMessages.size();
        for (MessagingListener l : listeners) {
            l.synchronizeMailboxProgress(account, folder, progress.get(), todo);
        }

        Timber.d("SYNC: Have %d unsynced messages", unsyncedMessages.size());

        messages.clear();
        final List<Message> largeMessages = new ArrayList<>();
        final List<Message> smallMessages = new ArrayList<>();
        if (!unsyncedMessages.isEmpty()) {
            int visibleLimit = localFolder.getVisibleLimit();
            int listSize = unsyncedMessages.size();

            if ((visibleLimit > 0) && (listSize > visibleLimit)) {
                unsyncedMessages = unsyncedMessages.subList(0, visibleLimit);
            }

            if (!unsyncedMessages.isEmpty()) {
                FetchProfile fp = new FetchProfile();
                if (remoteFolder.supportsFetchingFlags()) {
                    fp.add(FetchProfile.Item.FLAGS);
                }
                fp.add(FetchProfile.Item.ENVELOPE);

                Timber.d("SYNC: About to fetch %d unsynced messages for folder %s", unsyncedMessages.size(), folder);
                fetchUnsyncedMessages(listeners, account, remoteFolder, unsyncedMessages, smallMessages, largeMessages,
                        progress, todo,
                        fp);
            }

            Timber.d("SYNC: Synced unsynced messages for folder %s", folder);
        }

        Timber.d("SYNC: Have %d large messages and %d small messages out of %d unsynced messages",
                largeMessages.size(), smallMessages.size(), unsyncedMessages.size());

        unsyncedMessages.clear();
        /*
         * Grab the content of the small messages first. This is going to
         * be very fast and at very worst will be a single up of a few bytes and a single
         * download of 625k.
         */
        if (!smallMessages.isEmpty()) {
            FetchProfile fp = new FetchProfile();
            //TODO: Only fetch small and large messages if we have some
            fp.add(FetchProfile.Item.BODY);
            //        fp.add(FetchProfile.Item.FLAGS);
            //        fp.add(FetchProfile.Item.ENVELOPE);
            downloadSmallMessages(notificationController, listeners, account,
                    remoteFolder, localFolder, smallMessages, progress, unreadBeforeStart,
                    newMessages, todo, fp, controller);
            smallMessages.clear();
        }
        /*
         * Now do the large messages that require more round trips.
         */
        if (!largeMessages.isEmpty()) {
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.STRUCTURE);
            downloadLargeMessages(notificationController, listeners, account,
                    remoteFolder, localFolder, largeMessages, progress, unreadBeforeStart,
                    newMessages, todo, fp, controller);
            largeMessages.clear();
        }

        /*
         * Refresh the flags for any messages in the local store that we didn't just
         * download.
         */

        if (!syncFlagMessages.isEmpty()) {
            refreshLocalMessageFlags(notificationController, listeners, account,
                    remoteFolder, localFolder, syncFlagMessages, progress, todo, controller);
        }

        Timber.d("SYNC: Synced remote messages for folder %s, %d new messages", folder, newMessages.get());

        if (purgeToVisibleLimit) {
            localFolder.purgeToVisibleLimit(new MessageRemovalListener() {
                @Override
                public void messageRemoved(Message message) {
                    for (MessagingListener l : listeners) {
                        l.synchronizeMailboxRemovedMessage(account, folder, message);
                    }
                }

            });
        }

        // If the oldest message seen on this sync is newer than
        // the oldest message seen on the previous sync, then
        // we want to move our high-water mark forward
        // this is all here just for pop which only syncs inbox
        // this would be a little wrong for IMAP (we'd want a folder-level pref, not an account level pref.)
        // fortunately, we just don't care.
        Long oldestMessageTime = localFolder.getOldestMessageDate();

        if (oldestMessageTime != null) {
            Date oldestExtantMessage = new Date(oldestMessageTime);
            if (oldestExtantMessage.before(downloadStarted) &&
                    oldestExtantMessage.after(new Date(account.getLatestOldMessageSeenTime()))) {
                account.setLatestOldMessageSeenTime(oldestExtantMessage.getTime());
                account.save(Preferences.getPreferences(context));
            }

        }
        return newMessages.get();
    }

    private void refreshLocalMessageFlags(
            final NotificationController notificationController,
            final Set<MessagingListener> listeners,
            final Account account, final Folder remoteFolder,
            final LocalFolder localFolder,
            List<Message> syncFlagMessages,
            final AtomicInteger progress,
            final int todo,
            IMessageController controller) throws MessagingException {

        final String folder = remoteFolder.getName();
        if (remoteFolder.supportsFetchingFlags()) {
            Timber.d("SYNC: About to sync flags for %d remote messages for folder %s", syncFlagMessages.size(), folder);

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.FLAGS);

            List<Message> undeletedMessages = new LinkedList<>();
            for (Message message : syncFlagMessages) {
                if (!message.isSet(Flag.DELETED)) {
                    undeletedMessages.add(message);
                }
            }

            remoteFolder.fetch(undeletedMessages, fp, null);
            for (Message remoteMessage : syncFlagMessages) {
                LocalMessage localMessage = localFolder.getMessage(remoteMessage.getUid());
                boolean messageChanged = syncFlags(localMessage, remoteMessage);
                if (messageChanged) {
                    boolean shouldBeNotifiedOf = false;
                    if (localMessage.isSet(Flag.DELETED) || controller.isMessageSuppressed(localMessage)) {
                        for (MessagingListener l : listeners) {
                            l.synchronizeMailboxRemovedMessage(account, folder, localMessage);
                        }
                    } else {
                        if (controller.shouldNotifyForMessage(account, localFolder, localMessage)) {
                            shouldBeNotifiedOf = true;
                        }
                    }

                    // we're only interested in messages that need removing
                    if (!shouldBeNotifiedOf) {
                        MessageReference messageReference = localMessage.makeMessageReference();
                        notificationController.removeNewMailNotification(account, messageReference);
                    }
                }
                progress.incrementAndGet();
                for (MessagingListener l : listeners) {
                    l.synchronizeMailboxProgress(account, folder, progress.get(), todo);
                }
            }
        }
    }

    private void evaluateMessageForDownload(
            final Set<MessagingListener> listeners,
            final Message message, final String folder,
            final LocalFolder localFolder,
            final Folder remoteFolder,
            final Account account,
            final List<Message> unsyncedMessages,
            final List<Message> syncFlagMessages,
            boolean flagSyncOnly) throws MessagingException {
        if (message.isSet(Flag.DELETED)) {
            Timber.v("Message with uid %s is marked as deleted", message.getUid());

            syncFlagMessages.add(message);
            return;
        }

        Message localMessage = localFolder.getMessage(message.getUid());

        if (localMessage == null) {
            if (!flagSyncOnly) {
                if (!message.isSet(Flag.X_DOWNLOADED_FULL) && !message.isSet(Flag.X_DOWNLOADED_PARTIAL)) {
                    Timber.v("Message with uid %s has not yet been downloaded", message.getUid());

                    unsyncedMessages.add(message);
                } else {
                    Timber.v("Message with uid %s is partially or fully downloaded", message.getUid());

                    // Store the updated message locally
                    localFolder.appendMessages(Collections.singletonList(message));

                    localMessage = localFolder.getMessage(message.getUid());

                    localMessage.setFlag(Flag.X_DOWNLOADED_FULL, message.isSet(Flag.X_DOWNLOADED_FULL));
                    localMessage.setFlag(Flag.X_DOWNLOADED_PARTIAL, message.isSet(Flag.X_DOWNLOADED_PARTIAL));

                    for (MessagingListener l : listeners) {
                        if (!localMessage.isSet(Flag.SEEN)) {
                            l.synchronizeMailboxNewMessage(account, folder, localMessage);
                        }
                    }
                }
            }
        } else if (!localMessage.isSet(Flag.DELETED)) {
            Timber.v("Message with uid %s is present in the local store", message.getUid());

            if (!localMessage.isSet(Flag.X_DOWNLOADED_FULL) && !localMessage.isSet(Flag.X_DOWNLOADED_PARTIAL)) {
                Timber.v("Message with uid %s is not downloaded, even partially; trying again", message.getUid());

                unsyncedMessages.add(message);
            } else {
                String newPushState = remoteFolder.getNewPushState(localFolder.getPushState(), message);
                if (newPushState != null) {
                    localFolder.setPushState(newPushState);
                }
                syncFlagMessages.add(message);
            }
        } else {
            Timber.v("Local copy of message with uid %s is marked as deleted", message.getUid());
        }
    }


    private <T extends Message> void fetchUnsyncedMessages(
            final Set<MessagingListener> listeners,
            final Account account, final Folder<T> remoteFolder,
            List<T> unsyncedMessages,
            final List<Message> smallRemoteMessages,
            final List<Message> largeRemoteMessages,
            final AtomicInteger progress,
            final int todo,
            FetchProfile fp) throws MessagingException {
        final String folder = remoteFolder.getName();

        final Date earliestDate = account.getEarliestPollDate();
        remoteFolder.fetch(unsyncedMessages, fp,
            new MessageRetrievalListener<T>() {
                @Override
                public void messageFinished(T remoteMessage, int number, int ofTotal) {
                    try {
                        if (remoteMessage.isSet(Flag.DELETED) || remoteMessage.olderThan(earliestDate)) {
                            if (K9.isDebug()) {
                                if (remoteMessage.isSet(Flag.DELETED)) {
                                    Timber.v("Newly downloaded message %s:%s:%s was marked deleted on server, " +
                                            "skipping", account, folder, remoteMessage.getUid());
                                } else {
                                    Timber.d("Newly downloaded message %s is older than %s, skipping",
                                            remoteMessage.getUid(), earliestDate);
                                }
                            }
                            progress.incrementAndGet();
                            for (MessagingListener l : listeners) {
                                //TODO: This might be the source of poll count errors in the UI.
                                //Is todo always the same as ofTotal
                                l.synchronizeMailboxProgress(account, folder, progress.get(), todo);
                            }
                            return;
                        }

                        if (account.getMaximumAutoDownloadMessageSize() > 0 &&
                                remoteMessage.getSize() > account.getMaximumAutoDownloadMessageSize()) {
                            largeRemoteMessages.add(remoteMessage);
                        } else {
                            smallRemoteMessages.add(remoteMessage);
                        }
                    } catch (Exception e) {
                        Timber.e(e, "Error while storing downloaded message.");
                    }
                }

                @Override
                public void messageStarted(String uid, int number, int ofTotal) {
                }

                @Override
                public void messagesFinished(int total) {
                    // FIXME this method is almost never invoked by various Stores! Don't rely on it unless fixed!!
                }

            });
    }

    private <T extends Message> void downloadSmallMessages(
            final NotificationController notificationController,
            final Set<MessagingListener> listeners,
            final Account account, final Folder<T> remoteFolder,
            final LocalFolder localFolder,
            List<T> smallRemoteMessages,
            final AtomicInteger progress,
            final int unreadBeforeStart,
            final AtomicInteger newMessages,
            final int todo,
            FetchProfile fp,
            final IMessageController controller) throws MessagingException {
        final String folder = remoteFolder.getName();

        Timber.d("SYNC: Fetching %d small messages for folder %s", smallRemoteMessages.size(), folder);

        remoteFolder.fetch(smallRemoteMessages,
                fp, new MessageRetrievalListener<T>() {
                    @Override
                    public void messageFinished(final T message, int number, int ofTotal) {
                        try {

                            // Store the updated message locally
                            final LocalMessage localMessage = localFolder.storeSmallMessage(message, new Runnable() {
                                @Override
                                public void run() {
                                    progress.incrementAndGet();
                                }
                            });

                            // Increment the number of "new messages" if the newly downloaded message is
                            // not marked as read.
                            if (!localMessage.isSet(Flag.SEEN)) {
                                newMessages.incrementAndGet();
                            }

                            Timber.v("About to notify listeners that we got a new small message %s:%s:%s",
                                    account, folder, message.getUid());

                            // Update the listener with what we've found
                            for (MessagingListener l : listeners) {
                                l.synchronizeMailboxProgress(account, folder, progress.get(), todo);
                                if (!localMessage.isSet(Flag.SEEN)) {
                                    l.synchronizeMailboxNewMessage(account, folder, localMessage);
                                }
                            }
                            // Send a notification of this message

                            if (controller.shouldNotifyForMessage(account, localFolder, message)) {
                                // Notify with the localMessage so that we don't have to recalculate the content preview.
                                notificationController.addNewMailNotification(account, localMessage, unreadBeforeStart);
                            }

                        } catch (MessagingException me) {
                            Timber.e(me, "SYNC: fetch small messages");
                        }
                    }

                    @Override
                    public void messageStarted(String uid, int number, int ofTotal) {
                    }

                    @Override
                    public void messagesFinished(int total) {
                    }
                });

        Timber.d("SYNC: Done fetching small messages for folder %s", folder);
    }

    private <T extends Message> void downloadLargeMessages(
            final NotificationController notificationController,
            final Set<MessagingListener> listeners,
            final Account account, final Folder<T> remoteFolder,
            final LocalFolder localFolder,
            List<T> largeRemoteMessages,
            final AtomicInteger progress,
            final int unreadBeforeStart,
            final AtomicInteger newMessages,
            final int todo,
            FetchProfile fp, IMessageController controller) throws MessagingException {
        final String folder = remoteFolder.getName();

        Timber.d("SYNC: Fetching large messages for folder %s", folder);

        remoteFolder.fetch(largeRemoteMessages, fp, null);
        for (T message : largeRemoteMessages) {

            if (message.getBody() == null) {
                downloadSaneBody(account, remoteFolder, localFolder, message);
            } else {
                downloadPartial(remoteFolder, localFolder, message);
            }

            Timber.v("About to notify listeners that we got a new large message %s:%s:%s",
                    account, folder, message.getUid());

            // Update the listener with what we've found
            progress.incrementAndGet();
            // TODO do we need to re-fetch this here?
            LocalMessage localMessage = localFolder.getMessage(message.getUid());
            // Increment the number of "new messages" if the newly downloaded message is
            // not marked as read.
            if (!localMessage.isSet(Flag.SEEN)) {
                newMessages.incrementAndGet();
            }
            for (MessagingListener l : listeners) {
                l.synchronizeMailboxProgress(account, folder, progress.get(), todo);
                if (!localMessage.isSet(Flag.SEEN)) {
                    l.synchronizeMailboxNewMessage(account, folder, localMessage);
                }
            }
            // Send a notification of this message
            if (controller.shouldNotifyForMessage(account, localFolder, message)) {
                // Notify with the localMessage so that we don't have to recalculate the content preview.
                notificationController.addNewMailNotification(account, localMessage, unreadBeforeStart);
            }
        }

        Timber.d("SYNC: Done fetching large messages for folder %s", folder);
    }

    private void downloadPartial(Folder remoteFolder, LocalFolder localFolder, Message remoteMessage)
            throws MessagingException {
        /*
         * We have a structure to deal with, from which
         * we can pull down the parts we want to actually store.
         * Build a list of parts we are interested in. Text parts will be downloaded
         * right now, attachments will be left for later.
         */

        Set<Part> viewables = MessageExtractor.collectTextParts(remoteMessage);

        /*
         * Now download the parts we're interested in storing.
         */
        BodyFactory bodyFactory = new DefaultBodyFactory();
        for (Part part : viewables) {
            remoteFolder.fetchPart(remoteMessage, part, null, bodyFactory);
        }
        // Store the updated message locally
        localFolder.appendMessages(Collections.singletonList(remoteMessage));

        Message localMessage = localFolder.getMessage(remoteMessage.getUid());

        // Set a flag indicating this message has been fully downloaded and can be
        // viewed.
        localMessage.setFlag(Flag.X_DOWNLOADED_PARTIAL, true);
    }

    /**
     * @throws MessagingException if unable to fetch sane body or update local folder with message
     */
    private void downloadSaneBody(Account account, Folder remoteFolder, LocalFolder localFolder, Message remoteMessage) throws MessagingException {
        /*
         * The provider was unable to get the structure of the message, so
         * we'll download a reasonable portion of the messge and mark it as
         * incomplete so the entire thing can be downloaded later if the user
         * wishes to download it.
         */
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.BODY_SANE);
                /*
                 *  TODO a good optimization here would be to make sure that all Stores set
                 *  the proper size after this fetch and compare the before and after size. If
                 *  they equal we can mark this SYNCHRONIZED instead of PARTIALLY_SYNCHRONIZED
                 */

        remoteFolder.fetch(Collections.singletonList(remoteMessage), fp, null);

        // Store the updated message locally
        localFolder.appendMessages(Collections.singletonList(remoteMessage));

        Message localMessage = localFolder.getMessage(remoteMessage.getUid());


        // Certain (POP3) servers give you the whole message even when you ask for only the first x Kb
        if (!remoteMessage.isSet(Flag.X_DOWNLOADED_FULL)) {
                    /*
                     * Mark the message as fully downloaded if the message size is smaller than
                     * the account's autodownload size limit, otherwise mark as only a partial
                     * download.  This will prevent the system from downloading the same message
                     * twice.
                     *
                     * If there is no limit on autodownload size, that's the same as the message
                     * being smaller than the max size
                     */
            if (account.getMaximumAutoDownloadMessageSize() == 0
                    || remoteMessage.getSize() < account.getMaximumAutoDownloadMessageSize()) {
                localMessage.setFlag(Flag.X_DOWNLOADED_FULL, true);
            } else {
                // Set a flag indicating that the message has been partially downloaded and
                // is ready for view.
                localMessage.setFlag(Flag.X_DOWNLOADED_PARTIAL, true);
            }
        }

    }

    private boolean syncFlags(LocalMessage localMessage, Message remoteMessage) throws MessagingException {
        boolean messageChanged = false;
        if (localMessage == null || localMessage.isSet(Flag.DELETED)) {
            return false;
        }
        if (remoteMessage.isSet(Flag.DELETED)) {
            if (localMessage.getFolder().syncRemoteDeletions()) {
                localMessage.setFlag(Flag.DELETED, true);
                messageChanged = true;
            }
        } else {
            for (Flag flag : MessagingController.SYNC_FLAGS) {
                if (remoteMessage.isSet(flag) != localMessage.isSet(flag)) {
                    localMessage.setFlag(flag, remoteMessage.isSet(flag));
                    messageChanged = true;
                }
            }
        }
        return messageChanged;
    }

}
