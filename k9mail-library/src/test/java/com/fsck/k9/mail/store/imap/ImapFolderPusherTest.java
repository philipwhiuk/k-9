package com.fsck.k9.mail.store.imap;

import android.content.Context;
import android.os.PowerManager;

import com.fsck.k9.mail.MessagingException;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.fsck.k9.mail.Folder.OPEN_MODE_RO;
import static com.fsck.k9.mail.Folder.OPEN_MODE_RW;
import static com.fsck.k9.mail.store.imap.ImapResponseHelper.createImapResponse;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public class ImapFolderPusherTest {

    private ImapStore store = mock(ImapStore.class);
    private ImapConnection imapConnection = mock(ImapConnection.class);
    private FolderNameCodec folderNameCodec = mock(FolderNameCodec.class);
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
    private CountDownLatch executeExamineWakelock;

    private StoreConfig createStoreConfig() throws MessagingException {
        StoreConfig storeConfig = mock(StoreConfig.class);
        when(storeConfig.getInboxFolderName()).thenReturn(folderName);
        when(folderNameCodec.encode(folderName)).thenReturn(folderName);
        when(storeConfig.getStoreUri()).thenReturn("imap://user:password@imap.example.org");
        when(storeConfig.toString()).thenReturn("Store");
        when(store.getFolderNameCodec()).thenReturn(folderNameCodec);

        return storeConfig;
    }

    private void prepareImapFolderForOpen(int openMode, boolean isIdleCapable) throws MessagingException, IOException {
        when(store.getConnection()).thenReturn(imapConnection);
        when(imapConnection.isIdleCapable()).thenReturn(isIdleCapable);
        final List<ImapResponse> imapResponses = asList(
                createImapResponse("* FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft NonJunk $MDNSent)"),
                createImapResponse("* OK [PERMANENTFLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft NonJunk " +
                        "$MDNSent \\*)] Flags permitted."),
                createImapResponse("* 23 EXISTS"),
                createImapResponse("* 0 RECENT"),
                createImapResponse("* OK [UIDVALIDITY 1125022061] UIDs valid"),
                createImapResponse("* OK [UIDNEXT 57576] Predicted next UID"),
                (openMode == OPEN_MODE_RW) ?
                        createImapResponse("2 OK [READ-WRITE] Select completed.") :
                        createImapResponse("2 OK [READ-ONLY] Examine completed.")
        );

        if (openMode == OPEN_MODE_RW) {
            when(imapConnection.executeSimpleCommand("SELECT \"INBOX\"")).thenReturn(imapResponses);
        } else {
            when(imapConnection.executeSimpleCommand("EXAMINE \"INBOX\"")).thenAnswer(new Answer<List<ImapResponse>>() {
                @Override
                public List<ImapResponse> answer(InvocationOnMock invocation) throws Throwable {
                    executeExamineWakelock.countDown();
                    return imapResponses;
                }
            });
        }
    }

    @Before
    public void before() throws MessagingException, IOException {
        executeExamineWakelock = new CountDownLatch(1);
        when(pushReceiver.getContext()).thenReturn(RuntimeEnvironment.application);
        when(tracingPowerManagerFactory.getPowerManager(any(Context.class))).thenReturn(tracingPowerManager);
        when(tracingPowerManager.newWakeLock(anyInt(), anyString())).thenReturn(tracingWakelock);
        when(executorServiceFactory.createService()).thenReturn(executorService).thenReturn(executorService2);
        StoreConfig config = createStoreConfig();
        prepareImapFolderForOpen(OPEN_MODE_RO, true);
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
    public void task_opensReadOnlyConnectionToServer() throws Exception {
        imapFolderPusher.start();
        verify(executorService).submit(runnableCaptor.capture());
        Thread t = new Thread(runnableCaptor.getValue());
        t.start();
        executeExamineWakelock.await(100, TimeUnit.MILLISECONDS);
        verify(imapConnection, atLeastOnce()).executeSimpleCommand("EXAMINE \""+folderName+"\"");
        t.stop();
    }
}
