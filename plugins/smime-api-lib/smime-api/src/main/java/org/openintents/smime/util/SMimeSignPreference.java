package org.openintents.smime.util;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;

import org.openintents.smime.R;


public class SMimeSignPreference extends CheckBoxPreference {
    public SMimeSignPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        return isChecked() ? getContext().getString(R.string.smime_signing_unencrypted_enabled)
                : getContext().getString(R.string.smime_signing_unencrypted_disabled);
    }
}
