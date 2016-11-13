# SMIME API library

The SMIME API provides methods to execute SMIME operations, such as sign, encrypt, decrypt, verify, 
and more without user interaction from background threads. This is done by connecting your client 
application to a remote service provided by [OpenSMIME](https://www.github.com/philipwhiuk/open-smime) 
or other SMIME providers.

### News

#### Version 10
  * Initial release

[Full changelog here…](https://github.com/philipwhiuk/smime-api/blob/master/CHANGELOG.md)

### License
While OpenSMIME itself is GPLv3+, the API library is licensed under Apache License v2.
Thus, you are allowed to also use it in closed source applications as long as you respect the 
[Apache License v2](https://github.com/open-smime/smime-api/blob/master/LICENSE).

### Add the API library to your project

Add this to your build.gradle:

```gradle
repositories {
    jcenter()
}

dependencies {
    compile 'com.whiuk.philip:smime-api:1.0'
}
```

### Full example
A full working example is available in the 
[example project](https://github.com/open-smime/smime-api/blob/master/example). 
The [``SMimeApiActivity.java``](https://github.com/philipwhiuk/smime-api/blob/master/example/src/main/java/org/openintents/smime/example/SMimeApiActivity.java) 
contains most relevant sourcecode.

### API

[SMimeApi](https://github.com/philipwhiuk/smime-api/blob/master/smime-api/src/main/java/org/openintents/smime/util/OpenPgpApi.java) contains all possible Intents and available extras.

### Short tutorial

**This tutorial only covers the basics, please consult the full example for a complete overview over all methods**

The API is **not** designed around ``Intents`` which are started via ``startActivityForResult``. These Intent actions typically start an activity for user interaction, so they are not suitable for background tasks. Most API design decisions are explained at [the bottom of this wiki page](https://github.com/philipwhiuk/open-smime/wiki/SMIME-API#internal-design-decisions).

We will go through the basic steps to understand how this API works, following this (greatly simplified) sequence diagram:
![](https://github.com/philipwhiuk/open-smime/raw/master/Resources/docs/smime_api_1.jpg)

In this diagram the client app is depicted on the left side, the SMIME provider (in this case OpenKeychain) is depicted on the right.
The remote service is defined via the [AIDL](http://developer.android.com/guide/components/aidl.html) file [``ISMimeService``](https://github.com/philipwhiuk/smime-api/blob/master/smime-api/src/main/aidl/org/openintents/smime/ISMimeService.aidl).
It contains only one exposed method which can be invoked remotely:
```java
interface ISMimeService {
    Intent execute(in Intent data, in ParcelFileDescriptor input, in ParcelFileDescriptor output);
}
```
The interaction between the apps is done by binding from your client app to the remote service of OpenKeychain.
``SMimeServiceConnection`` is a helper class from the library to ease this step:
```java
SMimeServiceConnection mServiceConnection;

public void onCreate(Bundle savedInstance) {
    [...]
    mServiceConnection = new SMimeServiceConnection(this, "org.sufficientlysecure.keychain");
    mServiceConnection.bindToService();
}

public void onDestroy() {
    [...]
    if (mServiceConnection != null) {
        mServiceConnection.unbindFromService();
    }
}
```

Following the sequence diagram, these steps are executed:

1.  Define an ``Intent`` containing the actual SMIME instructions which should be done, e.g.
    ```java
Intent data = new Intent();
data.setAction(SMimeApi.ACTION_ENCRYPT);
data.putExtra(SMimeApi.EXTRA_USER_IDS, new String[]{"dominik@dominikschuermann.de"});
data.putExtra(SMimeApi.EXTRA_REQUEST_ASCII_ARMOR, true);
    ```
    Define an ``InputStream`` currently holding the plaintext, and an ``OutputStream`` where you want the ciphertext to be written by OpenKeychain's remote service:
    ```java
InputStream is = new ByteArrayInputStream("Hello world!".getBytes("UTF-8"));
ByteArrayOutputStream os = new ByteArrayOutputStream();
    ```
    Using a helper class from the library, ``is`` and ``os`` are passed via ``ParcelFileDescriptors`` as ``input`` and ``output`` together with ``Intent data``, as depicted in the sequence diagram, from the client to the remote service.
    Programmatically, this can be done with:
    ```java
SMimeApi api = new SMimeApi(this, mServiceConnection.getService());
Intent result = api.executeApi(data, is, os);
    ```

2.  The SMIME operation is executed by OpenKeychain and the produced ciphertext is written into ``os`` which can then be accessed by the client app.

3.  A result Intent is returned containing one of these result codes:
    * ``SMimeApi.RESULT_CODE_ERROR``
    * ``SMimeApi.RESULT_CODE_SUCCESS``
    * ``SMimeApi.RESULT_CODE_USER_INTERACTION_REQUIRED``

    If ``RESULT_CODE_USER_INTERACTION_REQUIRED`` is returned, an additional ``PendingIntent`` is returned to the client, which must be used to get user input required to process the request.
    A ``PendingIntent`` is executed with ``startIntentSenderForResult``, which starts an activity, originally belonging to OpenKeychain, on the [task stack](http://developer.android.com/guide/components/tasks-and-back-stack.html) of the client.
    Only if ``RESULT_CODE_SUCCESS`` is returned, ``os`` actually contains data.
    A nearly complete example looks like this:
    ```java
    switch (result.getIntExtra(SMimeApi.RESULT_CODE, SMimeApi.RESULT_CODE_ERROR)) {
        case SMimeApi.RESULT_CODE_SUCCESS: {
            try {
                Log.d(SMimeApi.TAG, "output: " + os.toString("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                Log.e(Constants.TAG, "UnsupportedEncodingException", e);
            }

            if (result.hasExtra(SMimeApi.RESULT_SIGNATURE)) {
                SMimeSignatureResult sigResult
                        = result.getParcelableExtra(SMimeApi.RESULT_SIGNATURE);
                [...]
            }
            break;
        }
        case SMimeApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
            PendingIntent pi = result.getParcelableExtra(SMimeApi.RESULT_INTENT);
            try {
                startIntentSenderForResult(pi.getIntentSender(), 42, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.e(Constants.TAG, "SendIntentException", e);
            }
            break;
        }
        case SMimeApi.RESULT_CODE_ERROR: {
            SMimeError error = result.getParcelableExtra(SMimeApi.RESULT_ERROR);
            [...]
            break;
        }
    }
    ```

4.  Results from a ``PendingIntent`` are returned in ``onActivityResult`` of the activity, which executed ``startIntentSenderForResult``.
    The returned ``Intent data`` in ``onActivityResult`` contains the original SMIME operation definition and new values acquired from the user interaction.
    Thus, you can now execute the ``Intent`` again, like done in step 1.
    This time it should return with ``RESULT_CODE_SUCCESS`` because all required information has been obtained by the previous user interaction stored in this ``Intent``.
    ```java
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        [...]
        // try again after user interaction
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 42: {
                    encrypt(data); // defined like in step 1
                    break;
                }
            }
        }
    }
    ```


### Tipps
*   ``api.executeApi(data, is, os);`` is a blocking call. If you want a convenient asynchronous call, use ``api.executeApiAsync(data, is, os, new MyCallback([... ]));``, where ``MyCallback`` is an private class implementing ``SMimeApi.ISMimeCallback``.
    See [``SMimeApiActivity.java``](https://github.com/philipwhiuk/smime-api/blob/master/example/src/main/java/org/openintents/smime/example/SMimeApiActivity.java) for an example.
*   Using

    ```java
    mServiceConnection = new SMimeServiceConnection(this, "com.whiuk.philip.opensmime");
    ```
    connects to OpenKeychain directly.
    If you want to let the user choose between SMIME providers, you can implement the [``SMimeAppPreference.java``](https://github.com/philipwhiuk/smime-api/tree/master/smime-api/src/main/java/org/openintents/smime/util/SMimeAppPreference.java) like done in the example app.

*    To enable installing a debug and release version at the same time, the `debug` build of OpenKeychain uses `org.sufficientlysecure.keychain.debug` as a package name. Make sure you connect to the right one during development!
