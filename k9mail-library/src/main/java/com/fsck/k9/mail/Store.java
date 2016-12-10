
package com.fsck.k9.mail;


import java.util.List;

/**
 * Store is the access point for an email message store. It's location can be
 * local or remote and no specific protocol is defined. Store is intended to
 * loosely model in combination the JavaMail classes javax.mail.Store and
 * javax.mail.Folder along with some additional functionality to improve
 * performance on mobile devices. Implementations of this class should focus on
 * making as few network connections as possible.
 */
public abstract class Store {
    public abstract Folder<? extends Message> getFolder(String name);

    public abstract List<? extends Folder> getPersonalNamespaces(boolean forceListAll) throws MessagingException;

    public abstract void checkSettings() throws MessagingException;

    /**
     * @return whether the store is capable of copying messages
     */
    public boolean isCopyCapable() {
        return false;
    }

    /**
     * @return whether the store is capable of moving messages
     */
    public boolean isMoveCapable() {
        return false;
    }

    /**
     * @return whether the store is capable of push
     */
    public boolean isPushCapable() {
        return false;
    }

    /**
     * @return whether the store is capable of sending messages
     */
    public boolean isSendCapable() {
        return false;
    }

    /**
     * @return whether the store is capable of expunging messages
     */
    public boolean isExpungeCapable() {
        return false;
    }

    /**
     * @return whether the store is capable of marking messages as read
     */
    public boolean isSeenFlagSupported() {
        return true;
    }

    /**
     * Send a series of messages
     */
    public void sendMessages(List<? extends Message> messages) throws MessagingException { }

    /**
     * @return an interface for pushing messages.
     */
    public Pusher getPusher(PushReceiver receiver) {
        return null;
    }

    public abstract boolean syncByDeltas();
}
