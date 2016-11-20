package com.fsck.k9.activity;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

import com.fsck.k9.R;
import com.fsck.k9.activity.misc.NonConfigurationInstance;

/**
 * A simple dialog.
 */
public class SimpleDialog implements NonConfigurationInstance {
    private final int mHeaderRes;
    private final int mMessageRes;
    private Object[] mArguments;
    private Dialog mDialog;

    SimpleDialog(int headerRes, int messageRes, Object... args) {
        this.mHeaderRes = headerRes;
        this.mMessageRes = messageRes;
        this.mArguments = args;
    }

    @Override
    public void restore(Activity activity) {
        show((Accounts) activity);
    }

    @Override
    public boolean retain() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
            return true;
        }
        return false;
    }

    public void show(final Accounts activity) {
        final String message = generateMessage(activity);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(mHeaderRes);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.okay_action,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        activity.setNonConfigurationInstance(null);
                        okayAction(activity);
                    }
                });
        mDialog = builder.show();
    }

    /**
     * Returns the message the dialog should display.
     *
     * @param activity
     *         The {@code Activity} this dialog belongs to.
     *
     * @return The message the dialog should display
     */
    protected String generateMessage(Accounts activity) {
        return activity.getString(mMessageRes, mArguments);
    }

    /**
     * This method is called after the "OK" button was pressed.
     *
     * @param activity
     *         The {@code Activity} this dialog belongs to.
     */
    protected void okayAction(Accounts activity) {
        // Do nothing
    }
}