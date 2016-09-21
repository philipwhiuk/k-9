package com.fsck.k9.mail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SingleThreadedExecutorServiceFactory {
    public ExecutorService createService() {
        return Executors.newSingleThreadExecutor();
    }
}
