/*
 * Copyright (C) 2015 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openintents.smime.util;


import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import org.openintents.smime.ISMimeService;
import org.openintents.smime.SMimeError;
import org.openintents.smime.R;
import org.openintents.smime.SMimeError;

public class SMimeCertPreference extends Preference {
    private long mCertId;
    private String mSMimeProvider;
    private SMimeServiceConnection mServiceConnection;
    private String mDefaultUserId;

    public static final int REQUEST_CODE_CERT_PREFERENCE = 8999;

    private static final int NO_KEY = 0;

    public SMimeCertPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        return (mCertId == NO_KEY) ? getContext().getString(R.string.smime_no_cert_selected)
                : getContext().getString(R.string.smime_cert_selected);
    }

    private void updateEnabled() {
        if (TextUtils.isEmpty(mSMimeProvider)) {
            setEnabled(false);
        } else {
            setEnabled(true);
        }
    }

    public void setSMimeProvider(String packageName) {
        mSMimeProvider = packageName;
        updateEnabled();
    }

    public void setDefaultUserId(String userId) {
        mDefaultUserId = userId;
    }

    @Override
    protected void onClick() {
        // bind to service
        mServiceConnection = new SMimeServiceConnection(
                getContext().getApplicationContext(),
                mSMimeProvider,
                new SMimeServiceConnection.OnBound() {
                    @Override
                    public void onBound(ISMimeService service) {

                        getSignKeyId(new Intent());
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(SMimeApi.TAG, "exception on binding!", e);
                    }
                }
        );
        mServiceConnection.bindToService();
    }

    private void getSignKeyId(Intent data) {
        data.setAction(SMimeApi.ACTION_GET_SIGN_KEY_ID);
        data.putExtra(SMimeApi.EXTRA_USER_ID, mDefaultUserId);

        SMimeApi api = new SMimeApi(getContext(), mServiceConnection.getService());
        api.executeApiAsync(data, null, null, new MyCallback(REQUEST_CODE_CERT_PREFERENCE));
    }

    private class MyCallback implements SMimeApi.ISMimeCallback {
        int requestCode;

        private MyCallback(int requestCode) {
            this.requestCode = requestCode;
        }

        @Override
        public void onReturn(Intent result) {
            switch (result.getIntExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_ERROR)) {
                case SMimeApi.RESULT_CODE_SUCCESS: {

                    long keyId = result.getLongExtra(SMimeApi.EXTRA_SIGN_KEY_ID, NO_KEY);
                    save(keyId);

                    break;
                }
                case SMimeApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {

                    PendingIntent pi = result.getParcelableExtra(SMimeApi.RESULT_INTENT);
                    try {
                        Activity act = (Activity) getContext();
                        act.startIntentSenderFromChild(
                                act, pi.getIntentSender(),
                                requestCode, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(SMimeApi.TAG, "SendIntentException", e);
                    }
                    break;
                }
                case SMimeApi.RESULT_CODE_ERROR: {
                    SMimeError error = result.getParcelableExtra(SMimeApi.RESULT_ERROR);
                    if (error != null) {
                        Log.e(SMimeApi.TAG, "RESULT_CODE_ERROR: " + error.getMessage());
                    } else {
                        Log.e(SMimeApi.TAG, "RESULT_CODE_ERROR with no error message");
                    }
                    break;
                }
            }
        }
    }

    private void save(long newValue) {
        // Give the client a chance to ignore this change if they deem it
        // invalid
        if (!callChangeListener(newValue)) {
            // They don't want the value to be set
            return;
        }

        setAndPersist(newValue);
    }

    /**
     * Public API
     */
    public void setValue(long keyId) {
        setAndPersist(keyId);
    }

    /**
     * Public API
     */
    public long getValue() {
        return mCertId;
    }

    private void setAndPersist(long newValue) {
        mCertId = newValue;

        // Save to persistent storage (this method will make sure this
        // preference should be persistent, along with other useful checks)
        persistLong(mCertId);

        // Data has changed, notify so UI can be refreshed!
        notifyChanged();

        // also update summary
        setSummary(getSummary());
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // This preference type's value type is Long, so we read the default
        // value from the attributes as an Integer.
        return (long) a.getInteger(index, NO_KEY);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            // Restore state
            mCertId = getPersistedLong(mCertId);
        } else {
            // Set state
            long value = (Long) defaultValue;
            setAndPersist(value);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        /*
         * Suppose a client uses this preference type without persisting. We
         * must save the instance state so it is able to, for example, survive
         * orientation changes.
         */

        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedState myState = new SavedState(superState);
        myState.certId = mCertId;
        myState.sMimeProvider = mSMimeProvider;
        myState.defaultUserId = mDefaultUserId;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        // Restore the instance state
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mCertId = myState.certId;
        mSMimeProvider = myState.sMimeProvider;
        mDefaultUserId = myState.defaultUserId;
        notifyChanged();
    }

    /**
     * SavedState, a subclass of {@link BaseSavedState}, will store the state
     * of MyPreference, a subclass of Preference.
     * <p/>
     * It is important to always call through to super methods.
     */
    private static class SavedState extends BaseSavedState {
        long certId;
        String sMimeProvider;
        String defaultUserId;

        public SavedState(Parcel source) {
            super(source);

            certId = source.readInt();
            sMimeProvider = source.readString();
            defaultUserId = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeLong(certId);
            dest.writeString(sMimeProvider);
            dest.writeString(defaultUserId);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    public boolean handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CERT_PREFERENCE && resultCode == Activity.RESULT_OK) {
            getSignKeyId(data);
            return true;
        } else {
            return false;
        }
    }

}