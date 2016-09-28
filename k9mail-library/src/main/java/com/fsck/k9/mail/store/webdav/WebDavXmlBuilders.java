package com.fsck.k9.mail.store.webdav;

import com.fsck.k9.mail.MessagingException;

/**
 * Created by philip on 28/09/2016.
 */

public class WebDavXmlBuilders {

    static String getSpecialFoldersList() {
        StringBuilder builder = new StringBuilder(200);
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>");
        builder.append("<propfind xmlns=\"DAV:\">");
        builder.append("<prop>");
        builder.append("<").append(WebDavConstants.DAV_MAIL_INBOX_FOLDER).append(" xmlns=\"urn:schemas:httpmail:\"/>");
        builder.append("<").append(WebDavConstants.DAV_MAIL_DRAFTS_FOLDER).append(" xmlns=\"urn:schemas:httpmail:\"/>");
        builder.append("<").append(WebDavConstants.DAV_MAIL_OUTBOX_FOLDER).append(" xmlns=\"urn:schemas:httpmail:\"/>");
        builder.append("<").append(WebDavConstants.DAV_MAIL_SENT_FOLDER).append(" xmlns=\"urn:schemas:httpmail:\"/>");
        builder.append("<").append(WebDavConstants.DAV_MAIL_TRASH_FOLDER).append(" xmlns=\"urn:schemas:httpmail:\"/>");
        // This should always be ##DavMailSubmissionURI## for which we already have a constant
        // buffer.append("<sendmsg xmlns=\"urn:schemas:httpmail:\"/>");

        builder.append("<").append(WebDavConstants.DAV_MAIL_SPAM_FOLDER).append(" xmlns=\"urn:schemas:httpmail:\"/>");

        builder.append("</prop>");
        builder.append("</propfind>");
        return builder.toString();
    }

    /***************************************************************
     * WebDAV XML Request body retrieval functions
     */
    static String getFolderListXml(String url) {
        StringBuilder builder = new StringBuilder(200);
        builder.append("<?xml version='1.0' ?>");
        builder.append("<a:searchrequest xmlns:a='DAV:'><a:sql>\r\n");
        builder.append("SELECT \"DAV:uid\", \"DAV:ishidden\"\r\n");
        builder.append(" FROM SCOPE('deep traversal of \"").append(url).append("\"')\r\n");
        builder.append(" WHERE \"DAV:ishidden\"=False AND \"DAV:isfolder\"=True\r\n");
        builder.append("</a:sql></a:searchrequest>\r\n");
        return builder.toString();
    }

    static String getMessageCountXml(String messageState) {
        StringBuilder builder = new StringBuilder(200);
        builder.append("<?xml version='1.0' ?>");
        builder.append("<a:searchrequest xmlns:a='DAV:'><a:sql>\r\n");
        builder.append("SELECT \"DAV:visiblecount\"\r\n");
        builder.append(" FROM \"\"\r\n");
        builder.append(" WHERE \"DAV:ishidden\"=False AND \"DAV:isfolder\"=False AND \"urn:schemas:httpmail:read\"=")
                .append(messageState).append("\r\n");
        builder.append(" GROUP BY \"DAV:ishidden\"\r\n");
        builder.append("</a:sql></a:searchrequest>\r\n");
        return builder.toString();
    }

    static String getMessageEnvelopeXml(String[] uids) {
        StringBuilder buffer = new StringBuilder(200);
        buffer.append("<?xml version='1.0' ?>");
        buffer.append("<a:searchrequest xmlns:a='DAV:'><a:sql>\r\n");
        buffer.append("SELECT \"DAV:uid\", \"DAV:getcontentlength\",");
        buffer.append(" \"urn:schemas:mailheader:mime-version\",");
        buffer.append(" \"urn:schemas:mailheader:content-type\",");
        buffer.append(" \"urn:schemas:mailheader:subject\",");
        buffer.append(" \"urn:schemas:mailheader:date\",");
        buffer.append(" \"urn:schemas:mailheader:thread-topic\",");
        buffer.append(" \"urn:schemas:mailheader:thread-index\",");
        buffer.append(" \"urn:schemas:mailheader:from\",");
        buffer.append(" \"urn:schemas:mailheader:to\",");
        buffer.append(" \"urn:schemas:mailheader:in-reply-to\",");
        buffer.append(" \"urn:schemas:mailheader:cc\",");
        buffer.append(" \"urn:schemas:httpmail:read\"");
        buffer.append(" \r\n");
        buffer.append(" FROM \"\"\r\n");
        buffer.append(" WHERE \"DAV:ishidden\"=False AND \"DAV:isfolder\"=False AND ");
        for (int i = 0, count = uids.length; i < count; i++) {
            if (i != 0) {
                buffer.append("  OR ");
            }
            buffer.append(" \"DAV:uid\"='").append(uids[i]).append("' ");
        }
        buffer.append("\r\n");
        buffer.append("</a:sql></a:searchrequest>\r\n");
        return buffer.toString();
    }

