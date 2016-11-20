package com.fsck.k9.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fsck.k9.Account;
import com.fsck.k9.R;
import com.fsck.k9.activity.misc.NonConfigurationInstance;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.Transport;
import com.fsck.k9.mail.store.RemoteStore;

import java.util.List;

/**
 * Ask the user for the incoming/outgoing server passwords.
 */
public class PasswordPromptDialog implements NonConfigurationInstance, TextWatcher {
    private AlertDialog mDialog;
    private EditText mIncomingPasswordView;
    private EditText mOutgoingPasswordView;
    private CheckBox mUseIncomingView;

    private Account mAccount;
    private List<Account> mRemainingAccounts;
    private String mIncomingPassword;
    private String mOutgoingPassword;
    private boolean mUseIncoming;

    /**
     * Constructor
     *
     * @param account
     *         The {@link Account} to ask the server passwords for. Never {@code null}.
     * @param accounts
     *         The (possibly empty) list of remaining accounts to ask passwords for. Never
     *         {@code null}.
     */
    PasswordPromptDialog(Account account, List<Account> accounts) {
        mAccount = account;
        mRemainingAccounts = accounts;
    }

    @Override
    public void restore(Activity activity) {
        show((Accounts) activity, true);
    }

    @Override
    public boolean retain() {
        if (mDialog != null) {
            // Retain entered passwords and checkbox state
            if (mIncomingPasswordView != null) {
                mIncomingPassword = mIncomingPasswordView.getText().toString();
            }
            if (mOutgoingPasswordView != null) {
                mOutgoingPassword = mOutgoingPasswordView.getText().toString();
                mUseIncoming = mUseIncomingView.isChecked();
            }

            // Dismiss dialog
            mDialog.dismiss();

            // Clear all references to UI objects
            mDialog = null;
            mIncomingPasswordView = null;
            mOutgoingPasswordView = null;
            mUseIncomingView = null;
            return true;
        }
        return false;
    }

    public void show(Accounts activity) {
        show(activity, false);
    }

