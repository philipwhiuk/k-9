---
title: Projects
layout: default
nav_hide: true
---

{% include documentation-header.html %}

# Projects

Email clients are surprisingly complex applications. There's a wide range of protocols to support, providers and features, authentication algorithms and user interface designs. Android itself is an evolving platform with each new version adding more features:

This page provides some of the projects. See the [Roadmap](roadmap.html) for a more concise, time planned list.

### API Version Update
In August 2018 for new apps and for November 2018 for old apps, the Google Play Store will require that K2 targets the API 26 or higher. Currently we set the API SDK version to 22.

#### Permissions Update

**Rationale:** API v23 introduced runtime permissions. Therefore our first priority is to support runtime permissions.

**Changes** We will check and ask for permission before using storage. We will check for permission for Contacts. In the future we will ask for permission for Contacts as part of the welcome screen.

#### Doze improvements

**Rationale:** API v23 introduced Doze. Therefore we need to ensure the app is still usable when affected fully by Doze.

**Changes** This may involve a foreground service.

#### Multi Window support

**Rationale:** API v24 introduced multi-window support. Therefore we need to ensure the app is usable in multi-window mode.

**Changes** This should be fairly minor although we will want to test the usability of the app when it only occupies part of the screen.

#### Notification Enhancements

**Rationale:** API v24 introduced more notification enhancements. Therefore we need to look and see we can improve notifications for API v24 devices.

**Changes** We already have a complex notifications API however we may need to do some work and testing for the extra features.

#### More Doze improvements

**Rationale:** API v24 expanded Doze. Therefore we need to ensure the app is still usable when affected further by Doze.

**Changes** This will depend on the extent of the changes done for Doze above.

#### Data Saver

**Rationale:** API v24 added Data Save. Therefore we need to ensure the app respects a user's Data Saver preferences.

#### App Shortcuts

**Rationale:** API v25 added App Shortcuts. Therefore we should look at how K2 should use App Shortcuts

**Changes** Additional manifest content and entry points for App Shortcuts.

#### Image Keyboards

**Rationale:** API v25 added Image Keyboards. Therefore we should look at how K2 should support Image Keyboards for email composition

**Changes** We probably only need to schedule future work for this.

#### Round Icon Resources

**Rationale:** API v25 added Round Icons.

**Changes** We should make sure K2 looks good with a round icon.

#### Storage Management Intent

**Rationale:** API v25 added a Storage Manager Intent. Therefore we see if this is useful

**Changes** We should schedule work for K2 to use the intent when saving attachments and other data.

#### Demo User Hint

**Rationale:** API v25 added a Demo User API call. Therefore we see if this is useful.

**Changes** We should schedule work for K2 to use the hint and show more help.

#### Notification improvements

**Rationale:** API v26 improved notifications.

**Changes** We will need to adopt a default channel and determine how to channel notifications based on contacts and accounts and preferences. We will also want to look at Notification dots and messaging style notifications.

#### Adaptive Icon Resources

**Rationale:** API v26 added adaptive icons.

**Changes** We should make sure K2 looks good with an adaptive icon.

##### WebView APIs

**Rationale:** API v26 added WebView API improvements.

**Changes** We should investigate whether we should use the WebView APIs in the message view.

#### Multi Display support

**Rationale:** API v26 introduced multi display support. Therefore we need to ensure the app is usable in multi display mode.

**Changes** This should be fairly minor although we will want to test if possible.

### Material Design Update

**Rationale:** The Android ecosystem has long ago moved away from Holo as it's theme engine. K2 now looks fairly out of step with the OS and other apps. We should adopt the Material design language and begin to retheme the app.

**Changes** This is a huge project which will cross multiple releases. Areas to look at:

* AppCompat support library
* RecyclerView support library
* Swipe actions for messages
* PreferencesFragment usage
* NavigationDrawer for switching accounts/folders

### Google Email Support Improvement
**Rationale:** Google is one the world's biggest email providers and a common email provider for Android users. They have a fairly open standards API and so it should be fairly straightforward to improve our support for their accounts.

#### OAuth Integration

This involves supporting `XOAUTH2` as an IMAP and SMTP authentication method - using an authentication token obtained from Google along with an app key.

#### Labels

This involves support for `X-GM-LABELS` IMAP `LIST` extension to denote a single message being in multiple 'folders'.

#### 'Important'

This involves support for the `\Important` folder.

#### Search

This involves support for the `X-GM-RAW` search argument and allowing advanced search when looking at Gmail accounts.

#### Multi-Folder messages

This involves support for the `X-GM-MSGID` argument - multiple folders for a single message.

#### Threading messages

This involves support for the `X-GM-THRID` argument - fetching messages by thread.

### Improved Account Setup Process

### Rich Text Editor

### S/MIME support

### K2 Contacts Provider
