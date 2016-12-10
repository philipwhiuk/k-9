package com.fsck.k9.mail.store.eas;


import android.util.Log;

import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.ssl.TrustedSocketFactory;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

//TODO: Stop using Apache HTTP
//TODO: Use TrustedSocketFactory
public class EasHttpClientFactory {
    // Connection timeout is the time given to connect to the server before reporting an IOException.
    private static final int CONNECTION_TIMEOUT = 20 * 1000;


    public HttpClient createNewHttpClient(String host) throws MessagingException {
        HttpParams params = new BasicHttpParams();

        // Disable automatic redirects on the http client.
        params.setBooleanParameter("http.protocol.handle-redirects", false);
        HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
        HttpConnectionParams.setSocketBufferSize(params, 8192);

        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRoute() {
            @Override
            public int getMaxForRoute(HttpRoute route) {
                    // We will allow up to 4 connections to the Exchange server.
                            return 4;
                }
        });

        SchemeRegistry reg = new SchemeRegistry();
        try {
            reg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            reg.register(new Scheme("https", new EasSocketFactory(host, 443), 443));
        } catch (NoSuchAlgorithmException nsa) {
            Log.e(K9MailLib.LOG_TAG, "NoSuchAlgorithmException in getHttpClient: " + nsa);
            throw new MessagingException("NoSuchAlgorithmException in getHttpClient: " + nsa);
        } catch (KeyManagementException kme) {
            Log.e(K9MailLib.LOG_TAG, "KeyManagementException in getHttpClient: " + kme);
            throw new MessagingException("KeyManagementException in getHttpClient: " + kme);
        }

        // Create a thread-safe connection manager so that this class can be used from multiple threads.
        ClientConnectionManager cm = new ThreadSafeClientConnManager(params, reg);
        return new DefaultHttpClient(cm, params);
    }
}
