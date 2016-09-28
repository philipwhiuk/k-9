package com.fsck.k9.preferences;

import com.fsck.k9.mail.AuthType;

import org.apache.tools.ant.filters.StringInputStream;
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

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "src/main/AndroidManifest.xml", sdk = 21)
public class SettingsParserTest {

    @Test
    public void parseSettings_account() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream(
                "<k9settings format=\"1\" version=\"1\">" +
                        "<accounts><account uuid=\""+validUUID+"\">" +
                        "<name>Account</name>" +
                        "</account></accounts>" +
                        "</k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add("1");
        SettingsImporter.Imported results = SettingsParser.parseSettings(
                inputStream, true, accountUuids, true);

        assertEquals(1, results.accounts.size());
        assertEquals("Account", results.accounts.get(validUUID).name);
        assertEquals(validUUID, results.accounts.get(validUUID).uuid);
    }

    @Test
    public void parseSettings_accountFolder() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream(
                "<k9settings format=\"1\" version=\"1\">" +
                        "<accounts><account uuid=\""+validUUID+"\">" +
                        "<name>Account</name>" +
                        "<folders>" +
                            "<folder name=\"Inbox\">" +
                                "<value key=\"a\">b</value>" +
                            "</folder>" +
                        "</folders>" +
                        "</account></accounts>" +
                        "</k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.Imported results = SettingsParser.parseSettings(
                inputStream, true, accountUuids, false);

        assertEquals(1, results.accounts.get(validUUID).folders.size());
        assertEquals("Inbox", results.accounts.get(validUUID).folders.get(0).name);
        assertEquals(1, results.accounts.get(validUUID).folders.get(0).settings.settings.size());
        assertEquals("b", results.accounts.get(validUUID).folders.get(0).settings.settings.get("a"));
    }

    @Test
    public void parseSettings_accountIdentity() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream(
            "<k9settings format=\"1\" version=\"1\">" +
                "<accounts>" +
                    "<account uuid=\""+validUUID+"\">" +
                        "<name>Account</name>" +
                        "<identities>" +
                            "<identity>" +
                                "<name>Main</name>" +
                                "<email>user@email.com</email>" +
                                "<description>Main identity</description>" +
                                "<settings>" +
                                    "<value key=\"a\">b</value>" +
                                "</settings>" +
                            "</identity>" +
                        "</identities>" +
                    "</account>" +
                "</accounts>" +
            "</k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.Imported results = SettingsParser.parseSettings(
                inputStream, true, accountUuids, false);

        assertEquals(1, results.accounts.get(validUUID).identities.size());
        assertEquals("Main", results.accounts.get(validUUID).identities.get(0).name);
        assertEquals("Main identity", results.accounts.get(validUUID).identities.get(0).description);
        assertEquals("user@email.com", results.accounts.get(validUUID).identities.get(0).email);
        assertEquals(1, results.accounts.get(validUUID).identities.get(0).settings.settings.size());
        assertEquals("b", results.accounts.get(validUUID).identities.get(0).settings.settings.get("a"));
    }

    @Test
    public void parseSettings_account_xoauth2() throws SettingsImportExportException {
        String validUUID = UUID.randomUUID().toString();
        InputStream inputStream = new StringInputStream(
                "<k9settings format=\"1\" version=\"1\">" +
                        "<accounts><account uuid=\""+validUUID+"\">" +
                        "<name>Account</name>" +
                        "<incoming-server>" +
                        "<authentication-type>XOAUTH2</authentication-type>" +
                        "</incoming-server>" +
                        "</account></accounts></k9settings>");
        List<String> accountUuids = new ArrayList<>();
        accountUuids.add(validUUID);
        SettingsImporter.Imported results = SettingsParser.parseSettings(
                inputStream, true, accountUuids, false);

        assertEquals("Account", results.accounts.get(validUUID).name);
        assertEquals(validUUID, results.accounts.get(validUUID).uuid);
        assertEquals(AuthType.XOAUTH2, results.accounts.get(validUUID).incoming.authenticationType);
    }

    @Test
    public void parseSettings_global() throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream(
                "<k9settings format=\"1\" version=\"1\">" +
                    "<global>" +
                        "<value key=\"a\">b</value>" +
                    "</global>" +
                "</k9settings>");
        SettingsImporter.Imported results = SettingsParser.parseSettings(
                inputStream, true, new ArrayList<String>(), false);

        assertEquals(1, results.globalSettings.settings.size());
        assertEquals("b", results.globalSettings.settings.get("a"));
    }

    @Test(expected = SettingsImportExportException.class)
    public void parseSettings_throwsExceptionOnBlankFile()
            throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream("");

        SettingsParser.parseSettings(inputStream, true, null, true);
    }

    @Test(expected = SettingsImportExportException.class)
    public void parseSettings_throwsExceptionOnMissingFormat()
            throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream(
                "<k9settings version=\"1\"></k9settings>");

        SettingsParser.parseSettings(inputStream, true, null, true);
    }

    @Test(expected = SettingsImportExportException.class)
    public void parseSettings_throwsExceptionOnInvalidFormat()
            throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream(
                "<k9settings version=\"1\" format=\"A\"></k9settings>");

        SettingsParser.parseSettings(inputStream, true, null, true);
    }

    @Test(expected = SettingsImportExportException.class)
    public void parseSettings_throwsExceptionOnNonPositiveFormat()
            throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream(
                "<k9settings version=\"1\" format=\"0\"></k9settings>");

        SettingsParser.parseSettings(inputStream, true, null, true);
    }

    @Test(expected = SettingsImportExportException.class)
    public void parseSettings_throwsExceptionOnMissingVersion()
            throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream(
                "<k9settings format=\"1\"></k9settings>");

        SettingsParser.parseSettings(inputStream, true, null, true);
    }

    @Test(expected = SettingsImportExportException.class)
    public void parseSettings_throwsExceptionOnInvalidVersion()
            throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream(
                "<k9settings format=\"1\" version=\"A\"></k9settings>");

        SettingsParser.parseSettings(inputStream, true, null, true);
    }

    @Test(expected = SettingsImportExportException.class)
    public void parseSettings_throwsExceptionOnNonPositiveVersion()
            throws SettingsImportExportException {
        InputStream inputStream = new StringInputStream(
                "<k9settings format=\"1\" version=\"0\"></k9settings>");

        SettingsParser.parseSettings(inputStream, true, null, true);
    }
}
