package com.fsck.k9.mail.store.webdav;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import static com.fsck.k9.mail.K9MailLib.LOG_TAG;

/**
 * Utility methods for WebDAV code.
 */
class WebDavUtils {

    /**
     * Returns a string of the stacktrace for a Throwable to allow for easy inline printing of errors.
     */
    static String processException(Throwable t) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, false, "US-ASCII");

            t.printStackTrace(ps);
            ps.flush();
            ps.close();

            return baos.toString("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            Log.e(LOG_TAG, "Unable to convert exception to string due to encoding error", e);
            return "Unable to convert exception to string due to encoding error: " + e.getMessage();
        }
    }
}
