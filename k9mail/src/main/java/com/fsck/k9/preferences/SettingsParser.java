package com.fsck.k9.preferences;

import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.fsck.k9.K9;
import com.fsck.k9.mail.AuthType;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SettingsParser {

    static SettingsImporter.Imported parseSettings(
            InputStream inputStream, boolean globalSettings, List<String> accountUuids, boolean overview)
        throws SettingsImportExportException {

        if (!overview && accountUuids == null) {
            throw new IllegalArgumentException("Argument 'accountUuids' must not be null.");
        }

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            //factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();

            InputStreamReader reader = new InputStreamReader(inputStream);
            xpp.setInput(reader);

            SettingsImporter.Imported imported = null;
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_TAG) {
                    if (SettingsExporter.ROOT_ELEMENT.equals(xpp.getName())) {
                        imported = parseRoot(xpp, globalSettings, accountUuids, overview);
                    } else {
                        Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                    }
                }
                eventType = xpp.next();
            }

            if (imported == null || (overview && imported.globalSettings == null &&
                    imported.accounts == null)) {
                throw new SettingsImportExportException("Invalid import data");
            }

            return imported;
        } catch (Exception e) {
            throw new SettingsImportExportException(e);
        }
    }

    private static SettingsImporter.Imported parseRoot(
            XmlPullParser xpp, boolean globalSettings, List<String> accountUuids, boolean overview)
            throws XmlPullParserException, IOException, SettingsImportExportException {

        SettingsImporter.Imported result = new SettingsImporter.Imported();

        String fileFormatVersionString = xpp.getAttributeValue(null,
                SettingsExporter.FILE_FORMAT_ATTRIBUTE);
        validateFileFormatVersion(fileFormatVersionString);

        String contentVersionString = xpp.getAttributeValue(null,
                SettingsExporter.VERSION_ATTRIBUTE);
        result.contentVersion = validateContentVersion(contentVersionString);

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG &&
                SettingsExporter.ROOT_ELEMENT.equals(xpp.getName()))) {

            if(eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (SettingsExporter.GLOBAL_ELEMENT.equals(element)) {
                    if (overview || globalSettings) {
                        if (result.globalSettings == null) {
                            if (overview) {
                                result.globalSettings = new SettingsImporter.ImportedSettings();
                                skipToEndTag(xpp, SettingsExporter.GLOBAL_ELEMENT);
                            } else {
                                result.globalSettings = parseSettings(xpp, SettingsExporter.GLOBAL_ELEMENT);
                            }
                        } else {
                            skipToEndTag(xpp, SettingsExporter.GLOBAL_ELEMENT);
                            Log.w(K9.LOG_TAG, "More than one global settings element. Only using the first one!");
                        }
                    } else {
                        skipToEndTag(xpp, SettingsExporter.GLOBAL_ELEMENT);
                        Log.i(K9.LOG_TAG, "Skipping global settings");
                    }
                } else if (SettingsExporter.ACCOUNTS_ELEMENT.equals(element)) {
                    if (result.accounts == null) {
                        result.accounts = parseAccounts(xpp, accountUuids, overview);
                    } else {
                        Log.w(K9.LOG_TAG, "More than one accounts element. Only using the first one!");
                    }
                } else {
                    Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                }
            }
            eventType = xpp.next();
        }

        return result;
    }

    private static int validateFileFormatVersion(String versionString)
            throws SettingsImportExportException {

        if (versionString == null) {
            throw new SettingsImportExportException("Missing file format version");
        }

        int version;
        try {
            version = Integer.parseInt(versionString);
        } catch (NumberFormatException e) {
            throw new SettingsImportExportException("Invalid file format version: " +
                    versionString);
        }

        if (version != SettingsExporter.FILE_FORMAT_VERSION) {
            throw new SettingsImportExportException("Unsupported file format version: " +
                    versionString);
        }

        return version;
    }

    private static int validateContentVersion(String versionString)
            throws SettingsImportExportException {

        if (versionString == null) {
            throw new SettingsImportExportException("Missing content version");
        }

        int version;
        try {
            version = Integer.parseInt(versionString);
        } catch (NumberFormatException e) {
            throw new SettingsImportExportException("Invalid content version: " +
                    versionString);
        }

        if (version < 1) {
            throw new SettingsImportExportException("Unsupported content version: " + versionString);
        }

        return version;
    }

    private static Map<String, SettingsImporter.ImportedAccount> parseAccounts(
            XmlPullParser xpp, List<String> accountUuids, boolean overview)
        throws XmlPullParserException, IOException {

        Map<String, SettingsImporter.ImportedAccount> accounts = null;

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG &&
                SettingsExporter.ACCOUNTS_ELEMENT.equals(xpp.getName()))) {

            if(eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (SettingsExporter.ACCOUNT_ELEMENT.equals(element)) {
                    if (accounts == null) {
                        accounts = new HashMap<String, SettingsImporter.ImportedAccount>();
                    }

                    SettingsImporter.ImportedAccount account = parseAccount(xpp, accountUuids, overview);

                    if (account == null) {
                        // Do nothing - parseAccount() already logged a message
                    } else if (!accounts.containsKey(account.uuid)) {
                        accounts.put(account.uuid, account);
                    } else {
                        Log.w(K9.LOG_TAG, "Duplicate account entries with UUID " + account.uuid +
                                ". Ignoring!");
                    }
                } else {
                    Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                }
            }
            eventType = xpp.next();
        }

        return accounts;
    }

    static SettingsImporter.ImportedSettings parseSettings(XmlPullParser xpp, String endTag)
            throws XmlPullParserException, IOException {

        SettingsImporter.ImportedSettings result = null;

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && endTag.equals(xpp.getName()))) {

            if(eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (SettingsExporter.VALUE_ELEMENT.equals(element)) {
                    String key = xpp.getAttributeValue(null, SettingsExporter.KEY_ATTRIBUTE);
                    String value = getText(xpp);

                    if (result == null) {
                        result = new SettingsImporter.ImportedSettings();
                    }

                    if (result.settings.containsKey(key)) {
                        Log.w(K9.LOG_TAG, "Already read key \"" + key + "\". Ignoring value \"" + value + "\"");
                    } else {
                        result.settings.put(key, value);
                    }
                } else {
                    Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                }
            }
            eventType = xpp.next();
        }

        return result;
    }

    static SettingsImporter.ImportedAccount parseAccount(XmlPullParser xpp, List<String> accountUuids,
                                                         boolean overview)
            throws XmlPullParserException, IOException {

        String uuid = xpp.getAttributeValue(null, SettingsExporter.UUID_ATTRIBUTE);

        try {
            UUID.fromString(uuid);
        } catch (Exception e) {
            skipToEndTag(xpp, SettingsExporter.ACCOUNT_ELEMENT);
            Log.w(K9.LOG_TAG, "Skipping account with invalid UUID " + uuid);
            return null;
        }

        SettingsImporter.ImportedAccount account = new SettingsImporter.ImportedAccount();
        account.uuid = uuid;

        if (overview || accountUuids.contains(uuid)) {
            int eventType = xpp.next();
            while (!(eventType == XmlPullParser.END_TAG &&
                    SettingsExporter.ACCOUNT_ELEMENT.equals(xpp.getName()))) {

                if(eventType == XmlPullParser.START_TAG) {
                    String element = xpp.getName();
                    if (SettingsExporter.NAME_ELEMENT.equals(element)) {
                        account.name = getText(xpp);
                    } else if (SettingsExporter.INCOMING_SERVER_ELEMENT.equals(element)) {
                        if (overview) {
                            skipToEndTag(xpp, SettingsExporter.INCOMING_SERVER_ELEMENT);
                        } else {
                            account.incoming = parseServerSettings(xpp, SettingsExporter.INCOMING_SERVER_ELEMENT);
                        }
                    } else if (SettingsExporter.OUTGOING_SERVER_ELEMENT.equals(element)) {
                        if (overview) {
                            skipToEndTag(xpp, SettingsExporter.OUTGOING_SERVER_ELEMENT);
                        } else {
                            account.outgoing = parseServerSettings(xpp, SettingsExporter.OUTGOING_SERVER_ELEMENT);
                        }
                    } else if (SettingsExporter.SETTINGS_ELEMENT.equals(element)) {
                        if (overview) {
                            skipToEndTag(xpp, SettingsExporter.SETTINGS_ELEMENT);
                        } else {
                            account.settings = parseSettings(xpp, SettingsExporter.SETTINGS_ELEMENT);
                        }
                    } else if (SettingsExporter.IDENTITIES_ELEMENT.equals(element)) {
                        if (overview) {
                            skipToEndTag(xpp, SettingsExporter.IDENTITIES_ELEMENT);
                        } else {
                            account.identities = parseIdentities(xpp);
                        }
                    } else if (SettingsExporter.FOLDERS_ELEMENT.equals(element)) {
                        if (overview) {
                            skipToEndTag(xpp, SettingsExporter.FOLDERS_ELEMENT);
                        } else {
                            account.folders = parseFolders(xpp);
                        }
                    } else {
                        Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                    }
                }
                eventType = xpp.next();
            }
        } else {
            skipToEndTag(xpp, SettingsExporter.ACCOUNT_ELEMENT);
            Log.i(K9.LOG_TAG, "Skipping account with UUID " + uuid);
        }

        // If we couldn't find an account name use the UUID
        if (account.name == null) {
            account.name = uuid;
        }

        return account;
    }

    private static SettingsImporter.ImportedServer parseServerSettings(XmlPullParser xpp, String endTag)
            throws XmlPullParserException, IOException {
        SettingsImporter.ImportedServer server = new SettingsImporter.ImportedServer();

        server.type = xpp.getAttributeValue(null, SettingsExporter.TYPE_ATTRIBUTE);

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && endTag.equals(xpp.getName()))) {
            if(eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (SettingsExporter.HOST_ELEMENT.equals(element)) {
                    server.host = getText(xpp);
                } else if (SettingsExporter.PORT_ELEMENT.equals(element)) {
                    server.port = getText(xpp);
                } else if (SettingsExporter.CONNECTION_SECURITY_ELEMENT.equals(element)) {
                    server.connectionSecurity = getText(xpp);
                } else if (SettingsExporter.AUTHENTICATION_TYPE_ELEMENT.equals(element)) {
                    String text = getText(xpp);
                    server.authenticationType = AuthType.valueOf(text);
                } else if (SettingsExporter.USERNAME_ELEMENT.equals(element)) {
                    server.username = getText(xpp);
                } else if (SettingsExporter.CLIENT_CERTIFICATE_ALIAS_ELEMENT.equals(element)) {
                    server.clientCertificateAlias = getText(xpp);
                } else if (SettingsExporter.PASSWORD_ELEMENT.equals(element)) {
                    server.password = getText(xpp);
                } else if (SettingsExporter.EXTRA_ELEMENT.equals(element)) {
                    server.extras = parseSettings(xpp, SettingsExporter.EXTRA_ELEMENT);
                } else {
                    Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                }
            }
            eventType = xpp.next();
        }

        return server;
    }

    private static List<SettingsImporter.ImportedIdentity> parseIdentities(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        List<SettingsImporter.ImportedIdentity> identities = null;

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG &&
                SettingsExporter.IDENTITIES_ELEMENT.equals(xpp.getName()))) {

            if(eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (SettingsExporter.IDENTITY_ELEMENT.equals(element)) {
                    if (identities == null) {
                        identities = new ArrayList<SettingsImporter.ImportedIdentity>();
                    }

                    SettingsImporter.ImportedIdentity identity = parseIdentity(xpp);
                    identities.add(identity);
                } else {
                    Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                }
            }
            eventType = xpp.next();
        }

        return identities;
    }

    private static SettingsImporter.ImportedIdentity parseIdentity(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        SettingsImporter.ImportedIdentity identity = new SettingsImporter.ImportedIdentity();

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG &&
                SettingsExporter.IDENTITY_ELEMENT.equals(xpp.getName()))) {

            if(eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (SettingsExporter.NAME_ELEMENT.equals(element)) {
                    identity.name = getText(xpp);
                } else if (SettingsExporter.EMAIL_ELEMENT.equals(element)) {
                    identity.email = getText(xpp);
                } else if (SettingsExporter.DESCRIPTION_ELEMENT.equals(element)) {
                    identity.description = getText(xpp);
                } else if (SettingsExporter.SETTINGS_ELEMENT.equals(element)) {
                    identity.settings = parseSettings(xpp, SettingsExporter.SETTINGS_ELEMENT);
                } else {
                    Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                }
            }
            eventType = xpp.next();
        }

        return identity;
    }

    private static List<SettingsImporter.ImportedFolder> parseFolders(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        List<SettingsImporter.ImportedFolder> folders = null;

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG &&
                SettingsExporter.FOLDERS_ELEMENT.equals(xpp.getName()))) {

            if(eventType == XmlPullParser.START_TAG) {
                String element = xpp.getName();
                if (SettingsExporter.FOLDER_ELEMENT.equals(element)) {
                    if (folders == null) {
                        folders = new ArrayList<SettingsImporter.ImportedFolder>();
                    }

                    SettingsImporter.ImportedFolder folder = parseFolder(xpp);
                    folders.add(folder);
                } else {
                    Log.w(K9.LOG_TAG, "Unexpected start tag: " + xpp.getName());
                }
            }
            eventType = xpp.next();
        }

        return folders;
    }

    private static SettingsImporter.ImportedFolder parseFolder(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        SettingsImporter.ImportedFolder folder = new SettingsImporter.ImportedFolder();

        String name = xpp.getAttributeValue(null, SettingsExporter.NAME_ATTRIBUTE);
        folder.name = name;

        folder.settings = parseSettings(xpp, SettingsExporter.FOLDER_ELEMENT);

        return folder;
    }

    private static String getText(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        int eventType = xpp.next();
        if (eventType != XmlPullParser.TEXT) {
            return "";
        }
        return xpp.getText();
    }

    private static void skipToEndTag(XmlPullParser xpp, String endTag)
            throws XmlPullParserException, IOException {

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && endTag.equals(xpp.getName()))) {
            eventType = xpp.next();
        }
    }
}
