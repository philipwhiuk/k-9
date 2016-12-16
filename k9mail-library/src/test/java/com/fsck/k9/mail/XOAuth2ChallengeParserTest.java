package com.fsck.k9.mail;

import com.fsck.k9.mail.filter.Base64;

<<<<<<< HEAD
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.fail;


public class XOAuth2ChallengeParserTest {
    public static String STATUS_400_RESPONSE, STATUS_401_RESPONSE, MISSING_STATUS_RESPONSE, INVALID_RESPONSE;

    static {
        try {
            STATUS_400_RESPONSE = Base64.encode(
                    "{\"status\":\"400\",\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"}");
            STATUS_401_RESPONSE = Base64.encode(
                    "{\"status\":\"401\",\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"}");
            MISSING_STATUS_RESPONSE = Base64.encode(
                    "{\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"}");
            INVALID_RESPONSE = Base64.encode(
                    "{\"status\":\"401\",\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"");
        } catch (UnsupportedEncodingException e) {
            fail();
        }
    }
=======

public class XOAuth2ChallengeParserTest {
    public static final String STATUS_400_RESPONSE = Base64.encode(
            "{\"status\":\"400\",\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"}");
    public static final String STATUS_401_RESPONSE = Base64.encode(
            "{\"status\":\"401\",\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"}");
    public static final String MISSING_STATUS_RESPONSE = Base64.encode(
            "{\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"}");
    public static final String INVALID_RESPONSE = Base64.encode(
            "{\"status\":\"401\",\"schemes\":\"bearer mac\",\"scope\":\"https://mail.google.com/\"");
>>>>>>> upstream-master
}
