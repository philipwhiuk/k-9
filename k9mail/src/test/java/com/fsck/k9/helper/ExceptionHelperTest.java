package com.fsck.k9.helper;


import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ExceptionHelperTest {

    @Test
    public void getRootCauseMessage_returnsMessageFromNestedException() {
        Throwable t = new Exception(new Exception("reason"));

        String result = ExceptionHelper.getRootCauseMessage(t);

        assertEquals("Exception: reason", result);
    }

    @Test
    public void getRootCauseMessage_stripsPackageName() {
        Throwable t = new Exception(new IOException("reason"));

        String result = ExceptionHelper.getRootCauseMessage(t);

        assertEquals("IOException: reason", result);
    }

    @Test
    public void getRootCauseMessage_usesLocalisedMessage() {
        Throwable t = new Exception(new LocalisedException());

        String result = ExceptionHelper.getRootCauseMessage(t);

        assertEquals("LocalisedException: localised", result);
    }

    public static class LocalisedException extends Throwable {
        public String getLocalizedMessage() {
            return "localised";
        }
    }
}
