package com.fsck.k9.mail.store.imap;

import android.support.annotation.Nullable;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessageHelper;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.MimeUtility;

import java.util.Locale;

class ImapBodyStructureParser {
    static void parseBodyStructure(ImapList bs, Part part, String id) throws MessagingException {
        if (bs.get(0) instanceof ImapList) {
            /*
             * This is a multipart
             */
            parseMultipartBodyStructure(bs, part, id);
        } else {
            /*
             * This is a body. We need to add as much information as we can find out about
             * it to the Part.
             */

            /*
             *  0| 0  body type
             *  1| 1  body subtype
             *  2| 2  body parameter parenthesized list
             *  3| 3  body id (unused)
             *  4| 4  body description (unused)
             *  5| 5  body encoding
             *  6| 6  body size
             *  -| 7  text lines (only for type TEXT, unused)
             * Extensions (optional):
             *  7| 8  body MD5 (unused)
             *  8| 9  body disposition
             *  9|10  body language (unused)
             * 10|11  body location (unused)
             */

            String type = bs.getString(0);
            String subType = bs.getString(1);
            String mimeType = (type + "/" + subType).toLowerCase(Locale.US);

            ImapList bodyParams = null;
            if (bs.get(2) instanceof ImapList) {
                bodyParams = bs.getList(2);
            }
            String encoding = bs.getString(5);
            int size = bs.getNumber(6);

            if (MimeUtility.isMessage(mimeType)) {
//                  A body type of type MESSAGE and subtype RFC822
//                  contains, immediately after the basic fields, the
//                  envelope structure, body structure, and size in
//                  text lines of the encapsulated message.
//                    [MESSAGE, RFC822, [NAME, Fwd: [#HTR-517941]:  update plans at 1am Friday - Memory allocation - displayware.eml], NIL, NIL, 7BIT, 5974, NIL, [INLINE, [FILENAME*0, Fwd: [#HTR-517941]:  update plans at 1am Friday - Memory all, FILENAME*1, ocation - displayware.eml]], NIL]
                /*
                 * This will be caught by fetch and handled appropriately.
                 */
                throw new MessagingException("BODYSTRUCTURE message/rfc822 not yet supported.");
            }

            /*
             * Set the content type with as much information as we know right now.
             */
            StringBuilder contentType = new StringBuilder();
            contentType.append(mimeType);

            if (bodyParams != null) {
                /*
                 * If there are body params we might be able to get some more information out
                 * of them.
                 */
                for (int i = 0, count = bodyParams.size(); i < count; i += 2) {
                    String paramName = bodyParams.getString(i);
                    String paramValue = bodyParams.getString(i + 1);
                    contentType.append(String.format(";\r\n %s=\"%s\"", paramName, paramValue));
                }
            }

            part.setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType.toString());

            // Extension items
            ImapList bodyDisposition = fetchBodyDisposition(bs, type);

            StringBuilder contentDisposition = new StringBuilder();

            if (bodyDisposition != null && !bodyDisposition.isEmpty()) {
                parseBodyDisposition(bodyDisposition, contentDisposition);
            }

            if (MimeUtility.getHeaderParameter(contentDisposition.toString(), "size") == null) {
                contentDisposition.append(String.format(Locale.US, ";\r\n size=%d", size));
            }

            /*
             * Set the content disposition containing at least the size. Attachment
             * handling code will use this down the road.
             */
            part.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, contentDisposition.toString());

            /*
             * Set the Content-Transfer-Encoding header. Attachment code will use this
             * to parse the body.
             */
            part.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, encoding);

            if (part instanceof ImapMessage) {
                ((ImapMessage) part).setSize(size);
            }

            part.setServerExtra(id);
        }
    }

    @Nullable
    private static ImapList fetchBodyDisposition(ImapList bs, String type) {
        if ("text".equalsIgnoreCase(type) && bs.size() > 9 && bs.get(9) instanceof ImapList) {
            return bs.getList(9);
        } else if (!("text".equalsIgnoreCase(type)) && bs.size() > 8 && bs.get(8) instanceof ImapList) {
            return bs.getList(8);
        }
        return null;
    }

    private static void parseBodyDisposition(ImapList bodyDisposition, StringBuilder contentDisposition) {
        if (!"NIL".equalsIgnoreCase(bodyDisposition.getString(0))) {
            contentDisposition.append(bodyDisposition.getString(0).toLowerCase(Locale.US));
        }

        if (bodyDisposition.size() > 1 && bodyDisposition.get(1) instanceof ImapList) {
            ImapList bodyDispositionParams = bodyDisposition.getList(1);
                    /*
                     * If there is body disposition information we can pull some more information
                     * about the attachment out.
                     */
            for (int i = 0, count = bodyDispositionParams.size(); i < count; i += 2) {
                String paramName = bodyDispositionParams.getString(i).toLowerCase(Locale.US);
                String paramValue = bodyDispositionParams.getString(i + 1);
                contentDisposition.append(String.format(";\r\n %s=\"%s\"", paramName, paramValue));
            }
        }
    }

    private static void parseMultipartBodyStructure(ImapList bs, Part part, String id) throws MessagingException {
        MimeMultipart mp = MimeMultipart.newInstance();
        for (int i = 0, count = bs.size(); i < count; i++) {
            if (bs.get(i) instanceof ImapList) {
                /*
                 * For each part in the message we're going to add a new BodyPart and parse
                 * into it.
                 */
                MimeBodyPart bp = new MimeBodyPart();
                if (id.equalsIgnoreCase("TEXT")) {
                    parseBodyStructure(bs.getList(i), bp, Integer.toString(i + 1));
                } else {
                    parseBodyStructure(bs.getList(i), bp, id + "." + (i + 1));
                }
                mp.addBodyPart(bp);
            } else {
                    /*
                     * We've got to the end of the children of the part, so now we can find out
                     * what type it is and bail out.
                     */
                String subType = bs.getString(i);
                mp.setSubType(subType.toLowerCase(Locale.US));
                break;
            }
        }
        MimeMessageHelper.setBody(part, mp);
    }
}
