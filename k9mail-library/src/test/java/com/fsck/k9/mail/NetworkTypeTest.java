package com.fsck.k9.mail;

import android.net.ConnectivityManager;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class NetworkTypeTest {
    @Test
    public void convertsTypesCorrectly() {
        assertEquals(NetworkType.MOBILE,
                NetworkType.fromConnectivityManagerType(ConnectivityManager.TYPE_MOBILE));
        assertEquals(NetworkType.WIFI,
                NetworkType.fromConnectivityManagerType(ConnectivityManager.TYPE_WIFI));
        assertEquals(NetworkType.OTHER,
                NetworkType.fromConnectivityManagerType(-1));
    }
}
