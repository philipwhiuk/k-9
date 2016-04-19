package com.fsck.k9.mail.store.imap;

import com.fsck.k9.mail.MessagingException;

class NegativeImapResponseException extends MessagingException {
    private static final long serialVersionUID = 3725007182205882394L;


    public NegativeImapResponseException(String message) {
        super(message, true);
    }

    public String getAlertText() {
        return alertText;
    }
}
