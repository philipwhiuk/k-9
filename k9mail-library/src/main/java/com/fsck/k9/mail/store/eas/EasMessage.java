package com.fsck.k9.mail.store.eas;

import android.util.Log;

import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.InputStream;

/**
 * A EAS Message
 */
public class EasMessage extends MimeMessage {
    public EasMessage(String uid, Folder folder) {
        this.mUid = uid;
        this.mFolder = folder;
    }

    public void setSize(int size) {
        this.mSize = size;
    }

    @Override
    public void parse(InputStream in) throws IOException, MessagingException {
        super.parse(in);
    }

    public void setFlagInternal(Flag flag, boolean set) throws MessagingException {
        super.setFlag(flag, set);
    }

    @Override
    public void delete(String trashFolderName) throws MessagingException {
//            Log.i(K9MailLib.LOG_TAG, "Deleting message by moving to " + trashFolderName);
//            mFolder.moveMessages(new Message[] { this }, mFolder.getStore().getFolder(trashFolderName));
        Log.e(K9MailLib.LOG_TAG,
                "Unimplemented method delete(String trashFolderName) legacy api, should not be in use");
    }

    @Override
    public void setFlag(Flag flag, boolean set) throws MessagingException {
        setFlag(flag, set, true);
    }

    public void setFlag(Flag flag, boolean set, boolean updateFolder) throws MessagingException {
        super.setFlag(flag, set);

        if (updateFolder) {
            mFolder.setFlags(new Message[] { this }, new Flag[] { flag }, set);
        }
    }
}