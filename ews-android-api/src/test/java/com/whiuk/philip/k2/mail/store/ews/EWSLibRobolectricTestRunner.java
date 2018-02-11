package com.whiuk.philip.k2.mail.store.ews;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


public class EWSLibRobolectricTestRunner extends RobolectricTestRunner {

    public EWSLibRobolectricTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected Config buildGlobalConfig() {
        return new Config.Builder()
                .setSdk(22)
                .setManifest(Config.NONE)
                .build();
    }
}