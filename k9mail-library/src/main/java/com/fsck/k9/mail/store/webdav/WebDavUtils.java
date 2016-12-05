package com.fsck.k9.mail.store.webdav;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * Utility methods for WebDAV code.
 */
class WebDavUtils {

    /**
     * Returns a string of the stacktrace for a Throwable to allow for easy inline printing of errors.
     */
    static String processException(Throwable t) throws UnsupportedEncodingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, false, "US-ASCII");

        t.printStackTrace(ps);
        ps.flush();
        ps.close();

        return baos.toString("US-ASCII");
    }
}
