package com.fsck.k9.activity;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.util.SparseBooleanArray;
import android.widget.ListView;

import com.fsck.k9.R;
import com.fsck.k9.activity.misc.NonConfigurationInstance;
import com.fsck.k9.preferences.SettingsImporter;

import java.util.ArrayList;
import java.util.List;


/**
 * A dialog that lets the user select which accounts to import from the settings file.
 */
public class ImportSelectionDialog implements NonConfigurationInstance {
    private SettingsImporter.ImportContents mImportContents;
    private Uri mUri;
    private AlertDialog mDialog;
    private SparseBooleanArray mSelection;


    ImportSelectionDialog(SettingsImporter.ImportContents importContents, Uri uri) {
        mImportContents = importContents;
        mUri = uri;
    }

    @Override
    public void restore(Activity activity) {
        show((Accounts) activity, mSelection);
    }

    @Override
    public boolean retain() {
        if (mDialog != null) {
            // Save the selection state of each list item
            mSelection = mDialog.getListView().getCheckedItemPositions();

            mDialog.dismiss();
            mDialog = null;
            return true;
        }
        return false;
    }

    public void show(Accounts activity) {
        show(activity, null);
    }

    public void show(final Accounts activity, SparseBooleanArray selection) {
        List<String> contents = new ArrayList<String>();

        if (mImportContents.globalSettings) {
            contents.add(activity.getString(R.string.settings_import_global_settings));
        }

        for (SettingsImporter.AccountDescription account : mImportContents.accounts) {
            contents.add(account.name);
        }

        int count = contents.size();
        boolean[] checkedItems = new boolean[count];
        if (selection != null) {
            for (int i = 0; i < count; i++) {
                checkedItems[i] = selection.get(i);
            }
        } else {
            for (int i = 0; i < count; i++) {
                checkedItems[i] = true;
            }
        }

        //TODO: listview header: "Please select the settings you wish to import"
        //TODO: listview footer: "Select all" / "Select none" buttons?
        //TODO: listview footer: "Overwrite existing accounts?" checkbox

        DialogInterface.OnMultiChoiceClickListener listener = new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                ((AlertDialog) dialog).getListView().setItemChecked(which, isChecked);
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMultiChoiceItems(contents.toArray(new String[0]), checkedItems, listener);
        builder.setTitle(activity.getString(R.string.settings_import_selection));
        builder.setInverseBackgroundForced(true);
        builder.setPositiveButton(R.string.okay_action,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        importSelection(activity, dialog);
                    }
                });
        builder.setNegativeButton(R.string.cancel_action,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        activity.setNonConfigurationInstance(null);
                    }
                });
        mDialog = builder.show();
    }

    private void importSelection(Accounts activity, DialogInterface dialog) {

        ListView listView = ((AlertDialog) dialog).getListView();
        SparseBooleanArray pos = listView.getCheckedItemPositions();

        boolean includeGlobals = mImportContents.globalSettings ? pos.get(0) : false;
        List<String> accountUuids = new ArrayList<String>();
        int start = mImportContents.globalSettings ? 1 : 0;
        for (int i = start, end = listView.getCount(); i < end; i++) {
            if (pos.get(i)) {
                accountUuids.add(mImportContents.accounts.get(i - start).uuid);
            }
        }

        /*
         * TODO: Think some more about this. Overwriting could change the store
         * type. This requires some additional code in order to work smoothly
         * while the app is running.
         */
        boolean overwrite = false;

        dialog.dismiss();
        activity.setNonConfigurationInstance(null);

        Accounts.ImportAsyncTask importAsyncTask = new Accounts.ImportAsyncTask(activity,
                includeGlobals, accountUuids, overwrite, mUri);
        activity.setNonConfigurationInstance(importAsyncTask);
        importAsyncTask.execute();
    }
}