    private void show(final Accounts activity, boolean restore) {
        ServerSettings incoming = RemoteStore.decodeStoreUri(mAccount.getStoreUri());
        ServerSettings outgoing = Transport.decodeTransportUri(mAccount.getTransportUri());

        boolean configureOutgoingServer = isOutgoingServerConfigurationRequired(outgoing);
        boolean configureIncomingServer = isIncomingServerConfigurationRequired(incoming);

        // Create a ScrollView that will be used as container for the whole layout
        final ScrollView scrollView = new ScrollView(activity);
        createDialog(activity, scrollView);
        // Use the dialog's layout inflater so its theme is used (and not the activity's theme).
        View layout = mDialog.getLayoutInflater().inflate(R.layout.accounts_password_prompt, scrollView);
        setIntroText(layout, activity, configureIncomingServer, configureOutgoingServer);

        if (configureIncomingServer) {
            showIncomingServerDetails(layout, activity, incoming);
        } else {
            layout.findViewById(R.id.incoming_server_prompt).setVisibility(View.GONE);
        }

        if (configureOutgoingServer) {
            showOutgoingServerDetails(layout, activity, outgoing);

            if (configureIncomingServer) {
                mUseIncomingView.setChecked(true);
                mUseIncomingView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mOutgoingPasswordView.setText(null);
                            mOutgoingPasswordView.setEnabled(false);
                        } else {
                            mOutgoingPasswordView.setText(mIncomingPasswordView.getText());
                            mOutgoingPasswordView.setEnabled(true);
                        }
                    }
                });
            } else {
                mUseIncomingView.setChecked(false);
                mUseIncomingView.setVisibility(View.GONE);
                mOutgoingPasswordView.setEnabled(true);
            }
        } else {
            layout.findViewById(R.id.outgoing_server_prompt).setVisibility(View.GONE);
        }

        mDialog.show();

        handleReshow(restore, configureIncomingServer, configureOutgoingServer);
    }

    private boolean isOutgoingServerConfigurationRequired(ServerSettings outgoing) {
        return AuthType.EXTERNAL != outgoing.authenticationType
                && !(ServerSettings.Type.WebDAV == outgoing.type)
                && outgoing.username != null
                && !outgoing.username.isEmpty()
                && (outgoing.password == null || outgoing.password
                .isEmpty());
    }

    private boolean isIncomingServerConfigurationRequired(ServerSettings incoming) {
        return AuthType.EXTERNAL != incoming.authenticationType
                && (incoming.password == null || incoming.password
                .isEmpty());
    }

    private void createDialog(final Accounts activity, ScrollView scrollView) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.settings_import_activate_account_header));
        builder.setView(scrollView);
        builder.setPositiveButton(activity.getString(R.string.okay_action),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handleActivateAccount(dialog, activity);
                    }
                });
        builder.setNegativeButton(activity.getString(R.string.cancel_action),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        activity.setNonConfigurationInstance(null);
                    }
                });
        mDialog = builder.create();
    }

    private void showOutgoingServerDetails(View layout, Accounts activity, ServerSettings outgoing) {
        // Display the hostname of the outgoing server
        TextView outgoingText = (TextView) layout.findViewById(
                R.id.password_prompt_outgoing_server);
        outgoingText.setText(activity.getString(R.string.settings_import_outgoing_server,
                outgoing.host));

        mOutgoingPasswordView = (EditText) layout.findViewById(
                R.id.outgoing_server_password);
        mOutgoingPasswordView.addTextChangedListener(this);

        mUseIncomingView = (CheckBox) layout.findViewById(
                R.id.use_incoming_server_password);
    }

    private void showIncomingServerDetails(View layout, Activity activity, ServerSettings incoming) {
        // Display the hostname of the incoming server
        TextView incomingText = (TextView) layout.findViewById(
                R.id.password_prompt_incoming_server);
        incomingText.setText(activity.getString(R.string.settings_import_incoming_server,
                incoming.host));

        mIncomingPasswordView = (EditText) layout.findViewById(R.id.incoming_server_password);
        mIncomingPasswordView.addTextChangedListener(this);
    }

    private void setIntroText(
            View layout, Activity activity, boolean configureIncomingServer,
            boolean configureOutgoingServer) {
        TextView intro = (TextView) layout.findViewById(R.id.password_prompt_intro);
        String serverPasswords = activity.getResources().getQuantityString(
                R.plurals.settings_import_server_passwords,
                (configureIncomingServer && configureOutgoingServer) ? 2 : 1);
        intro.setText(activity.getString(R.string.settings_import_activate_account_intro,
                mAccount.getDescription(), serverPasswords));
    }

    private void handleActivateAccount(DialogInterface dialog, Accounts activity) {
        String incomingPassword = null;
        if (mIncomingPasswordView != null) {
            incomingPassword = mIncomingPasswordView.getText().toString();
        }
        String outgoingPassword = null;
        if (mOutgoingPasswordView != null) {
            outgoingPassword = (mUseIncomingView.isChecked()) ?
                    incomingPassword : mOutgoingPasswordView.getText().toString();
        }

        dialog.dismiss();

        // Set the server passwords in the background
        SetPasswordsAsyncTask asyncTask = new SetPasswordsAsyncTask(activity, mAccount,
                incomingPassword, outgoingPassword, mRemainingAccounts);
        activity.setNonConfigurationInstance(asyncTask);
        asyncTask.execute();
    }


    /**
     * Restore the contents of the password boxes and the checkbox (if the dialog was
     * retained during a configuration change).
     */
    private void handleReshow(boolean restore, boolean configureIncomingServer, boolean configureOutgoingServer) {
        if (restore) {
            if (configureIncomingServer) {
                mIncomingPasswordView.setText(mIncomingPassword);
            }
            if (configureOutgoingServer) {
                mOutgoingPasswordView.setText(mOutgoingPassword);
                mUseIncomingView.setChecked(mUseIncoming);
            }
        } else {
            if (configureIncomingServer) {
                // Trigger afterTextChanged() being called
                // Work around this bug: https://code.google.com/p/android/issues/detail?id=6360
                mIncomingPasswordView.setText(mIncomingPasswordView.getText());
            } else {
                mOutgoingPasswordView.setText(mOutgoingPasswordView.getText());
            }
        }
    }

    @Override
    public void afterTextChanged(Editable arg0) {
        boolean enable = false;
        // Is the password box for the incoming server password empty?
        if (mIncomingPasswordView != null) {
            if (mIncomingPasswordView.getText().length() > 0) {
                // Do we need to check the outgoing server password box?
                if (mOutgoingPasswordView == null) {
                    enable = true;
                }
                // If the checkbox to use the incoming server password is checked we need to make
                // sure that the password box for the outgoing server isn't empty.
                else if (mUseIncomingView.isChecked() ||
                        mOutgoingPasswordView.getText().length() > 0) {
                    enable = true;
                }
            }
        } else {
            enable = mOutgoingPasswordView.getText().length() > 0;
        }

        // Disable "OK" button if the user hasn't specified all necessary passwords.
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enable);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Not used
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Not used
    }
}
