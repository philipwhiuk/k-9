package com.fsck.k9.mail.power;

import android.content.Context;

//TODO: Move some of the getPowerManager code from TracingPowerManager into here.
public class TracingPowerManagerFactory {
    public TracingPowerManager getPowerManager(Context context) {
        return TracingPowerManager.getPowerManager(context);
    }
}
