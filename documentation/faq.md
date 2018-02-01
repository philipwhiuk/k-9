---
title: Frequently Asked Questions
nav_order: 0
layout: default
nav_hide: true
---

{% include documentation-header.html %}

# Frequently Asked Questions (FAQ)

[Where did the name 'K2' come from? What's with that icon?](#anchor0.1)

[How do I prevent K2 Mail from reporting unread messages I already read from my desktop? I'm using POP3.](#anchor1)

[How do I get back to the accounts overview screen?](#anchor2)

[Why can't I move K2 Mail to the SD card?](#anchor3)

[How do I change my Signature?](#anchor3)

[How can I configure the _Archive_, _Drafts_, _Sent_, _Spam_ and _Trash_ folders? ](#anchor4)

[How can I attach `*.xyz` files? ](#anchor5)

[Why aren't my settings saved?](#anchor6)

[Why does "Mark all messages as read" say that there are messages I can't see?](#anchor7)

[K2 is taking ages to fetch messages, help!](#anchor8)

[I'm having some trouble with a lot of concurrent connections to my IMAP server ](#anchor9)

[Why K2 is using so much bandwidth to sync my POP mail?](#anchor10)

[K2 is crashing, please help!](#anchor11)

[Where is the source code?](#anchor12)

[Why are there no release notes/list of changes for version X?](#anchor13)

[What is the difference between version 4.0xx and 4.1xx?](#anchor13b)

[What is the difference between pushing and polling?](#anchor14)

[Why is K2 telling me, "Syncing disabled"?](#anchor15)

[I added a new folder to the account on the server - why don't I see it in K2?](#anchor16)

[How do I add additional Mail Accounts?](#anchor17)

[How do I add Unified Mail Inbox?](#anchor18)

[When will 'X' be developed/released?](#anchor19)

### <a name="anchor0.1"></a>Where did the name 'K2' come from? What's with that icon?

K2 is a successor to the original K-9 Mail app. The icon is of the mountain of the same name.

### <a name="anchor1"></a> How do I prevent K2 Mail from reporting unread messages I already read from my desktop? I'm using POP3.

This is a POP3 limitation: that protocol doesn't report/support READ/UNREAD status.
IMAP addresses this limitation.

### <a name="anchor2"></a> How do I get back to the accounts overview screen?

If you see a list of mails inside of a folder and you want to get back to the overview screen, you have to

* Press the menu button
* Select "More"
* Select "Accounts"

### <a name="anchor3"></a> Why can't I move K2 Mail to the SD card?

Google doesn't let programs installed on SD run as services, which we need to do in order to be able to fetch your mail.

### <a name="anchor3"></a> How do I change my Signature?

Do the following to replace default signature in K2 with your personal signature for any email account or stop K2 default signature for that particular account:

Open K2, select email account in which you wish to create a signature.
Key Sequence: Menu/Settings/Account Settings/Sending Mail/Composition Defaults

In this dialog, you can enter Your Name, Your Email Address, Bcc all messages to, CHECK (or uncheck) USE SIGNATURE, enter your signature using correct syntax of top line of signature with -- followed by a line break, and enter your desired signature. Choose radio button to place Signature position before or after quoted text.

### <a name="anchor4"></a> How can I configure the _Archive_, _Drafts_, _Sent_, _Spam_ and _Trash_ folders?

"Account settings" (via long click or Menu) > "Folders" ("Incoming server" in pre-3.400 versions)

### <a name="anchor5"></a> How can I attach `*.xyz` files?

K2 Mail uses the standard Android way to inherit external app features (`Intents`); you need a file manager application in order to choose any file.

Compatible file managers:

* [OI File Manager](http://www.openintents.org/en/filemanager) ([open source](http://code.google.com/p/openintents/))

### <a name="anchor6"></a> Why aren't my settings saved?

Please use the `Back` key to leave all settings screens in order to trigger settings save.

### <a name="anchor7"></a> Why does "Mark all messages as read" say that there are messages I can't see?

K2 lets you choose to only sync the most recent messages to your phone.
This particuar feature operates on all messages, even if you've chosen
to sync only the last 10 messages to your phone.

### <a name="anchor8"></a> K2 is taking ages to fetch messages, help!

Some device have very [inefficient storage](Storage). Try switching storage (3.400+ only).

### <a name="anchor9"></a> I'm having some trouble with a lot of concurrent connections to my IMAP server

You've turned on "push" mail and marked a lot of folders for push. IMAP push requires one connection per folder.

### <a name="anchor10"></a> Why K2 is using so much bandwidth to sync my POP mail?

K2 normally doesn't take much bandwidth. It is comparable to other Android mail applications such as GMail. However, there may be some situations where K2 can consume up to 150k of bandwidth everytime an account is refreshed.

This normally happen when you have a POP account that has a huge inbox folder. If you have a inbox of about 2500 messages in it, it would generate data exchange between K2 and the POP server in the order of 120k per refresh query.

### <a name="anchor11"></a> K2 is crashing, please help!

If you can't find a similar problem on our [issue tracker](https://github.com/philipwhiuk/k2/issues), please file a new issue and provide a [debug log](/documentation/debugging.html) and detailed steps on how to reproduce the problem if possible.

### <a name="anchor12"></a> Where is the source code?

For the application see: [the K2 source code](https://github.com/philipwhiuk/k2).

For the website see: [the K2 website source code](https://github.com/philipwhiuk/k2/tree/gh-pages).

### <a name="anchor13b"></a> What is the difference between version 4.0xx and 4.1xx?

Prior to version 6, the version number was as follows:

In short:  
4.0xx = stable / Google Play (Market)  
4.1xx = unstable / development  
4.2xx = the next stable  
4.3xx = the next development

For version 6 and later the version is as follows:

x.y.z:

x - major version change - breaking compatibility (e.g. change in minimum Android version)
y - minor version change - new features, but no breaking change
z - patch version change - bugfix, no new features

Every version of K2 is available to be downloaded [here](https://github.com/philipwhiuk/k2/releases).

Generally we don't release updates for older versions (e.g. if we release 6.1.1 then 6.2.0 we would not expect to release 6.1.2). For major versions this is open to change subject to the availability of a maintainer for the previous major version.

### <a name="anchor14"></a> What is the difference between pushing and polling?

Depending on what type of account you have configured, K2 may support either push or poll for fetching new mail (see Account settings > Fetching mail). At this time, K2 only supports push for IMAP accounts.

Polling refers to the mail client periodically asking the mail server for new mail, on some configured time increment.

Pushing refers to the mail client keeping an open connection to the mail server, so that the server can inform the client of new mail as soon as it is received.

Pushing typically uses more battery than periodic polling. First, when you have any account configured to push, this keeps an Android service running at all times. This service keeps 1 open connection to the mail server for each folder configured to push (which also uses battery power). And lastly, pushing will typically lead to more frequent communications between the client and the server.

With polling however there is a delay between an email arriving and it being received (e.g. if it arrives just after the last poll it will be delayed for nearly an entire poll interval). At higher poll rates, the overhead of polling and not receiving any messages, plus the near constant connection being open makes it less battery efficient.

### <a name="anchor15"></a> Why is K2 telling me, "Syncing disabled"?

It will say this if there is no network, or if there are no folders set up to pull or push (inbox by default will), or if pull and push are both disabled, or if syncing is disabled for all K2 accounts.

Most of these settings are under Settings > Account settings > Fetching mail. See Folder poll frequency, Poll folders, and Push folders. To change a folder's class, long-press on the folder and select Folder settings.

The global K2 setting is under Settings > Global settings > Network > Background sync.

If you are using Android version 6 or later, you are also likely to suffer from problems related to the "Doze" battery saving feature. For more information and potential workarounds see ticket [857](https://github.com/k9mail/k-9/issues/857) in the K-9 GitHub repository.

### <a name="anchor16"></a> I added a new folder to the account on the server - why don't I see it in K2?

K2 currently does not automatically update the list of folders. To refresh, while viewing the current folders, select Menu > Folders > Refresh Folders. This will also delete folders from K2 that have been removed from the server.

### <a name="anchor17"></a> How do I add additional Mail Accounts?

When you start K2 it goes to the inbox (or Unified Inbox if that is setup)

1) At the top to left of the word Inbox there is the app icon and to Left of that is a small < (less than symbol once)

2) Tap once brings you to folders

3) Tap it a second time brings you to Accounts

4) Now from that screen you can tap the menu button (left of home button on G3). That will pop a menu where the first item is "Add Account"

Note: If you are using Unified inbox, you only need to tap the < (less than symbol)

### <a name="anchor18"></a> How do I add Unified Mail Inbox?

1) Start K2

2) Tap Menu > Settings > Global Settings >Interaction

- check the "start in Unified Inbox option
-  Tap return to set


### <a name="anchor19"></a>When will 'X' be developed/released?

K2 has no fixed release schedule. Nor does it have any full-time developers at time of writing. Fixes to bugs and enhancements are done by interested developers according to their own priorities.

In terms of releases we try to ensure adequate testing and beta time for individual features,
we have an emphasis on providing a rapid deployment phase - with new features moving into beta on a regular basis after being pushed to the repository.

Backporting bug fixes from the current unstable release to the current stable is generally reserved for bugs causing security vulnerabilities or data loss

See the <a href="{{site.url}}/roadmap.html">Roadmap</a> for a rough plan of features.
