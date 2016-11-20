package com.fsck.k9.activity;


import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.preferences.SettingsImporter;

import java.util.ArrayList;
import java.util.List;

/**
 * A dialog that displays how many accounts were successfully imported.
 */
public class AccountsImportedDialog extends SimpleDialog {
    private SettingsImporter.ImportResults mImportResults;
    private String mFilename;

    AccountsImportedDialog(SettingsImporter.ImportResults importResults, String filename) {
        super(R.string.settings_import_success_header, R.string.settings_import_success);
        mImportResults = importResults;
        mFilename = filename;
    }

    @Override
    protected String generateMessage(Accounts activity) {
        //TODO: display names of imported accounts (name from file *and* possibly new name)

        int imported = mImportResults.importedAccounts.size();
        String accounts = activity.getResources().getQuantityString(
                R.plurals.settings_import_accounts, imported, imported);
        return activity.getString(R.string.settings_import_success, accounts, mFilename);
    }

    @Override
    protected void okayAction(Accounts activity) {
        Context context = activity.getApplicationContext();
        Preferences preferences = Preferences.getPreferences(context);
        List<Account> disabledAccounts = new ArrayList<Account>();
        for (SettingsImporter.AccountDescriptionPair accountPair : mImportResults.importedAccounts) {
            Account account = preferences.getAccount(accountPair.imported.uuid);
            if (account != null && !account.isEnabled()) {
                disabledAccounts.add(account);
            }
        }
        if (disabledAccounts.size() > 0) {
            activity.promptForServerPasswords(disabledAccounts);
        } else {
            activity.setNonConfigurationInstance(null);
        }
    }
}
