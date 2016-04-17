package com.fsck.k9.mail.store.eas;

import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.ServerSettings;

public class EasStoreSettings extends ServerSettings {

    protected EasStoreSettings(String host, int port, ConnectionSecurity connectionSecurity,
                                  AuthType authenticationType, String username, String password,
                                  String clientCertificateAlias) {
        super(Type.WebDAV, host, port, connectionSecurity, authenticationType, username,
                password, clientCertificateAlias);
    }
}
