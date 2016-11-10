
package com.fsck.k9.mail;

public class MessagingException extends Exception {
    public static final long serialVersionUID = -1;

    private boolean permanentFailure = false;
    private boolean activeNetworkConnection = true;

    public MessagingException(String message) {
        super(message);
    }

    public MessagingException(String message, boolean perm) {
        super(message);
        permanentFailure = perm;
    }

    public MessagingException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public MessagingException(String message, boolean perm, Throwable throwable) {
        super(message, throwable);
        permanentFailure = perm;
    }

    public MessagingException(String message, Throwable throwable, boolean activeNetworkConnection) {
        super(message, throwable);
        this.activeNetworkConnection = activeNetworkConnection;
    }

    public boolean isPermanentFailure() {
        return permanentFailure;
    }

    public boolean hadActiveNetworkConnection() {
        return activeNetworkConnection;
    }

    //TODO setters in Exception are bad style, remove (it's nearly unused anyway)
    public void setPermanentFailure(boolean permanentFailure) {
        this.permanentFailure = permanentFailure;
    }

}
