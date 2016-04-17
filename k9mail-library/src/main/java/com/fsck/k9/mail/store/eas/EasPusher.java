package com.fsck.k9.mail.store.eas;

import android.os.PowerManager;
import android.util.Log;

import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.PushReceiver;
import com.fsck.k9.mail.Pusher;
import com.fsck.k9.mail.power.TracingPowerManager;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ByteArrayEntity;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by philip on 17/04/2016.
 */
public class EasPusher implements Pusher {
    final EasStore mStore;
    final PushReceiver receiver;
    private long lastRefresh = -1;

    Thread listeningThread = null;
    final AtomicBoolean stop = new AtomicBoolean(false);
    final AtomicInteger delayTime = new AtomicInteger(NORMAL_DELAY_TIME);
    final AtomicInteger idleFailureCount = new AtomicInteger(0);
    TracingPowerManager.TracingWakeLock wakeLock = null;

    public EasPusher(EasStore store, PushReceiver receiver) {
        mStore = store;
        this.receiver = receiver;

        TracingPowerManager pm = TracingPowerManager.getPowerManager(receiver.getContext());
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasPusher " + store.getAccount().getDescription());
        wakeLock.setReferenceCounted(false);
    }

    private String getLogId() {
        String id = getAccount().getDescription() + "/" + Thread.currentThread().getName();
        return id;
    }

    public void start(final List<String> folderNames) {
        stop.set(false);

        Runnable runner = new Runnable() {
            public void run() {
                wakeLock.acquire(K9.PUSH_WAKE_LOCK_TIMEOUT);
                if (K9.DEBUG)
                    Log.i(K9MailLib.LOG_TAG, "Pusher starting for " + getLogId());

                while (!stop.get()) {
                    try {
                        Serializer s = new Serializer();

                        int responseTimeout = getAccount().getIdleRefreshMinutes() * 60 + (IDLE_READ_TIMEOUT_INCREMENT / 1000);
                        s.start(Tags.PING_PING)
                                .data(Tags.PING_HEARTBEAT_INTERVAL, String.valueOf(responseTimeout))
                                .start(Tags.PING_FOLDERS);

                        // Using getFolder here will ensure we have retrieved the folder list from the server.
                        for (String folderName : folderNames) {
                            EasFolder folder = (EasFolder)mStore.getFolder(folderName);
                            if (folder != null) {
                                s.start(Tags.PING_FOLDER)
                                        .data(Tags.PING_ID, folder.mServerId)
                                        .data(Tags.PING_CLASS, "Email")
                                        .end();
                            }
                        }

                        s.end().end().done();

                        int timeout = responseTimeout * 1000 + IDLE_READ_TIMEOUT_INCREMENT;
                        HttpResponse resp = sendHttpClientPost(PING_COMMAND, new ByteArrayEntity(s.toByteArray()),
                                timeout);
                        try {
                            int code = resp.getStatusLine().getStatusCode();
                            if (code == HttpStatus.SC_OK) {
                                InputStream is = resp.getEntity().getContent();
                                if (is != null) {
                                    PingParser pingParser = new PingParser(is);
                                    if (!pingParser.parse()) {
                                        // We are finished with the connection. Go ahead an release it before syncing.
                                        reclaimConnection(resp);

                                        for (String folderServerId : pingParser.getFolderList()) {
                                            Folder folder = mStore.getFolder(folderServerId);
                                            if (folder != null) {
                                                receiver.syncFolder(folder);
                                                break;
                                            }
                                        }
                                    } else {
                                        throw new MessagingException("Parsing of Ping response failed");
                                    }
                                } else {
                                    Log.d(K9MailLib.LOG_TAG, "Empty input stream in sync command response");
                                }
                            } else {
                                throw new MessagingException("Received an unsuccessful HTTP status during a ping request: "
                                        + String.valueOf(code));
                            }
                        } finally {
                            reclaimConnection(resp);
                        }
                    } catch (Exception e) {
                        wakeLock.acquire(K9.PUSH_WAKE_LOCK_TIMEOUT);
//                            receiver.setPushActive(getName(), false);

                        if (stop.get()) {
                            Log.i(K9MailLib.LOG_TAG, "Got exception while idling, but stop is set for " + getLogId());
                        } else {
                            receiver.pushError("Push error for " + getLogId(), e);
                            Log.e(K9MailLib.LOG_TAG, "Got exception while idling for " + getLogId(), e);
                            int delayTimeInt = delayTime.get();
                            receiver.sleep(wakeLock, delayTimeInt);
                            delayTimeInt *= 2;
                            if (delayTimeInt > MAX_DELAY_TIME) {
                                delayTimeInt = MAX_DELAY_TIME;
                            }
                            delayTime.set(delayTimeInt);
                            if (idleFailureCount.incrementAndGet() > IDLE_FAILURE_COUNT_LIMIT) {
                                Log.e(K9MailLib.LOG_TAG, "Disabling pusher for " + getLogId() + " after " + idleFailureCount.get() + " consecutive errors");
                                receiver.pushError("Push disabled for " + getLogId() + " after " + idleFailureCount.get() + " consecutive errors", e);
                                stop.set(true);
                            }
                        }
                    }
                }

                for (String folderName : folderNames) {
                    receiver.setPushActive(folderName, false);
                }

                try {
                    if (K9.DEBUG)
                        Log.i(K9MailLib.LOG_TAG, "Pusher for " + getLogId() + " is exiting");
                } catch (Exception me) {
                    Log.e(K9MailLib.LOG_TAG, "Got exception while closing for " + getLogId(), me);
                } finally {
                    wakeLock.release();
                }
            }
        };
        listeningThread = new Thread(runner);
        listeningThread.start();
    }

    public void refresh() {
    }

    public void stop() {
        if (K9.DEBUG)
            Log.i(K9MailLib.LOG_TAG, "Requested stop of EAS pusher");
        stop.set(true);
    }

    public int getRefreshInterval() {
        return (getAccount().getIdleRefreshMinutes() * 60 * 1000);
    }

    public long getLastRefresh() {
        return lastRefresh;
    }

    public void setLastRefresh(long lastRefresh) {
        this.lastRefresh = lastRefresh;
    }
}