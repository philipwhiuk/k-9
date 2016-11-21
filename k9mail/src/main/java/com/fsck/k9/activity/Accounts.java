
package com.fsck.k9.activity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fsck.k9.Account;
import com.fsck.k9.AccountStats;
import com.fsck.k9.BaseAccount;
import com.fsck.k9.FontSizes;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.compose.MessageActions;
import com.fsck.k9.activity.misc.ExtendedAsyncTask;
import com.fsck.k9.activity.misc.NonConfigurationInstance;
import com.fsck.k9.activity.setup.AccountSettings;
import com.fsck.k9.activity.setup.AccountSetupBasics;
import com.fsck.k9.activity.setup.Prefs;
import com.fsck.k9.activity.setup.WelcomeMessage;
import com.fsck.k9.controller.MessagingController;
import com.fsck.k9.helper.SizeFormatter;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.Transport;
import com.fsck.k9.mail.store.RemoteStore;
import com.fsck.k9.mailstore.StorageManager;
import com.fsck.k9.preferences.SettingsExporter;
import com.fsck.k9.preferences.SettingsImportExportException;
import com.fsck.k9.preferences.SettingsImporter;
import com.fsck.k9.preferences.SettingsImporter.AccountDescription;
import com.fsck.k9.preferences.SettingsImporter.AccountDescriptionPair;
import com.fsck.k9.preferences.SettingsImporter.ImportContents;
import com.fsck.k9.preferences.SettingsImporter.ImportResults;
import com.fsck.k9.search.LocalSearch;
import com.fsck.k9.search.SearchAccount;
import com.fsck.k9.search.SearchSpecification.Attribute;
import com.fsck.k9.search.SearchSpecification.SearchField;
import com.fsck.k9.view.ColorChip;

import de.cketti.library.changelog.ChangeLog;


public class Accounts extends K9ListActivity implements OnItemClickListener {

    /**
     * Number of special accounts ('Unified Inbox' and 'All Messages')
     */
    private static final int SPECIAL_ACCOUNTS_COUNT = 2;

    private static final int DIALOG_REMOVE_ACCOUNT = 1;
    private static final int DIALOG_CLEAR_ACCOUNT = 2;
    private static final int DIALOG_RECREATE_ACCOUNT = 3;
    private static final int DIALOG_NO_FILE_MANAGER = 4;

    /*
     * Must be serializable hence implementation class used for declaration.
     */
    private ConcurrentHashMap<String, AccountStats> accountStats = new ConcurrentHashMap<String, AccountStats>();

    private ConcurrentMap<BaseAccount, String> pendingWork = new ConcurrentHashMap<BaseAccount, String>();

    private BaseAccount mSelectedContextAccount;
    private int mUnreadMessageCount = 0;

    private AccountsHandler mHandler = new AccountsHandler();
    private AccountsAdapter mAdapter;
    private SearchAccount mAllMessagesAccount = null;
    private SearchAccount mUnifiedInboxAccount = null;
    private FontSizes mFontSizes = K9.getFontSizes();

    private MenuItem mRefreshMenuItem;
    private ActionBar mActionBar;

    private TextView mActionBarTitle;
    private TextView mActionBarSubTitle;
    private TextView mActionBarUnread;

    /**
     * Contains information about objects that need to be retained on configuration changes.
     *
     * @see #onRetainNonConfigurationInstance()
     */
    private NonConfigurationInstance mNonConfigurationInstance;


    private static final int ACTIVITY_REQUEST_PICK_SETTINGS_FILE = 1;

    class AccountsHandler extends Handler {
        private void setViewTitle() {
            mActionBarTitle.setText(getString(R.string.accounts_title));

            if (mUnreadMessageCount == 0) {
                mActionBarUnread.setVisibility(View.GONE);
            } else {
                mActionBarUnread.setText(String.format("%d", mUnreadMessageCount));
                mActionBarUnread.setVisibility(View.VISIBLE);
            }

            String operation = mListener.getOperation(Accounts.this);
            operation = operation.trim();
            if (operation.length() < 1) {
                mActionBarSubTitle.setVisibility(View.GONE);
            } else {
                mActionBarSubTitle.setVisibility(View.VISIBLE);
                mActionBarSubTitle.setText(operation);
            }
        }
        public void refreshTitle() {
            runOnUiThread(new Runnable() {
                public void run() {
                    setViewTitle();
                }
            });
        }

        public void dataChanged() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }

