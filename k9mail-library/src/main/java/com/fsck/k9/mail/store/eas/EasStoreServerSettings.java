package com.fsck.k9.mail.store.eas;

import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.ServerSettings;

import java.util.Map;

public class EasStoreServerSettings extends ServerSettings {
    public EasStoreServerSettings(Type type, String host, int port, ConnectionSecurity connectionSecurity, AuthType authenticationType, String username, String password, String clientCertificateAlias, Map<String, String> extra) {
        super(type, host, port, connectionSecurity, authenticationType, username, password, clientCertificateAlias, extra);
    }

    public String getPolicyKey() {
        return getExtra().get("policyKey");
    }

    public String getSyncKey() {
        return getExtra().get("syncKey");
    }
}
