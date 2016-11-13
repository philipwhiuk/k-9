package org.openintents.openpgp.util;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;

import org.openintents.openpgp.R;


public class OpenPgpSignPreference extends CheckBoxPreference {
    public OpenPgpSignPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        return isChecked() ? getContext().getString(R.string.openpgp_signing_unencrypted_enabled)
                : getContext().getString(R.string.openpgp_signing_unencrypted_disabled);
    }
}
