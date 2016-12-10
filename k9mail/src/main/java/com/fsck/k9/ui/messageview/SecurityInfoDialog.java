package com.fsck.k9.ui.messageview;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.fsck.k9.R;
import com.fsck.k9.view.MessageCryptoDisplayStatus;
import com.fsck.k9.view.ThemeUtils;
import com.fsck.k9.view.TransportCryptoDisplayStatus;


public class SecurityInfoDialog extends DialogFragment {
    public static final String ARG_CRYPTO_DISPLAY_STATUS = "crypto_display_status";
    public static final String ARG_TRANSPORT_DISPLAY_STATUS = "transport_display_status";
    public static final int ICON_ANIM_DELAY = 400;
    public static final int ICON_ANIM_DURATION = 350;


    private View dialogView;

    private View topIconFrame;
    private ImageView topIcon1;
    private ImageView topIcon2;
    private ImageView topIcon3;
    private TextView topText;

    private View bottomIconFrame;
    private ImageView bottomIcon1;
    private ImageView bottomIcon2;
    private TextView bottomText;

    private View transportIconFrame;
    private ImageView transportIcon;
    private TextView transportText;


    public static SecurityInfoDialog newInstance(MessageCryptoDisplayStatus displayStatus,
                                                 TransportCryptoDisplayStatus transportState) {
        SecurityInfoDialog frag = new SecurityInfoDialog();

        Bundle args = new Bundle();
        args.putString(ARG_CRYPTO_DISPLAY_STATUS, displayStatus.toString());
        args.putString(ARG_TRANSPORT_DISPLAY_STATUS, transportState.toString());
        frag.setArguments(args);

        return frag;
    }

    @SuppressLint("InflateParams") // inflating without root element is fine for creating a dialog
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Builder b = new AlertDialog.Builder(getActivity());

        dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.message_crypto_info_dialog, null);

        topIconFrame = dialogView.findViewById(R.id.crypto_info_top_frame);
        topIcon1 = (ImageView) topIconFrame.findViewById(R.id.crypto_info_top_icon_1);
        topIcon2 = (ImageView) topIconFrame.findViewById(R.id.crypto_info_top_icon_2);
        topIcon3 = (ImageView) topIconFrame.findViewById(R.id.crypto_info_top_icon_3);
        topText = (TextView) dialogView.findViewById(R.id.crypto_info_top_text);

        bottomIconFrame = dialogView.findViewById(R.id.crypto_info_bottom_frame);
        bottomIcon1 = (ImageView) bottomIconFrame.findViewById(R.id.crypto_info_bottom_icon_1);
        bottomIcon2 = (ImageView) bottomIconFrame.findViewById(R.id.crypto_info_bottom_icon_2);
        bottomText = (TextView) dialogView.findViewById(R.id.crypto_info_bottom_text);

        transportIconFrame = dialogView.findViewById(R.id.transport_info_frame);
        transportIcon = (ImageView) transportIconFrame.findViewById(R.id.transport_info_icon);
        transportText = (TextView) dialogView.findViewById(R.id.transport_info_text);

        MessageCryptoDisplayStatus cryptoDisplayStatus =
                MessageCryptoDisplayStatus.valueOf(getArguments().getString(ARG_CRYPTO_DISPLAY_STATUS));
        setMessageForCryptoDisplayStatus(cryptoDisplayStatus);

        TransportCryptoDisplayStatus transportState =
                TransportCryptoDisplayStatus.valueOf(getArguments().getString(ARG_TRANSPORT_DISPLAY_STATUS));
        setMessageForTransportDisplayStatus(transportState);

        b.setView(dialogView);
        b.setPositiveButton(R.string.crypto_info_ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dismiss();
            }
        });

        if (cryptoDisplayStatus.hasAssociatedKey()) {
            b.setNeutralButton(R.string.crypto_info_view_key, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Fragment frag = getTargetFragment();
                    if (!(frag instanceof OnClickShowCryptoKeyListener)) {
                        throw new AssertionError("Displaying activity must implement OnClickShowCryptoKeyListener!");
                    }
                    ((OnClickShowCryptoKeyListener) frag).onClickShowCryptoKey();
                }
            });
        }

        return b.create();
    }

    private void setMessageForCryptoDisplayStatus(MessageCryptoDisplayStatus displayStatus) {
        if (displayStatus.textResTop == null) {
            throw new AssertionError("Crypto info dialog can only be displayed for items with text!");
        }

        if (displayStatus.textResBottom == null) {
            setMessageSingleLine(displayStatus.colorAttr,
                    displayStatus.textResTop, displayStatus.statusIconRes,
                    displayStatus.statusDotsRes);
        } else {
            if (displayStatus.statusDotsRes == null) {
                throw new AssertionError("second icon must be non-null if second text is non-null!");
            }
            setMessageWithAnimation(displayStatus.colorAttr,
                    displayStatus.textResTop, displayStatus.statusIconRes,
                    displayStatus.textResBottom, displayStatus.statusDotsRes);
        }
    }

    private void setMessageForTransportDisplayStatus(TransportCryptoDisplayStatus displayStatus) {
        @ColorInt int color = ThemeUtils.getStyledColor(getActivity(), displayStatus.colorAttr);
        transportIcon.setImageResource(displayStatus.statusIconRes);
        transportIcon.setColorFilter(color);
        if (displayStatus.textRes != null) {
            transportText.setText(displayStatus.textRes);
        } else {
            transportText.setVisibility(View.GONE);
        }
    }

    private void setMessageSingleLine(@AttrRes int colorAttr,
            @StringRes int topTextRes, @DrawableRes int statusIconRes,
            @DrawableRes Integer statusDotsRes) {
        @ColorInt int color = ThemeUtils.getStyledColor(getActivity(), colorAttr);

        topIcon1.setImageResource(statusIconRes);
        topIcon1.setColorFilter(color);
        topText.setText(topTextRes);

        if (statusDotsRes != null) {
            topIcon3.setImageResource(statusDotsRes);
            topIcon3.setColorFilter(color);
            topIcon3.setVisibility(View.VISIBLE);
        } else {
            topIcon3.setVisibility(View.GONE);
        }

        bottomText.setVisibility(View.GONE);
        bottomIconFrame.setVisibility(View.GONE);
    }

    private void setMessageWithAnimation(@AttrRes int colorAttr,
            @StringRes int topTextRes, @DrawableRes int statusIconRes,
            @StringRes int bottomTextRes, @DrawableRes int statusDotsRes) {
        topIcon1.setImageResource(statusIconRes);
        topIcon2.setImageResource(statusDotsRes);
        topIcon3.setVisibility(View.GONE);
        topText.setText(topTextRes);

        bottomIcon1.setImageResource(statusIconRes);
        bottomIcon2.setImageResource(statusDotsRes);
        bottomText.setText(bottomTextRes);

        topIcon1.setColorFilter(ThemeUtils.getStyledColor(getActivity(), colorAttr));
        bottomIcon2.setColorFilter(ThemeUtils.getStyledColor(getActivity(), colorAttr));

        prepareIconAnimation();
    }

    private void prepareIconAnimation() {
        topText.setAlpha(0.0f);
        bottomText.setAlpha(0.0f);

        dialogView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                float halfVerticalPixelDifference = (bottomIconFrame.getY() - topIconFrame.getY()) / 2.0f;
                topIconFrame.setTranslationY(halfVerticalPixelDifference);
                bottomIconFrame.setTranslationY(-halfVerticalPixelDifference);

                topIconFrame.animate().translationY(0)
                        .setStartDelay(ICON_ANIM_DELAY)
                        .setDuration(ICON_ANIM_DURATION)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                bottomIconFrame.animate().translationY(0)
                        .setStartDelay(ICON_ANIM_DELAY)
                        .setDuration(ICON_ANIM_DURATION)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                topText.animate().alpha(1.0f).setStartDelay(ICON_ANIM_DELAY + ICON_ANIM_DURATION).start();
                bottomText.animate().alpha(1.0f).setStartDelay(ICON_ANIM_DELAY + ICON_ANIM_DURATION).start();

                view.removeOnLayoutChangeListener(this);
            }
        });
    }

    public interface OnClickShowCryptoKeyListener {
        void onClickShowCryptoKey();
    }
}