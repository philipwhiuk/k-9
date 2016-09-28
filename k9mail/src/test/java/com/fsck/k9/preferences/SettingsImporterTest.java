package com.fsck.k9.preferences;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.AuthType;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "src/main/AndroidManifest.xml", sdk = 21)
public class SettingsImporterTest {

    @Before
    public void before() {
        Preferences prefs = Preferences.getPreferences(RuntimeEnvironment.application);
        List<Account> accounts = prefs.getAccounts();
        for(Account account: accounts)
            prefs.deleteAccount(account);

    }

    @Test
    public void importSettings_accountWithNoIncomingServerSettingsErroneous()
            throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<outgoing-server type=\"SMTP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.ImportResults results = SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);
        assertEquals(1, results.errorneousAccounts.size());
        assertEquals("Account", results.errorneousAccounts.get(0).name);
        assertEquals(validUUID, results.errorneousAccounts.get(0).uuid);
    }

    @Test
    public void importSettings_accountWithNoOutgoingServerSettingsErroneous()
            throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.ImportResults results = SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, false);
        assertEquals(1, results.errorneousAccounts.size());
        assertEquals("Account", results.errorneousAccounts.get(0).name);
        assertEquals(validUUID, results.errorneousAccounts.get(0).uuid);
    }

    @Test
    public void importSettings_accountWithEmptySettingsErroneous() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.ImportResults results = SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);
        assertEquals(1, results.errorneousAccounts.size());
        assertEquals("Account", results.errorneousAccounts.get(0).name);
        assertEquals(validUUID, results.errorneousAccounts.get(0).uuid);
    }

    @Test
    public void importSettings_accountWithNoIdentityErroneous()
            throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.ImportResults results = SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);
        assertEquals(1, results.errorneousAccounts.size());
        assertEquals("Account", results.errorneousAccounts.get(0).name);
        assertEquals(validUUID, results.errorneousAccounts.get(0).uuid);
    }

    @Test
    public void importSettings_accountWithBadIdentityEmailErroneous()
            throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user.gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.ImportResults results = SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);
        assertEquals(1, results.errorneousAccounts.size());
        assertEquals("Account", results.errorneousAccounts.get(0).name);
        assertEquals(validUUID, results.errorneousAccounts.get(0).uuid);
    }

    private String validSettingsWithAccount(String accountUUID) {
        return "<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+accountUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                "<username>user</username>" +
                "<password>pass</password>" +
                "<authentication-type>PLAIN</authentication-type>" +
                "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>";
    }


    @Test
    public void importSettings_renamesAccountsWithDuplicateNames() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream(validSettingsWithAccount(validUUID));
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);

        String validUUID2 = UUID.randomUUID().toString();
        InputStream inputStream2 = new StringInputStream(validSettingsWithAccount(validUUID2));
        List<String> accountUuids2 = new ArrayList<>();
        accountUuids2.add(validUUID2);
        SettingsImporter.ImportResults results = SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream2, true, accountUuids2, true);

        assertEquals(0, results.errorneousAccounts.size());
        assertEquals(1, results.importedAccounts.size());
        assertEquals("Account", results.importedAccounts.get(0).original.name);
        assertEquals("Account (1)", results.importedAccounts.get(0).imported.name);
        assertEquals(validUUID2, results.importedAccounts.get(0).imported.uuid);

        assertEquals(2, Preferences.getPreferences(RuntimeEnvironment.application)
                .getAccounts().size());
    }

    @Test
    public void importSettings_enablesAccountsWithPassword() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                    "<incoming-server type=\"IMAP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user</username>" +
                    "<password>pass</password>" +
                    "<authentication-type>PLAIN</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user</username>" +
                    "<password>pass</password>" +
                    "<authentication-type>PLAIN</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);

        assertTrue(Preferences.getPreferences(RuntimeEnvironment.application)
                .getAccount(validUUID).isEnabled());
    }

    @Test
    public void importSettings_disablesAccountsWithNoPassword() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user</username>" +
                    "<authentication-type>PLAIN</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user</username>" +
                    "<authentication-type>PLAIN</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);

        assertFalse(Preferences.getPreferences(RuntimeEnvironment.application)
                .getAccount(validUUID).isEnabled());
    }

    @Test
    public void importSettings_doesNotDisableExternalAuthAccountsWithNoPassword()
            throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user@gmail.com</username>" +
                    "<authentication-type>EXTERNAL</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user@googlemail.com</username>" +
                    "<authentication-type>EXTERNAL</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);

        assertTrue(Preferences.getPreferences(RuntimeEnvironment.application)
                .getAccount(validUUID).isEnabled());
    }

    @Test
    public void importSettings_doesNotDisableXoauth2AuthAccountsWithNoPassword()
            throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream("<k9settings format=\"1\" version=\"1\">" +
                "<accounts><account uuid=\""+validUUID+"\"><name>Account</name>" +
                "<incoming-server type=\"IMAP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user@gmail.com</username>" +
                    "<authentication-type>XOAUTH2</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</incoming-server>" +
                "<outgoing-server type=\"SMTP\">" +
                    "<connection-security>SSL_TLS_REQUIRED</connection-security>" +
                    "<username>user@googlemail.com</username>" +
                    "<authentication-type>XOAUTH2</authentication-type>" +
                    "<host>googlemail.com</host>" +
                "</outgoing-server>" +
                "<settings><value key=\"a\">b</value></settings>" +
                "<identities><identity><email>user@gmail.com</email></identity></identities>" +
                "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.importSettings(
                RuntimeEnvironment.application, inputStream, true, accountUuids, true);

        assertTrue(Preferences.getPreferences(RuntimeEnvironment.application)
                .getAccount(validUUID).isEnabled());
    }

}
