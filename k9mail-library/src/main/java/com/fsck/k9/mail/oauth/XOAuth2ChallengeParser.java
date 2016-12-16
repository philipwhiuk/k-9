package com.fsck.k9.mail.oauth;

<<<<<<< HEAD
=======

>>>>>>> upstream-master
import android.util.Log;

import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.filter.Base64;
<<<<<<< HEAD

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

import static com.fsck.k9.mail.K9MailLib.LOG_TAG;

=======
import org.json.JSONException;
import org.json.JSONObject;

import static com.fsck.k9.mail.K9MailLib.LOG_TAG;


>>>>>>> upstream-master
/**
 * Parses Google's Error/Challenge responses
 * See: https://developers.google.com/gmail/xoauth2_protocol#error_response
 */
public class XOAuth2ChallengeParser {
    public static final String BAD_RESPONSE = "400";

<<<<<<< HEAD
    public static boolean shouldRetry(String response, String host) throws UnsupportedEncodingException {
=======

    public static boolean shouldRetry(String response, String host) {
>>>>>>> upstream-master
        String decodedResponse = Base64.decode(response);

        if (K9MailLib.isDebug()) {
            Log.v(LOG_TAG, "Challenge response: " + decodedResponse);
        }

        try {
            JSONObject json = new JSONObject(decodedResponse);
<<<<<<< HEAD
            if (!BAD_RESPONSE.equals(json.getString("status"))) {
                return false;
            }
        } catch (JSONException jsonException) {
            Log.e(LOG_TAG, "Error decoding JSON response from:"
                    + host + ". Response was:" + decodedResponse);
        }
=======
            String status = json.getString("status");
            if (!BAD_RESPONSE.equals(status)) {
                return false;
            }
        } catch (JSONException jsonException) {
            Log.e(LOG_TAG, "Error decoding JSON response from: " + host + ". Response was: " + decodedResponse);
        }

>>>>>>> upstream-master
        return true;
    }
}