    static String getMessagesXml() {
        StringBuilder builder = new StringBuilder(200);
        builder.append("<?xml version='1.0' ?>");
        builder.append("<a:searchrequest xmlns:a='DAV:'><a:sql>\r\n");
        builder.append("SELECT \"DAV:uid\"\r\n");
        builder.append(" FROM \"\"\r\n");
        builder.append(" WHERE \"DAV:ishidden\"=False AND \"DAV:isfolder\"=False\r\n");
        builder.append("</a:sql></a:searchrequest>\r\n");
        return builder.toString();
    }

    static String getMessageUrlsXml(String[] uids) {
        StringBuilder buffer = new StringBuilder(600);
        buffer.append("<?xml version='1.0' ?>");
        buffer.append("<a:searchrequest xmlns:a='DAV:'><a:sql>\r\n");
        buffer.append("SELECT \"urn:schemas:httpmail:read\", \"DAV:uid\"\r\n");
        buffer.append(" FROM \"\"\r\n");
        buffer.append(" WHERE \"DAV:ishidden\"=False AND \"DAV:isfolder\"=False AND ");
        for (int i = 0, count = uids.length; i < count; i++) {
            if (i != 0) {
                buffer.append("  OR ");
            }

            buffer.append(" \"DAV:uid\"='").append(uids[i]).append("' ");

        }
        buffer.append("\r\n");
        buffer.append("</a:sql></a:searchrequest>\r\n");
        return buffer.toString();
    }

    static String getMessageFlagsXml(String[] uids) throws MessagingException {
        if (uids.length == 0) {
            throw new MessagingException("Attempt to get flags on 0 length array for uids");
        }

        StringBuilder buffer = new StringBuilder(200);
        buffer.append("<?xml version='1.0' ?>");
        buffer.append("<a:searchrequest xmlns:a='DAV:'><a:sql>\r\n");
        buffer.append("SELECT \"urn:schemas:httpmail:read\", \"DAV:uid\"\r\n");
        buffer.append(" FROM \"\"\r\n");
        buffer.append(" WHERE \"DAV:ishidden\"=False AND \"DAV:isfolder\"=False AND ");

        for (int i = 0, count = uids.length; i < count; i++) {
            if (i != 0) {
                buffer.append(" OR ");
            }
            buffer.append(" \"DAV:uid\"='").append(uids[i]).append("' ");
        }
        buffer.append("\r\n");
        buffer.append("</a:sql></a:searchrequest>\r\n");
        return buffer.toString();
    }

    static String getMarkMessagesReadXml(String[] urls, boolean read) {
        StringBuilder buffer = new StringBuilder(600);
        buffer.append("<?xml version='1.0' ?>\r\n");
        buffer.append("<a:propertyupdate xmlns:a='DAV:' xmlns:b='urn:schemas:httpmail:'>\r\n");
        buffer.append("<a:target>\r\n");
        for (String url : urls) {
            buffer.append(" <a:href>").append(url).append("</a:href>\r\n");
        }
        buffer.append("</a:target>\r\n");
        buffer.append("<a:set>\r\n");
        buffer.append(" <a:prop>\r\n");
        buffer.append("  <b:read>").append(read ? "1" : "0").append("</b:read>\r\n");
        buffer.append(" </a:prop>\r\n");
        buffer.append("</a:set>\r\n");
        buffer.append("</a:propertyupdate>\r\n");
        return buffer.toString();
    }

    // For flag:
    // http://www.devnewsgroups.net/group/microsoft.public.exchange.development/topic27175.aspx
    // "<m:0x10900003>1</m:0x10900003>" & _

    static String getMoveOrCopyMessagesReadXml(String[] urls, boolean isMove) {

        String action = (isMove ? "move" : "copy");
        StringBuilder buffer = new StringBuilder(600);
        buffer.append("<?xml version='1.0' ?>\r\n");
        buffer.append("<a:").append(action).append(" xmlns:a='DAV:' xmlns:b='urn:schemas:httpmail:'>\r\n");
        buffer.append("<a:target>\r\n");
        for (String url : urls) {
            buffer.append(" <a:href>").append(url).append("</a:href>\r\n");
        }
        buffer.append("</a:target>\r\n");

        buffer.append("</a:").append(action).append(">\r\n");
        return buffer.toString();
    }
}
