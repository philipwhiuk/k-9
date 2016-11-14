package com.fsck.k9.mail;


public class CryptoMimeTypes {
    public static final String MULTIPART_ENCRYPTED = "multipart/encrypted";
    public static final String MULTIPART_SIGNED = "multipart/signed";
    public static final String PROTOCOL_PARAMETER = "protocol";
    public static final String APPLICATION_PGP_ENCRYPTED = "application/pgp-encrypted";
    public static final String APPLICATION_PGP_SIGNATURE = "application/pgp-signature";
    public static final String APPLICATION_SMIME_ENCRYPTED = "application/pkcs7-mime";
    public static final String APPLICATION_SMIME_SIGNATURE = "application/pkcs7-signature";
    public static final String TEXT_PLAIN = "text/plain";
    // APPLICATION/PGP is a special case which occurs from mutt. see http://www.mutt.org/doc/PGP-Notes.txt
    public static final String APPLICATION_PGP = "application/pgp";
}
