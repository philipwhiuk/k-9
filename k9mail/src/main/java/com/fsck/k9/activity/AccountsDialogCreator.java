package com.fsck.k9.activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;

import com.fsck.k9.Account;
import com.fsck.k9.BaseAccount;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.controller.MessagingController;

public class AccountsDialogCreator {

    /**
     * URL used to open Android Market application
     */
    private static final String FILE_MANAGER_ANDROID_MARKET_URL =
            "https://play.google.com/store/apps/details?id=org.openintents.filemanager";

    @SuppressLint("StringFormatInvalid")
    public static Dialog createRemoveAccountDialog(
            final Accounts accounts, int id, final BaseAccount mSelectedContextAccount) {


        return ConfirmationDialog.create(accounts, id,
                R.string.account_delete_dlg_title,
                accounts.getString(R.string.account_delete_dlg_instructions_fmt,
                        mSelectedContextAccount.getDescription()),
                R.string.okay_action,
                R.string.cancel_action,
                new Runnable() {
                    @Override
                    public void run() {
                        if (mSelectedContextAccount instanceof Account) {
                            Account realAccount = (Account) mSelectedContextAccount;
                            try {
                                realAccount.getLocalStore().delete();
                            } catch (Exception e) {
                                // Ignore, this may lead to localStores on sd-cards that
                                // are currently not inserted to be left
                            }
                            MessagingController.getInstance(accounts.getApplication())
                                    .deleteAccount(realAccount);
                            Preferences.getPreferences(accounts)
                                    .deleteAccount(realAccount);
                            K9.setServicesEnabled(accounts);
                            accounts.refresh();
                        }
                    }
                });
    }

    @SuppressLint("StringFormatInvalid")
    public static Dialog createClearAccountDialog(
            final Accounts accounts, int id, final BaseAccount mSelectedContextAccount,
            final Accounts.AccountsHandler mHandler) {
        return ConfirmationDialog.create(accounts, id,
                R.string.account_clear_dlg_title,
                accounts.getString(R.string.account_clear_dlg_instructions_fmt,
                        mSelectedContextAccount.getDescription()),
                R.string.okay_action,
                R.string.cancel_action,
                new Runnable() {
                    @Override
                    public void run() {
                        if (mSelectedContextAccount instanceof Account) {
                            Account realAccount = (Account) mSelectedContextAccount;
                            mHandler.workingAccount(realAccount,
                                    R.string.clearing_account);
                            MessagingController.getInstance(accounts.getApplication())
                                    .clear(realAccount, null);
                        }
                    }
                });
    }

    @SuppressLint("StringFormatInvalid")
    public static Dialog createRecreateAccountDialog(
            final Accounts accounts, int id, final BaseAccount mSelectedContextAccount,
            final Accounts.AccountsHandler mHandler) {

        return ConfirmationDialog.create(accounts, id,
                R.string.account_recreate_dlg_title,
                accounts.getString(R.string.account_recreate_dlg_instructions_fmt,
                        mSelectedContextAccount.getDescription()),
                R.string.okay_action,
                R.string.cancel_action,
                new Runnable() {
                    @Override
                    public void run() {
                        if (mSelectedContextAccount instanceof Account) {
                            Account realAccount = (Account) mSelectedContextAccount;
                            mHandler.workingAccount(realAccount,
                                    R.string.recreating_account);
                            MessagingController.getInstance(accounts.getApplication())
                                    .recreate(realAccount, null);
                        }
                    }
                });
    }

    public static Dialog createNoFileManagerDialog(
            final Accounts accounts, int id) {

        return ConfirmationDialog.create(accounts, id,
                R.string.import_dialog_error_title,
                accounts.getString(R.string.import_dialog_error_message),
                R.string.open_market,
                R.string.close,
                new Runnable() {
                    @Override
                    public void run() {
                        Uri uri = Uri.parse(FILE_MANAGER_ANDROID_MARKET_URL);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        accounts.startActivity(intent);
                    }
                });
    }
}
