package com.fsck.k9.mail;

import org.junit.Test;

import java.security.cert.X509Certificate;

import static junit.framework.Assert.assertEquals;

public class CertificateChainExceptionTest {

    @Test
    public void getCertChain_returnsChain() {
        X509Certificate[] chain = new X509Certificate[]{};
        CertificateChainException exception = new CertificateChainException(
                "Message", chain, new Exception());
        assertEquals(chain, exception.getCertChain());
    }
}