        public void workingAccount(final Account account, final int res) {
            runOnUiThread(new Runnable() {
                public void run() {
                    String toastText = getString(res, account.getDescription());

                    Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        }


        @SuppressLint("StringFormatInvalid")
        public void accountSizeChanged(final Account account, final long oldSize, final long newSize) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AccountStats stats = accountStats.get(account.getUuid());
                    if (newSize != -1 && stats != null && K9.measureAccounts()) {
                        stats.size = newSize;
                    }
                    String oldSizeFormatted = SizeFormatter.formatSize(getApplication(), oldSize);

                    String toastText = getString(R.string.account_size_changed, account.getDescription(),
                            oldSizeFormatted,
                            SizeFormatter.formatSize(getApplication(), newSize));

                    Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
                    toast.show();
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
        public void progress(final boolean progress) {
            // Make sure we don't try this before the menu is initialized
            // this could happen while the activity is initialized.
            if (mRefreshMenuItem == null) {
                return;
            }

            runOnUiThread(new Runnable() {
                public void run() {
                    if (progress) {
                        mRefreshMenuItem.setActionView(R.layout.actionbar_indeterminate_progress_actionview);
                    } else {
                        mRefreshMenuItem.setActionView(null);
                    }
                }
            });

        }
        public void progress(final int progress) {
            runOnUiThread(new Runnable() {
                public void run() {
                    getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress);
                }
            });
        }
    }

    public void setProgress(boolean progress) {
        mHandler.progress(progress);
    }

    ActivityListener mListener = new ActivityListener() {
        @Override
        public void informUserOfStatus() {
            mHandler.refreshTitle();
        }

        @Override
        public void folderStatusChanged(Account account, String folderName, int unreadMessageCount) {
            try {
                AccountStats stats = account.getStats(Accounts.this);
                if (stats == null) {
                    Log.w(K9.LOG_TAG, "Unable to get account stats");
                } else {
                    accountStatusChanged(account, stats);
                }
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Unable to get account stats", e);
            }
        }
        @Override
        public void accountStatusChanged(BaseAccount account, AccountStats stats) {
            AccountStats oldStats = accountStats.get(account.getUuid());
            int oldUnreadMessageCount = 0;
            if (oldStats != null) {
                oldUnreadMessageCount = oldStats.unreadMessageCount;
            }
            if (stats == null) {
                stats = new AccountStats(); // empty stats for unavailable accounts
                stats.available = false;
            }
            accountStats.put(account.getUuid(), stats);
            if (account instanceof Account) {
                mUnreadMessageCount += stats.unreadMessageCount - oldUnreadMessageCount;
            }
            mHandler.dataChanged();
            pendingWork.remove(account);

            if (pendingWork.isEmpty()) {
                mHandler.progress(Window.PROGRESS_END);
                mHandler.refreshTitle();
            } else {
                int level = (Window.PROGRESS_END / mAdapter.getCount()) * (mAdapter.getCount() - pendingWork.size()) ;
                mHandler.progress(level);
            }
        }

        @Override
        public void accountSizeChanged(Account account, long oldSize, long newSize) {
            mHandler.accountSizeChanged(account, oldSize, newSize);
        }

        @Override
        public void synchronizeMailboxFinished(
            Account account,
            String folder,
            int totalMessagesInMailbox,
        int numNewMessages) {
            MessagingController.getInstance(getApplication()).getAccountStats(Accounts.this, account, mListener);
            super.synchronizeMailboxFinished(account, folder, totalMessagesInMailbox, numNewMessages);

            mHandler.progress(false);

        }

        @Override
        public void synchronizeMailboxStarted(Account account, String folder) {
            super.synchronizeMailboxStarted(account, folder);
            mHandler.progress(true);
        }

        @Override
        public void synchronizeMailboxFailed(Account account, String folder,
        String message) {
            super.synchronizeMailboxFailed(account, folder, message);
            mHandler.progress(false);

        }

    };

    private static String ACCOUNT_STATS = "accountStats";
    private static String STATE_UNREAD_COUNT = "unreadCount";
    private static String SELECTED_CONTEXT_ACCOUNT = "selectedContextAccount";

    public static final String EXTRA_STARTUP = "startup";

    public static final String ACTION_IMPORT_SETTINGS = "importSettings";


    public static void listAccounts(Context context) {
        Intent intent = new Intent(context, Accounts.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(EXTRA_STARTUP, false);
        context.startActivity(intent);
    }

    public static void importSettings(Context context) {
        Intent intent = new Intent(context, Accounts.class);
        intent.setAction(ACTION_IMPORT_SETTINGS);
        context.startActivity(intent);
    }

    @SuppressLint("StringFormatInvalid")
    public static LocalSearch createUnreadSearch(Context context, BaseAccount account) {
        String searchTitle = context.getString(R.string.search_title, account.getDescription(),
                context.getString(R.string.unread_modifier));

        LocalSearch search;
        if (account instanceof SearchAccount) {
            search = ((SearchAccount) account).getRelatedSearch().clone();
            search.setName(searchTitle);
        } else {
            search = new LocalSearch(searchTitle);
            search.addAccountUuid(account.getUuid());

            Account realAccount = (Account) account;
            realAccount.excludeSpecialFolders(search);
            realAccount.limitToDisplayableFolders(search);
        }

        search.and(SearchField.READ, "1", Attribute.NOT_EQUALS);

        return search;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (!K9.isHideSpecialAccounts()) {
            createSpecialAccounts();
        }

        List<Account> accounts = Preferences.getPreferences(this).getAccounts();
        Intent intent = getIntent();
        //onNewIntent(intent);

        // see if we should show the welcome message
        if (ACTION_IMPORT_SETTINGS.equals(intent.getAction())) {
            onImport();
        } else if (accounts.size() < 1) {
            WelcomeMessage.showWelcomeMessage(this);
            finish();
            return;
        }

        if (UpgradeDatabases.actionUpgradeDatabases(this, intent)) {
            finish();
            return;
        }

        boolean startup = intent.getBooleanExtra(EXTRA_STARTUP, true);
        if (startup && K9.startIntegratedInbox() && !K9.isHideSpecialAccounts()) {
            onOpenAccount(mUnifiedInboxAccount);
            finish();
            return;
        } else if (startup && accounts.size() == 1 && onOpenAccount(accounts.get(0))) {
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_PROGRESS);
        mActionBar = getActionBar();
        initializeActionBar();
        setContentView(R.layout.accounts);
        ListView listView = getListView();
        listView.setOnItemClickListener(this);
        listView.setItemsCanFocus(false);
        listView.setScrollingCacheEnabled(false);
        registerForContextMenu(listView);


        if (icicle != null && icicle.containsKey(SELECTED_CONTEXT_ACCOUNT)) {
            String accountUuid = icicle.getString("selectedContextAccount");
            mSelectedContextAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        }

        restoreAccountStats(icicle);
        mHandler.setViewTitle();

        // Handle activity restarts because of a configuration change (e.g. rotating the screen)
        mNonConfigurationInstance = (NonConfigurationInstance) getLastNonConfigurationInstance();
        if (mNonConfigurationInstance != null) {
            mNonConfigurationInstance.restore(this);
        }

        ChangeLog cl = new ChangeLog(this);
        if (cl.isFirstRun()) {
            cl.getLogDialog().show();
        }
    }

    private void initializeActionBar() {
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setCustomView(R.layout.actionbar_custom);

        View customView = mActionBar.getCustomView();
        mActionBarTitle = (TextView) customView.findViewById(R.id.actionbar_title_first);
        mActionBarSubTitle = (TextView) customView.findViewById(R.id.actionbar_title_sub);
        mActionBarUnread = (TextView) customView.findViewById(R.id.actionbar_unread_count);

        mActionBar.setDisplayHomeAsUpEnabled(false);
    }

    /**
     * Creates and initializes the special accounts ('Unified Inbox' and 'All Messages')
     */
    private void createSpecialAccounts() {
        mUnifiedInboxAccount = SearchAccount.createUnifiedInboxAccount(this);
        mAllMessagesAccount = SearchAccount.createAllMessagesAccount(this);
    }

    @SuppressWarnings("unchecked")
    private void restoreAccountStats(Bundle icicle) {
        if (icicle != null) {
            Map<String, AccountStats> oldStats = (Map<String, AccountStats>)icicle.get(ACCOUNT_STATS);
            if (oldStats != null) {
                accountStats.putAll(oldStats);
            }
            mUnreadMessageCount = icicle.getInt(STATE_UNREAD_COUNT);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectedContextAccount != null) {
            outState.putString(SELECTED_CONTEXT_ACCOUNT, mSelectedContextAccount.getUuid());
        }
        outState.putSerializable(STATE_UNREAD_COUNT, mUnreadMessageCount);
        outState.putSerializable(ACCOUNT_STATS, accountStats);
    }

    private StorageManager.StorageListener storageListener = new StorageManager.StorageListener() {

        @Override
        public void onUnmount(String providerId) {
            refresh();
        }

        @Override
        public void onMount(String providerId) {
            refresh();
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        refresh();
        MessagingController.getInstance(getApplication()).addListener(mListener);
        StorageManager.getInstance(getApplication()).addListener(storageListener);
        mListener.onResume(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        MessagingController.getInstance(getApplication()).removeListener(mListener);
        StorageManager.getInstance(getApplication()).removeListener(storageListener);
        mListener.onPause(this);
    }

    /**
     * Save the reference to a currently displayed dialog or a running AsyncTask (if available).
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        Object retain = null;
        if (mNonConfigurationInstance != null && mNonConfigurationInstance.retain()) {
            retain = mNonConfigurationInstance;
        }
        return retain;
    }

    private List<BaseAccount> accounts = new ArrayList<BaseAccount>();
    private enum ACCOUNT_LOCATION {
        TOP, MIDDLE, BOTTOM;
    }
    private EnumSet<ACCOUNT_LOCATION> accountLocation(BaseAccount account) {
        EnumSet<ACCOUNT_LOCATION> accountLocation = EnumSet.of(ACCOUNT_LOCATION.MIDDLE);
        if (accounts.size() > 0) {
            if (accounts.get(0).equals(account)) {
                accountLocation.remove(ACCOUNT_LOCATION.MIDDLE);
                accountLocation.add(ACCOUNT_LOCATION.TOP);
            }
            if (accounts.get(accounts.size() - 1).equals(account)) {
                accountLocation.remove(ACCOUNT_LOCATION.MIDDLE);
                accountLocation.add(ACCOUNT_LOCATION.BOTTOM);
            }
        }
        return accountLocation;
    }


    void refresh() {
        accounts.clear();
        accounts.addAll(Preferences.getPreferences(this).getAccounts());

        List<BaseAccount> newAccounts = createListOfAccounts();

        mAdapter = new AccountsAdapter(newAccounts);
        getListView().setAdapter(mAdapter);
        if (!newAccounts.isEmpty()) {
            mHandler.progress(Window.PROGRESS_START);
        }
        pendingWork.clear();
        mHandler.refreshTitle();

        MessagingController controller = MessagingController.getInstance(getApplication());

        for (BaseAccount account : newAccounts) {
            pendingWork.put(account, "true");

            if (account instanceof Account) {
                Account realAccount = (Account) account;
                controller.getAccountStats(this, realAccount, mListener);
            } else if (K9.countSearchMessages() && account instanceof SearchAccount) {
                final SearchAccount searchAccount = (SearchAccount) account;
                controller.getSearchAccountStats(searchAccount, mListener);
            }
        }
    }

    private List<BaseAccount> createListOfAccounts() {
        List<BaseAccount> newAccounts;
        if (!K9.isHideSpecialAccounts() && accounts.size() > 0) {
            if (mUnifiedInboxAccount == null || mAllMessagesAccount == null) {
                createSpecialAccounts();
            }

            newAccounts = new ArrayList<BaseAccount>(accounts.size() +
                    SPECIAL_ACCOUNTS_COUNT);
            newAccounts.add(mUnifiedInboxAccount);
            newAccounts.add(mAllMessagesAccount);
        } else {
            newAccounts = new ArrayList<BaseAccount>(accounts.size());
        }

        newAccounts.addAll(accounts);

        return newAccounts;
    }

    private void onAddNewAccount() {
        AccountSetupBasics.actionNewAccount(this);
    }

    private void onEditPrefs() {
        Prefs.actionPrefs(this);
    }

    /*
     * This method is called with 'null' for the argument 'account' if
     * all accounts are to be checked. This is handled accordingly in
     * MessagingController.checkMail().
     */
    private void onCheckMail(Account account) {
        MessagingController.getInstance(getApplication()).checkMail(this, account, true, true, null);
        if (account == null) {
            MessagingController.getInstance(getApplication()).sendPendingMessages(null);
        } else {
            MessagingController.getInstance(getApplication()).sendPendingMessages(account, null);
        }

    }

    private void onClearCommands(Account account) {
        MessagingController.getInstance(getApplication()).clearAllPending(account);
    }

    private void onEmptyTrash(Account account) {
        MessagingController.getInstance(getApplication()).emptyTrash(account, null);
    }


    private void onCompose() {
        Account defaultAccount = Preferences.getPreferences(this).getDefaultAccount();
        if (defaultAccount != null) {
            MessageActions.actionCompose(this, defaultAccount);
        } else {
            onAddNewAccount();
        }
    }

    /**
     * Show that account's inbox or folder-list
     * or return false if the account is not available.
     * @param account the account to open ({@link SearchAccount} or {@link Account})
     * @return false if unsuccessfull
     */
    @SuppressLint("StringFormatInvalid")
    private boolean onOpenAccount(BaseAccount account) {
        if (account instanceof SearchAccount) {
            SearchAccount searchAccount = (SearchAccount)account;
            MessageList.actionDisplaySearch(this, searchAccount.getRelatedSearch(), false, false);
        } else {
            Account realAccount = (Account)account;
            if (!realAccount.isEnabled()) {
                onActivateAccount(realAccount);
                return false;
            } else if (!realAccount.isAvailable(this)) {
                String toastText = getString(R.string.account_unavailable, account.getDescription());
                Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_SHORT);
                toast.show();

                Log.i(K9.LOG_TAG, "refusing to open account that is not available");
                return false;
            }
            if (K9.FOLDER_NONE.equals(realAccount.getAutoExpandFolderName())) {
                FolderList.actionHandleAccount(this, realAccount);
            } else {
                LocalSearch search = new LocalSearch(realAccount.getAutoExpandFolderName());
                search.addAllowedFolder(realAccount.getAutoExpandFolderName());
                search.addAccountUuid(realAccount.getUuid());
                MessageList.actionDisplaySearch(this, search, false, true);}
        }
        return true;
    }

    private void onActivateAccount(Account account) {
        List<Account> disabledAccounts = new ArrayList<Account>();
        disabledAccounts.add(account);
        promptForServerPasswords(disabledAccounts);
    }

    /**
     * Ask the user to enter the server passwords for disabled accounts.
     *
     * @param disabledAccounts
     *         A non-empty list of {@link Account}s to ask the user for passwords. Never
     *         {@code null}.
     *         <p><strong>Note:</strong> Calling this method will modify the supplied list.</p>
     */
    void promptForServerPasswords(final List<Account> disabledAccounts) {
        Account account = disabledAccounts.remove(0);
        PasswordPromptDialog dialog = new PasswordPromptDialog(account, disabledAccounts);
        setNonConfigurationInstance(dialog);
        dialog.show(this);
    }

    private void onDeleteAccount(Account account) {
        mSelectedContextAccount = account;
        showDialog(DIALOG_REMOVE_ACCOUNT);
    }

    private void onEditAccount(Account account) {
        AccountSettings.actionSettings(this, account);
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public Dialog onCreateDialog(int id) {
        // Android recreates our dialogs on configuration changes even when they have been
        // dismissed. Make sure we have all information necessary before creating a new dialog.
        switch (id) {
            case DIALOG_REMOVE_ACCOUNT: {
                if (mSelectedContextAccount == null) {
                    return null;
                }
                return AccountsDialogCreator.createRemoveAccountDialog(this, id, mSelectedContextAccount);
            }
            case DIALOG_CLEAR_ACCOUNT: {
                if (mSelectedContextAccount == null) {
                    return null;
                }
                return AccountsDialogCreator.createClearAccountDialog(this, id, mSelectedContextAccount, mHandler);
            }
            case DIALOG_RECREATE_ACCOUNT: {
                if (mSelectedContextAccount == null) {
                    return null;
                }
                return AccountsDialogCreator.createRecreateAccountDialog(this, id, mSelectedContextAccount, mHandler);
            }
            case DIALOG_NO_FILE_MANAGER: {
                return AccountsDialogCreator.createNoFileManagerDialog(this, id);
            }
        }

        return super.onCreateDialog(id);
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onPrepareDialog(int id, Dialog d) {
        AlertDialog alert = (AlertDialog) d;
        switch (id) {
            case DIALOG_REMOVE_ACCOUNT: {
                alert.setMessage(getString(R.string.account_delete_dlg_instructions_fmt,
                                           mSelectedContextAccount.getDescription()));
                break;
            }
            case DIALOG_CLEAR_ACCOUNT: {
                alert.setMessage(getString(R.string.account_clear_dlg_instructions_fmt,
                                           mSelectedContextAccount.getDescription()));
                break;
            }
            case DIALOG_RECREATE_ACCOUNT: {
                alert.setMessage(getString(R.string.account_recreate_dlg_instructions_fmt,
                                           mSelectedContextAccount.getDescription()));
                break;
            }
        }

        super.onPrepareDialog(id, d);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo)item.getMenuInfo();
        // submenus don't actually set the menuInfo, so the "advanced"
        // submenu wouldn't work.
        if (menuInfo != null) {
            mSelectedContextAccount = (BaseAccount)getListView().getItemAtPosition(menuInfo.position);
        }
        if (mSelectedContextAccount instanceof Account) {
            Account realAccount = (Account)mSelectedContextAccount;
            switch (item.getItemId()) {
                case R.id.delete_account:
                    onDeleteAccount(realAccount);
                    break;
                case R.id.account_settings:
                    onEditAccount(realAccount);
                    break;
                case R.id.activate:
                    onActivateAccount(realAccount);
                    break;
                case R.id.clear_pending:
                    onClearCommands(realAccount);
                    break;
                case R.id.empty_trash:
                    onEmptyTrash(realAccount);
                    break;
                case R.id.clear:
                    onClear(realAccount);
                    break;
                case R.id.recreate:
                    onRecreate(realAccount);
                    break;
                case R.id.export:
                    onExport(false, realAccount);
                    break;
                case R.id.move_up:
                    onMove(realAccount, true);
                    break;
                case R.id.move_down:
                    onMove(realAccount, false);
                    break;
            }
        }
        return true;
    }



    private void onClear(Account account) {
        showDialog(DIALOG_CLEAR_ACCOUNT);

    }
    private void onRecreate(Account account) {
        showDialog(DIALOG_RECREATE_ACCOUNT);
    }
    private void onMove(final Account account, final boolean up) {
        MoveAccountAsyncTask asyncTask = new MoveAccountAsyncTask(this, account, up);
        setNonConfigurationInstance(asyncTask);
        asyncTask.execute();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        BaseAccount account = (BaseAccount)parent.getItemAtPosition(position);
        onOpenAccount(account);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.add_new_account:
            onAddNewAccount();
            break;
        case R.id.edit_prefs:
            onEditPrefs();
            break;
        case R.id.check_mail:
            onCheckMail(null);
            break;
        case R.id.compose:
            onCompose();
            break;
        case R.id.about:
            onAbout();
            break;
        case R.id.search:
            onSearchRequested();
            break;
        case R.id.export_all:
            onExport(true, null);
            break;
        case R.id.import_settings:
            onImport();
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void onAbout() {
        K9AboutDialogBuilder.create(this).show();
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.accounts_option, menu);
        mRefreshMenuItem = menu.findItem(R.id.check_mail);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle(R.string.accounts_context_menu_title);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        BaseAccount account =  mAdapter.getItem(info.position);

        if ((account instanceof Account) && !((Account) account).isEnabled()) {
            getMenuInflater().inflate(R.menu.disabled_accounts_context, menu);
        } else {
            getMenuInflater().inflate(R.menu.accounts_context, menu);
        }

        if (account instanceof SearchAccount) {
            for (int i = 0; i < menu.size(); i++) {
                android.view.MenuItem item = menu.getItem(i);
                    item.setVisible(false);
            }
        }
        else {
            EnumSet<ACCOUNT_LOCATION> accountLocation = accountLocation(account);
            if (accountLocation.contains(ACCOUNT_LOCATION.TOP)) {
                menu.findItem(R.id.move_up).setEnabled(false);
            }
            else {
                menu.findItem(R.id.move_up).setEnabled(true);
            }
            if (accountLocation.contains(ACCOUNT_LOCATION.BOTTOM)) {
                menu.findItem(R.id.move_down).setEnabled(false);
            }
            else {
                menu.findItem(R.id.move_down).setEnabled(true);
            }
        }
    }

    private void onImport() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> infos = packageManager.queryIntentActivities(i, 0);

        if (infos.size() > 0) {
            startActivityForResult(Intent.createChooser(i, null),
                                   ACTIVITY_REQUEST_PICK_SETTINGS_FILE);
        } else {
            showDialog(DIALOG_NO_FILE_MANAGER);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(K9.LOG_TAG, "onActivityResult requestCode = " + requestCode + ", resultCode = "
                + resultCode + ", data = " + data);
        if (resultCode != RESULT_OK)
            return;
        if (data == null) {
            return;
        }
        switch (requestCode) {
        case ACTIVITY_REQUEST_PICK_SETTINGS_FILE:
            onImport(data.getData());
            break;
        }
    }

    private void onImport(Uri uri) {
        ListImportContentsAsyncTask asyncTask = new ListImportContentsAsyncTask(this, uri);
        setNonConfigurationInstance(asyncTask);
        asyncTask.execute();
    }

    private void showSimpleDialog(int headerRes, int messageRes, Object... args) {
        SimpleDialog dialog = new SimpleDialog(headerRes, messageRes, args);
        dialog.show(this);
        setNonConfigurationInstance(dialog);
    }

    /**
     * Shows a dialog that displays how many accounts were successfully imported.
     *
     * @param importResults
     *         The {@link ImportResults} instance returned by the {@link SettingsImporter}.
     * @param filename
     *         The name of the settings file that was imported.
     */
    private void showAccountsImportedDialog(ImportResults importResults, String filename) {
        AccountsImportedDialog dialog = new AccountsImportedDialog(importResults, filename);
        dialog.show(this);
        setNonConfigurationInstance(dialog);
    }

    /**
     * Display a dialog that lets the user select which accounts to import from the settings file.
     *
     * @param importContents
     *         The {@link ImportContents} instance returned by
     *         {@link SettingsImporter#getImportStreamContents(InputStream)}
     * @param uri
     *         The (content) URI of the settings file.
     */
    private void showImportSelectionDialog(ImportContents importContents, Uri uri) {
        ImportSelectionDialog dialog = new ImportSelectionDialog(importContents, uri);
        dialog.show(this);
        setNonConfigurationInstance(dialog);
    }

    /**
     * Set the {@code NonConfigurationInstance} this activity should retain on configuration
     * changes.
     *
     * @param inst
     *         The {@link NonConfigurationInstance} that should be retained when
     *         {@link Accounts#onRetainNonConfigurationInstance()} is called.
     */
    void setNonConfigurationInstance(NonConfigurationInstance inst) {
        mNonConfigurationInstance = inst;
    }

    class AccountsAdapter extends ArrayAdapter<BaseAccount> {
        public AccountsAdapter(List<BaseAccount> accounts) {
            super(Accounts.this, 0, accounts);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final BaseAccount account = getItem(position);
            View view;
            if (convertView != null) {
                view = convertView;
            } else {
                view = getLayoutInflater().inflate(R.layout.accounts_item, parent, false);
            }
            AccountViewHolder holder = (AccountViewHolder) view.getTag();
            if (holder == null) {
                holder = new AccountViewHolder();
                holder.description = (TextView) view.findViewById(R.id.description);
                holder.email = (TextView) view.findViewById(R.id.email);
                holder.newMessageCount = (TextView) view.findViewById(R.id.new_message_count);
                holder.flaggedMessageCount = (TextView) view.findViewById(R.id.flagged_message_count);
                holder.newMessageCountWrapper = (View) view.findViewById(R.id.new_message_count_wrapper);
                holder.flaggedMessageCountWrapper = (View) view.findViewById(R.id.flagged_message_count_wrapper);
                holder.newMessageCountIcon = (View) view.findViewById(R.id.new_message_count_icon);
                holder.flaggedMessageCountIcon = (View) view.findViewById(R.id.flagged_message_count_icon);
                holder.activeIcons = (RelativeLayout) view.findViewById(R.id.active_icons);

                holder.chip = view.findViewById(R.id.chip);
                holder.folders = (ImageButton) view.findViewById(R.id.folders);
                holder.accountsItemLayout = (LinearLayout)view.findViewById(R.id.accounts_item_layout);

                view.setTag(holder);
            }
            AccountStats stats = accountStats.get(account.getUuid());

            if (stats != null && account instanceof Account && stats.size >= 0) {
                holder.email.setText(SizeFormatter.formatSize(Accounts.this, stats.size));
                holder.email.setVisibility(View.VISIBLE);
            } else {
                if (account.getEmail().equals(account.getDescription())) {
                    holder.email.setVisibility(View.GONE);
                } else {
                    holder.email.setVisibility(View.VISIBLE);
                    holder.email.setText(account.getEmail());
                }
            }

            String description = account.getDescription();
            if (description == null || description.isEmpty()) {
                description = account.getEmail();
            }

            holder.description.setText(description);

            Integer unreadMessageCount = null;
            if (stats != null) {
                unreadMessageCount = stats.unreadMessageCount;
                holder.newMessageCount.setText(String.format("%d", unreadMessageCount));
                holder.newMessageCountWrapper.setVisibility(unreadMessageCount > 0 ? View.VISIBLE : View.GONE);

                holder.flaggedMessageCount.setText(String.format("%d", stats.flaggedMessageCount));
                holder.flaggedMessageCountWrapper.setVisibility(K9.messageListStars() && stats.flaggedMessageCount > 0 ? View.VISIBLE : View.GONE);

                holder.flaggedMessageCountWrapper.setOnClickListener(createFlaggedSearchListener(account));
                holder.newMessageCountWrapper.setOnClickListener(createUnreadSearchListener(account));

                holder.activeIcons.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Toast toast = Toast.makeText(getApplication(), getString(R.string.tap_hint), Toast.LENGTH_SHORT);
                        toast.show();
                    }
                }
                                                     );

            } else {
                holder.newMessageCountWrapper.setVisibility(View.GONE);
                holder.flaggedMessageCountWrapper.setVisibility(View.GONE);
            }
            if (account instanceof Account) {
                Account realAccount = (Account)account;

                holder.chip.setBackgroundColor(realAccount.getChipColor());

                holder.flaggedMessageCountIcon.setBackgroundDrawable( realAccount.generateColorChip(false, true).drawable() );
                holder.newMessageCountIcon.setBackgroundDrawable( realAccount.generateColorChip(false, false).drawable() );

            } else {
                holder.chip.setBackgroundColor(0xff999999);
                holder.newMessageCountIcon.setBackgroundDrawable( new ColorChip(0xff999999, false, ColorChip.CIRCULAR).drawable() );
                holder.flaggedMessageCountIcon.setBackgroundDrawable(new ColorChip(0xff999999, false, ColorChip.STAR).drawable());
            }




            mFontSizes.setViewTextSize(holder.description, mFontSizes.getAccountName());
            mFontSizes.setViewTextSize(holder.email, mFontSizes.getAccountDescription());

            if (account instanceof SearchAccount) {
                holder.folders.setVisibility(View.GONE);
            } else {
                holder.folders.setVisibility(View.VISIBLE);
                holder.folders.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        FolderList.actionHandleAccount(Accounts.this, (Account)account);

                    }
                });
            }

