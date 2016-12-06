package com.fsck.k9.view;


import android.support.annotation.AttrRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.fsck.k9.R;
import com.fsck.k9.mail.internet.SecureTransportState;


public enum TransportCryptoDisplayStatus {
    UNKNOWN (
            R.attr.openpgp_blue,
            R.drawable.status_lock,
            R.string.transport_crypto_msg_unknown
    ),

    ENCRYPTED (
            R.attr.openpgp_green,
            R.drawable.status_lock,
            R.string.transport_crypto_msg_encrypted
    ),

    UNENCRYPTED (
            R.attr.openpgp_grey,
            R.drawable.status_lock_disabled,
            R.string.transport_crypto_msg_unencrypted
    );

    @AttrRes public final int colorAttr;
    @DrawableRes public final int statusIconRes;
    @StringRes public final Integer textRes;

    TransportCryptoDisplayStatus(@AttrRes int colorAttr, @DrawableRes int statusIconRes, @StringRes int textRes) {
        this.colorAttr = colorAttr;
        this.statusIconRes = statusIconRes;

        this.textRes = textRes;
    }

    TransportCryptoDisplayStatus(@AttrRes int colorAttr, @DrawableRes int statusIconRes) {
        this.colorAttr = colorAttr;
        this.statusIconRes = statusIconRes;
        this.textRes = null;
    }

    @NonNull
    public static TransportCryptoDisplayStatus fromResultAnnotation(SecureTransportState secureTransportState) {
        if (secureTransportState == null) {
            return UNKNOWN;
        }

        switch (secureTransportState) {
            case SECURE:
                return ENCRYPTED;
            case INSECURE:
                return UNENCRYPTED;
            case UNKNOWN:
                return UNKNOWN;
        }
        throw new IllegalStateException("Unhandled case!");
    }
}
