package com.fsck.k9.mail.store.pop3;

public class Pop3Capabilities {
    public boolean cramMD5;
    public boolean authPlain;
    public boolean stls;
    public boolean top;
    public boolean uidl;
    public boolean external;

    @Override
    public String toString() {
        return String.format("CRAM-MD5 %b, PLAIN %b, STLS %b, TOP %b, UIDL %b, EXTERNAL %b",
                cramMD5,
                authPlain,
                stls,
                top,
                uidl,
                external);
    }
}