            return view;
        }


        private OnClickListener createFlaggedSearchListener(BaseAccount account) {
            String searchTitle = getString(R.string.search_title, account.getDescription(),
                    getString(R.string.flagged_modifier));

            LocalSearch search;
            if (account instanceof SearchAccount) {
                search = ((SearchAccount) account).getRelatedSearch().clone();
                search.setName(searchTitle);
            } else {
                search = new LocalSearch(searchTitle);
                search.addAccountUuid(account.getUuid());

                Account realAccount = (Account) account;
                realAccount.excludeSpecialFolders(search);
                realAccount.limitToDisplayableFolders(search);
            }

            search.and(SearchField.FLAGGED, "1", Attribute.EQUALS);

            return new AccountClickListener(search);
        }

        private OnClickListener createUnreadSearchListener(BaseAccount account) {
            LocalSearch search = createUnreadSearch(Accounts.this, account);
            return new AccountClickListener(search);
        }

        class AccountViewHolder {
            public TextView description;
            public TextView email;
            public TextView newMessageCount;
            public TextView flaggedMessageCount;
            public View newMessageCountIcon;
            public View flaggedMessageCountIcon;
            public View newMessageCountWrapper;
            public View flaggedMessageCountWrapper;
            public RelativeLayout activeIcons;
            public View chip;
            public ImageButton folders;
            public LinearLayout accountsItemLayout;
        }
    }

    private class AccountClickListener implements OnClickListener {

        final LocalSearch search;

        AccountClickListener(LocalSearch search) {
            this.search = search;
        }

        @Override
        public void onClick(View v) {
            MessageList.actionDisplaySearch(Accounts.this, search, true, false);
        }

    }

    public void onExport(final boolean includeGlobals, final Account account) {

        // TODO, prompt to allow a user to choose which accounts to export
        Set<String> accountUuids = null;
        if (account != null) {
            accountUuids = new HashSet<String>();
            accountUuids.add(account.getUuid());
        }

        ExportAsyncTask asyncTask = new ExportAsyncTask(this, includeGlobals, accountUuids);
        setNonConfigurationInstance(asyncTask);
        asyncTask.execute();
    }

    /**
     * Handles exporting of global settings and/or accounts in a background thread.
     */
    private static class ExportAsyncTask extends ExtendedAsyncTask<Void, Void, Boolean> {
        private boolean mIncludeGlobals;
        private Set<String> mAccountUuids;
        private String mFileName;


        private ExportAsyncTask(Accounts activity, boolean includeGlobals,
                                Set<String> accountUuids) {
            super(activity);
            mIncludeGlobals = includeGlobals;
            mAccountUuids = accountUuids;
        }

        @Override
        protected void showProgressDialog() {
            String title = mContext.getString(R.string.settings_export_dialog_title);
            String message = mContext.getString(R.string.settings_exporting);
            mProgressDialog = ProgressDialog.show(mActivity, title, message, true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                mFileName = SettingsExporter.exportToFile(mContext, mIncludeGlobals,
                            mAccountUuids);
            } catch (SettingsImportExportException e) {
                Log.w(K9.LOG_TAG, "Exception during export", e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            removeProgressDialog();

            if (success) {
                activity.showSimpleDialog(R.string.settings_export_success_header,
                                          R.string.settings_export_success, mFileName);
            } else {
                //TODO: better error messages
                activity.showSimpleDialog(R.string.settings_export_failed_header,
                                          R.string.settings_export_failure);
            }
        }
    }

    /**
     * Handles importing of global settings and/or accounts in a background thread.
     */
    static class ImportAsyncTask extends ExtendedAsyncTask<Void, Void, Boolean> {
        private boolean mIncludeGlobals;
        private List<String> mAccountUuids;
        private boolean mOverwrite;
        private Uri mUri;
        private ImportResults mImportResults;

        ImportAsyncTask(Accounts activity, boolean includeGlobals,
                        List<String> accountUuids, boolean overwrite, Uri uri) {
            super(activity);
            mIncludeGlobals = includeGlobals;
            mAccountUuids = accountUuids;
            mOverwrite = overwrite;
            mUri = uri;
        }

        @Override
        protected void showProgressDialog() {
            String title = mContext.getString(R.string.settings_import_dialog_title);
            String message = mContext.getString(R.string.settings_importing);
            mProgressDialog = ProgressDialog.show(mActivity, title, message, true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                InputStream is = mContext.getContentResolver().openInputStream(mUri);
                try {
                    mImportResults = SettingsImporter.importSettings(mContext, is,
                                     mIncludeGlobals, mAccountUuids, mOverwrite);
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        /* Ignore */
                    }
                }
            } catch (SettingsImportExportException e) {
                Log.w(K9.LOG_TAG, "Exception during import", e);
                return false;
            } catch (FileNotFoundException e) {
                Log.w(K9.LOG_TAG, "Couldn't open import file", e);
                return false;
            } catch (Exception e) {
                Log.w(K9.LOG_TAG, "Unknown error", e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            removeProgressDialog();

            String filename = mUri.getLastPathSegment();
            boolean globalSettings = mImportResults.globalSettings;
            int imported = mImportResults.importedAccounts.size();
            if (success && (globalSettings || imported > 0)) {
                if (imported == 0) {
                    activity.showSimpleDialog(R.string.settings_import_success_header,
                                              R.string.settings_import_global_settings_success, filename);
                } else {
                    activity.showAccountsImportedDialog(mImportResults, filename);
                }

                activity.refresh();
            } else {
                //TODO: better error messages
                activity.showSimpleDialog(R.string.settings_import_failed_header,
                                          R.string.settings_import_failure, filename);
            }
        }
    }

    private static class ListImportContentsAsyncTask extends ExtendedAsyncTask<Void, Void, Boolean> {
        private Uri mUri;
        private ImportContents mImportContents;

        private ListImportContentsAsyncTask(Accounts activity, Uri uri) {
            super(activity);

            mUri = uri;
        }

        @Override
        protected void showProgressDialog() {
            String title = mContext.getString(R.string.settings_import_dialog_title);
            String message = mContext.getString(R.string.settings_import_scanning_file);
            mProgressDialog = ProgressDialog.show(mActivity, title, message, true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                ContentResolver resolver = mContext.getContentResolver();
                InputStream is = resolver.openInputStream(mUri);
                try {
                    mImportContents = SettingsImporter.getImportStreamContents(is);
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        /* Ignore */
                    }
                }
            } catch (SettingsImportExportException e) {
                Log.w(K9.LOG_TAG, "Exception during export", e);
                return false;
            } catch (FileNotFoundException e) {
                Log.w(K9.LOG_TAG, "Couldn't read content from URI " + mUri);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            removeProgressDialog();

            if (success) {
                activity.showImportSelectionDialog(mImportContents, mUri);
            } else {
                String filename = mUri.getLastPathSegment();
                //TODO: better error messages
                activity.showSimpleDialog(R.string.settings_import_failed_header,
                                          R.string.settings_import_failure, filename);
            }
        }
    }

    private static class MoveAccountAsyncTask extends ExtendedAsyncTask<Void, Void, Void> {
        private Account mAccount;
        private boolean mUp;

        protected MoveAccountAsyncTask(Activity activity, Account account, boolean up) {
            super(activity);
            mAccount = account;
            mUp = up;
        }

        @Override
        protected void showProgressDialog() {
            String message = mActivity.getString(R.string.manage_accounts_moving_message);
            mProgressDialog = ProgressDialog.show(mActivity, null, message, true);
        }

        @Override
        protected Void doInBackground(Void... args) {
            mAccount.move(Preferences.getPreferences(mContext), mUp);
            return null;
        }

        @Override
        protected void onPostExecute(Void arg) {
            Accounts activity = (Accounts) mActivity;

            // Let the activity know that the background task is complete
            activity.setNonConfigurationInstance(null);

            activity.refresh();
            removeProgressDialog();
        }
    }
}
