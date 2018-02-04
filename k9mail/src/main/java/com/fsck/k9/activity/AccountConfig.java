package com.fsck.k9.activity;


import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import com.fsck.k9.Account.DeletePolicy;
import com.fsck.k9.Account.FolderMode;
import com.fsck.k9.activity.setup.CheckDirection;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.NetworkType;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mail.store.StoreConfig;


public interface AccountConfig extends StoreConfig {
    ConnectionSecurity getIncomingSecurityType();
    AuthType getIncomingAuthType();
    String getIncomingPort();
    ConnectionSecurity getOutgoingSecurityType();
    AuthType getOutgoingAuthType();
    String getOutgoingPort();
    boolean isNotifyNewMail();
    boolean isShowOngoing();
    int getAutomaticCheckIntervalMinutes();
    int getDisplayCount();
    FolderMode getFolderPushMode();
    String getName();
    DeletePolicy getDeletePolicy();

    void init(String email, String password);

    String getEmail();
    String getDescription();
    Store getRemoteStore() throws MessagingException;

    void setName(String name);
    void setDescription(String description);
    void setDeletePolicy(DeletePolicy deletePolicy);
    void setEmail(String email);
    void setCompression(NetworkType networkType, boolean useCompression);

    void addCertificate(CheckDirection direction, X509Certificate certificate) throws CertificateException;

    void setSubscribedFoldersOnly(boolean subscribedFoldersOnly);

    void deleteCertificate(String newHost, int newPort, CheckDirection direction);
}
