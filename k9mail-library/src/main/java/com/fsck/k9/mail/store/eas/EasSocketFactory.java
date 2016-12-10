package com.fsck.k9.mail.store.eas;

import com.fsck.k9.mail.ssl.DefaultTrustedSocketFactory;
import com.fsck.k9.mail.ssl.TrustManagerFactory;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;


/*
 * TODO: find out what's going on here and document it.
 * TODO: Same code as WebDav currently. Move to common HTTP package if still the case
 * Using two socket factories looks suspicious.
 */
public class EasSocketFactory implements LayeredSocketFactory {
    private SSLSocketFactory mSocketFactory;
    private org.apache.http.conn.ssl.SSLSocketFactory mSchemeSocketFactory;

    public EasSocketFactory(String host, int port) throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {
                TrustManagerFactory.get(host, port)
        }, null);
        mSocketFactory = sslContext.getSocketFactory();
        mSchemeSocketFactory = org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory();
        mSchemeSocketFactory.setHostnameVerifier(
                org.apache.http.conn.ssl.SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
    }

    public Socket connectSocket(Socket sock, String host, int port,
            InetAddress localAddress, int localPort, HttpParams params)
            throws IOException, ConnectTimeoutException {
        return mSchemeSocketFactory.connectSocket(sock, host, port, localAddress, localPort, params);
    }

    @Override
    public Socket createSocket() throws IOException {
        return mSocketFactory.createSocket();
    }

    public boolean isSecure(Socket sock) throws IllegalArgumentException {
        return mSchemeSocketFactory.isSecure(sock);
    }
    public Socket createSocket(
            final Socket socket,
            final String host,
            final int port,
            final boolean autoClose
    ) throws IOException {
        SSLSocket sslSocket = (SSLSocket) mSocketFactory.createSocket(
                socket,
                host,
                port,
                autoClose
        );
        DefaultTrustedSocketFactory.setSniHost(mSocketFactory, sslSocket, host);
        //hostnameVerifier.verify(host, sslSocket);
        // verifyHostName() didn't blowup - good!
        return sslSocket;
    }
}
