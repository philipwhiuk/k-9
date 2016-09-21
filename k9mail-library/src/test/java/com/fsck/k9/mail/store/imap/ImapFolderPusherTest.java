package com.fsck.k9.mail.store.imap;

import android.content.Context;
import android.os.PowerManager;

import com.fsck.k9.mail.PushReceiver;
import com.fsck.k9.mail.SingleThreadedExecutorServiceFactory;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mail.power.TracingPowerManager;
import com.fsck.k9.mail.power.TracingPowerManagerFactory;
import com.fsck.k9.mail.store.StoreConfig;

import org.apache.tools.ant.taskdefs.Exec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.ExecutorService;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public class ImapFolderPusherTest {

    private ImapStore store = mock(ImapStore.class);
    private String folderName = "INBOX";
    private PushReceiver pushReceiver = mock(PushReceiver.class);
    private TracingPowerManagerFactory tracingPowerManagerFactory = mock(TracingPowerManagerFactory.class);
    private TracingPowerManager tracingPowerManager = mock(TracingPowerManager.class);
    private TracingPowerManager.TracingWakeLock tracingWakelock = mock(TracingPowerManager.TracingWakeLock.class);
    private SingleThreadedExecutorServiceFactory executorServiceFactory = mock(SingleThreadedExecutorServiceFactory.class);
    private ExecutorService executorService = mock(ExecutorService.class);
    private ExecutorService executorService2 = mock(ExecutorService.class);
    private ImapFolderPusher imapFolderPusher;
    private ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

    private StoreConfig createStoreConfig() {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getInboxFolderName()).thenReturn("INBOX");
        when(storeConfig.getStoreUri()).thenReturn("imap://user:password@imap.example.org");
        when(storeConfig.toString()).thenReturn("Store");

        return storeConfig;
    }

    @Before
    public void before() {
        when(pushReceiver.getContext()).thenReturn(RuntimeEnvironment.application);
        when(tracingPowerManagerFactory.getPowerManager(any(Context.class))).thenReturn(tracingPowerManager);
        when(tracingPowerManager.newWakeLock(anyInt(), anyString())).thenReturn(tracingWakelock);
        when(executorServiceFactory.createService()).thenReturn(executorService).thenReturn(executorService2);
        StoreConfig config = createStoreConfig();
        when(store.getStoreConfig()).thenReturn(config);
        imapFolderPusher = new ImapFolderPusher(store, folderName, pushReceiver, tracingPowerManagerFactory, executorServiceFactory);
    }

    @Test
    public void canCreateFolderPusher() {
        //Tested in setup
    }

    @Test
    public void constructor_obtainsNonReferenceCountedWakelockFromPowerManager() {
        verify(tracingPowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImapFolderPusher Store:INBOX");
        verify(tracingWakelock).setReferenceCounted(false);
    }

    @Test
    public void start_canBeCalled() {
        imapFolderPusher.start();
    }

    @Test
    public void start_submitsTaskToExecutor() {
        imapFolderPusher.start();
        verify(executorService).submit(any(Runnable.class));
    }

    @Test(expected = IllegalStateException.class)
    public void start_throwsExceptionIfCalledWhenAlreadyStarted() {
        imapFolderPusher.start();
        imapFolderPusher.start();
    }

    @Test(expected = IllegalStateException.class)
    public void start_createsNewExecutorEachTime() {
        imapFolderPusher.start();
        imapFolderPusher.stop();
        imapFolderPusher.start();

        verify(executorServiceFactory, times(2)).createService();
        verify(executorService2).submit(any(Runnable.class));
    }

    @Test(expected = IllegalStateException.class)
    public void stop_throwsExceptionIfNotStarted() {
        imapFolderPusher.stop();
    }

    @Test
    public void stop_canBeCalledAfterStart() {
        imapFolderPusher.start();
        imapFolderPusher.stop();
    }

    @Test
    public void stop_shutsdownNowOldExector() {
        imapFolderPusher.start();
        imapFolderPusher.stop();

        verify(executorService).shutdownNow();
    }

    @Test
    public void task_() {
        imapFolderPusher.start();
        verify(executorService).submit(runnableCaptor.capture());

        runnableCaptor.getValue().run();
    }
}
