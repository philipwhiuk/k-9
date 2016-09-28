package com.fsck.k9.mail.store.imap;

import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.K9MailLib;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class ImapFetchFieldsBuilder {
    public static Set<String> fromFetchProfile(FetchProfile fetchProfile, int maximumAutoDownloadMessageSize) {
        Set<String> fetchFields = new LinkedHashSet<>();
        fetchFields.add("UID");

        if (fetchProfile.contains(FetchProfile.Item.FLAGS)) {
            fetchFields.add("FLAGS");
        }

        if (fetchProfile.contains(FetchProfile.Item.ENVELOPE)) {
            fetchFields.add("INTERNALDATE");
            fetchFields.add("RFC822.SIZE");
            fetchFields.add("BODY.PEEK[HEADER.FIELDS (date subject from content-type to cc " +
                    "reply-to message-id references in-reply-to " + K9MailLib.IDENTITY_HEADER + ")]");
        }

        if (fetchProfile.contains(FetchProfile.Item.STRUCTURE)) {
            fetchFields.add("BODYSTRUCTURE");
        }

        if (fetchProfile.contains(FetchProfile.Item.BODY_SANE)) {
            if (maximumAutoDownloadMessageSize > 0) {
                fetchFields.add(String.format(Locale.US, "BODY.PEEK[]<0.%d>", maximumAutoDownloadMessageSize));
            } else {
                fetchFields.add("BODY.PEEK[]");
            }
        }

        if (fetchProfile.contains(FetchProfile.Item.BODY)) {
            fetchFields.add("BODY.PEEK[]");
        }

        return fetchFields;
    }
}
