package com.fsck.k9.mail.store.imap;

import com.fsck.k9.mail.Flag;

import java.util.Set;

public class ImapQueryBuilder {


    public static String buildQuery(Set<Flag> requiredFlags, Set<Flag> forbiddenFlags,
                                    String queryString, boolean isRemoteSearchFullText) {
        String imapQuery = "UID SEARCH ";

        if (requiredFlags != null) {
            for (Flag flag : requiredFlags) {
                switch (flag) {
                    case DELETED: imapQuery += "DELETED "; break;
                    case SEEN: imapQuery += "SEEN "; break;
                    case ANSWERED: imapQuery += "ANSWERED "; break;
                    case FLAGGED: imapQuery += "FLAGGED "; break;
                    case DRAFT: imapQuery += "DRAFT "; break;
                    case RECENT: imapQuery += "RECENT "; break;
                    default: break;
                }
            }
        }

        if (forbiddenFlags != null) {
            for (Flag flag : forbiddenFlags) {
                switch (flag) {
                    case DELETED: imapQuery += "UNDELETED "; break;
                    case SEEN: imapQuery += "UNSEEN "; break;
                    case ANSWERED: imapQuery += "UNANSWERED "; break;
                    case FLAGGED: imapQuery += "UNFLAGGED "; break;
                    case DRAFT: imapQuery += "UNDRAFT "; break;
                    case RECENT: imapQuery += "UNRECENT "; break;
                    default: break;
                }
            }
        }

        String encodedQuery = ImapUtility.encodeString(queryString);
        if (isRemoteSearchFullText) {
            imapQuery += "TEXT " + encodedQuery;
        } else {
            imapQuery += "OR SUBJECT " + encodedQuery + " FROM " + encodedQuery;
        }

        return imapQuery;
    }
}
