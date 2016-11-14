/*
 * Copyright (C) 2014-2015 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openintents.smime.util;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.openintents.smime.ISMimeService;
import org.openintents.smime.SMimeError;


@SuppressWarnings("unused")
public class SMimeApi {

    public static final String TAG = "SMime API";

    public static final String SERVICE_INTENT = "org.openintents.smime.ISMimeService";

    /**
     * see CHANGELOG.md
     */
    public static final int API_VERSION = 10;

    /**
     * General extras
     * --------------
     *
     * required extras:
     * int           EXTRA_API_VERSION           (always required)
     *
     * returned extras:
     * int           RESULT_CODE                 (RESULT_CODE_ERROR, RESULT_CODE_SUCCESS or RESULT_CODE_USER_INTERACTION_REQUIRED)
     * SMimeError  RESULT_ERROR                (if RESULT_CODE == RESULT_CODE_ERROR)
     * PendingIntent RESULT_INTENT               (if RESULT_CODE == RESULT_CODE_USER_INTERACTION_REQUIRED)
     */

    public static final String ACTION_CHECK_PERMISSION = "org.openintents.smime.action.CHECK_PERMISSION";

    /**
     * DEPRECATED
     * Same as ACTION_CLEARTEXT_SIGN
     * <p/>
     * optional extras:
     * boolean       EXTRA_REQUEST_ASCII_ARMOR   (DEPRECATED: this makes no sense here)
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     */
    public static final String ACTION_SIGN = "org.openintents.smime.action.SIGN";

    /**
     * Sign text resulting in a cleartext signature
     * Some magic pre-processing of the text is done to convert it to a format usable for
     * cleartext signatures per RFC 4880 before the text is actually signed:
     * - end cleartext with newline
     * - remove whitespaces on line endings
     * <p/>
     * required extras:
     * long          EXTRA_SIGN_KEY_ID           (key id of signing key)
     * <p/>
     * optional extras:
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     */
    public static final String ACTION_CLEARTEXT_SIGN = "org.openintents.smime.action.CLEARTEXT_SIGN";

    /**
     * Sign text or binary data resulting in a detached signature.
     * No OutputStream necessary for ACTION_DETACHED_SIGN (No magic pre-processing like in ACTION_CLEARTEXT_SIGN)!
     * The detached signature is returned separately in RESULT_DETACHED_SIGNATURE.
     * <p/>
     * required extras:
     * long          EXTRA_SIGN_KEY_ID           (key id of signing key)
     * <p/>
     * optional extras:
     * boolean       EXTRA_REQUEST_ASCII_ARMOR   (request ascii armor for detached signature)
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     * <p/>
     * returned extras:
     * byte[]        RESULT_DETACHED_SIGNATURE
     */
    public static final String ACTION_DETACHED_SIGN = "org.openintents.smime.action.DETACHED_SIGN";

    /**
     * Encrypt
     * <p/>
     * required extras:
     * String[]      EXTRA_USER_IDS              (=emails of recipients, if more than one key has a user_id, a PendingIntent is returned via RESULT_INTENT)
     * or
     * long[]        EXTRA_KEY_IDS
     * <p/>
     * optional extras:
     * boolean       EXTRA_REQUEST_ASCII_ARMOR   (request ascii armor for output)
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     * String        EXTRA_ORIGINAL_FILENAME     (original filename to be encrypted as metadata)
     * boolean       EXTRA_ENABLE_COMPRESSION    (enable ZLIB compression, default ist true)
     */
    public static final String ACTION_ENCRYPT = "org.openintents.smime.action.ENCRYPT";

    /**
     * Sign and encrypt
     * <p/>
     * required extras:
     * String[]      EXTRA_USER_IDS              (=emails of recipients, if more than one key has a user_id, a PendingIntent is returned via RESULT_INTENT)
     * or
     * long[]        EXTRA_KEY_IDS
     * <p/>
     * optional extras:
     * long          EXTRA_SIGN_KEY_ID           (key id of signing key)
     * boolean       EXTRA_REQUEST_ASCII_ARMOR   (request ascii armor for output)
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     * String        EXTRA_ORIGINAL_FILENAME     (original filename to be encrypted as metadata)
     * boolean       EXTRA_ENABLE_COMPRESSION    (enable ZLIB compression, default ist true)
     */
    public static final String ACTION_SIGN_AND_ENCRYPT = "org.openintents.smime.action.SIGN_AND_ENCRYPT";

    /**
     * Decrypts and verifies given input stream. This methods handles encrypted-only, signed-and-encrypted,
     * and also signed-only input.
     * OutputStream is optional, e.g., for verifying detached signatures!
     * <p/>
     * If SMimeSignatureResult.getResult() == SMimeSignatureResult.RESULT_KEY_MISSING
     * in addition a PendingIntent is returned via RESULT_INTENT to download missing keys.
     * On all other status, in addition a PendingIntent is returned via RESULT_INTENT to open
     * the key view in OpenKeychain.
     * <p/>
     * optional extras:
     * byte[]        EXTRA_DETACHED_SIGNATURE    (detached signature)
     * <p/>
     * returned extras:
     * SMimeSignatureResult   RESULT_SIGNATURE
     * SMimeDecryptionResult  RESULT_DECRYPTION
     * SMimeDecryptMetadata   RESULT_METADATA
     * String                   RESULT_CHARSET   (charset which was specified in the headers of ascii armored input, if any)
     */
    public static final String ACTION_DECRYPT_VERIFY = "org.openintents.smime.action.DECRYPT_VERIFY";

    /**
     * Decrypts the header of an encrypted file to retrieve metadata such as original filename.
     * <p/>
     * This does not decrypt the actual content of the file.
     * <p/>
     * returned extras:
     * SMimeDecryptMetadata   RESULT_METADATA
     * String                   RESULT_CHARSET   (charset which was specified in the headers of ascii armored input, if any)
     */
    public static final String ACTION_DECRYPT_METADATA = "org.openintents.smime.action.DECRYPT_METADATA";

    /**
     * Select key id for signing
     * <p/>
     * optional extras:
     * String      EXTRA_USER_ID
     * <p/>
     * returned extras:
     * long        EXTRA_SIGN_KEY_ID
     */
    public static final String ACTION_GET_SIGN_KEY_ID = "org.openintents.smime.action.GET_SIGN_KEY_ID";

    /**
     * Get key ids based on given user ids (=emails)
     * <p/>
     * required extras:
     * String[]      EXTRA_USER_IDS
     * <p/>
     * returned extras:
     * long[]        RESULT_KEY_IDS
     */
    public static final String ACTION_GET_KEY_IDS = "org.openintents.smime.action.GET_KEY_IDS";

    /**
     * This action returns RESULT_CODE_SUCCESS if the OpenPGP Provider already has the key
     * corresponding to the given key id in its database.
     * <p/>
     * It returns RESULT_CODE_USER_INTERACTION_REQUIRED if the Provider does not have the key.
     * The PendingIntent from RESULT_INTENT can be used to retrieve those from a keyserver.
     * <p/>
     * If an Output stream has been defined the whole public key is returned.
     * required extras:
     * long        EXTRA_KEY_ID
     * <p/>
     * optional extras:
     * String      EXTRA_REQUEST_ASCII_ARMOR (request that the returned key is encoded in ASCII Armor)
     *
     */
    public static final String ACTION_GET_KEY = "org.openintents.smime.action.GET_KEY";

    /* Intent extras */
    public static final String EXTRA_API_VERSION = "api_version";

    // DEPRECATED!!!
    public static final String EXTRA_ACCOUNT_NAME = "account_name";

    // ACTION_DETACHED_SIGN, ENCRYPT, SIGN_AND_ENCRYPT, DECRYPT_VERIFY
    // request ASCII Armor for output
    // OpenPGP Radix-64, 33 percent overhead compared to binary, see http://tools.ietf.org/html/rfc4880#page-53)
    public static final String EXTRA_REQUEST_ASCII_ARMOR = "ascii_armor";

    // ACTION_DETACHED_SIGN
    public static final String RESULT_DETACHED_SIGNATURE = "detached_signature";
    public static final String RESULT_SIGNATURE_MICALG = "signature_micalg";

    // ENCRYPT, SIGN_AND_ENCRYPT
    public static final String EXTRA_USER_IDS = "user_ids";
    public static final String EXTRA_KEY_IDS = "key_ids";
    public static final String EXTRA_SIGN_KEY_ID = "sign_key_id";
    // optional extras:
    public static final String EXTRA_PASSPHRASE = "passphrase";
    public static final String EXTRA_ORIGINAL_FILENAME = "original_filename";
    public static final String EXTRA_ENABLE_COMPRESSION = "enable_compression";
    public static final String EXTRA_ENCRYPT_OPPORTUNISTIC = "opportunistic";

    // GET_SIGN_KEY_ID
    public static final String EXTRA_USER_ID = "user_id";

    // GET_KEY
    public static final String EXTRA_KEY_ID = "key_id";
    public static final String RESULT_KEY_IDS = "key_ids";

    /* Service Intent returns */
    public static final String RESULT_CODE = "result_code";

    // get actual error object from RESULT_ERROR
    public static final int RESULT_CODE_ERROR = 0;
    // success!
    public static final int RESULT_CODE_SUCCESS = 1;
    // get PendingIntent from RESULT_INTENT, start PendingIntent with startIntentSenderForResult,
    // and execute service method again in onActivityResult
    public static final int RESULT_CODE_USER_INTERACTION_REQUIRED = 2;

    public static final String RESULT_ERROR = "error";
    public static final String RESULT_INTENT = "intent";

    // DECRYPT_VERIFY
    public static final String EXTRA_DECRYPTION_RESULT = "decryption_result";
    public static final String EXTRA_DETACHED_SIGNATURE = "detached_signature";
    public static final String EXTRA_PROGRESS_MESSENGER = "progress_messenger";
    public static final String EXTRA_DATA_LENGTH = "data_length";
    public static final String EXTRA_SENDER_ADDRESS = "sender_address";
    public static final String RESULT_SIGNATURE = "signature";
    public static final String RESULT_DECRYPTION = "decryption";
    public static final String RESULT_METADATA = "metadata";
    // This will be the charset which was specified in the headers of ascii armored input, if any
    public static final String RESULT_CHARSET = "charset";

    // INTERNAL, should not be used
    public static final String EXTRA_CALL_UUID1 = "call_uuid1";
    public static final String EXTRA_CALL_UUID2 = "call_uuid2";

    ISMimeService mService;
    Context mContext;
    final AtomicInteger mPipeIdGen = new AtomicInteger();

    public SMimeApi(Context context, ISMimeService service) {
        this.mContext = context;
        this.mService = service;
    }

    public interface ISMimeCallback {
        void onReturn(final Intent result);
    }

    public interface ISMimeSinkResultCallback<T> {
        void onProgress(int current, int max);
        void onReturn(final Intent result, T sinkResult);
    }

    public interface CancelableBackgroundOperation {
        void cancelOperation();
    }

    private class SMimeSourceSinkAsyncTask<T> extends AsyncTask<Void, Integer, SMimeDataResult<T>>
            implements CancelableBackgroundOperation {
        Intent data;
        SMimeDataSource dataSource;
        SMimeDataSink<T> dataSink;
        ISMimeSinkResultCallback<T> callback;

        private SMimeSourceSinkAsyncTask(Intent data, SMimeDataSource dataSource,
                SMimeDataSink<T> dataSink, ISMimeSinkResultCallback<T> callback) {
            this.data = data;
            this.dataSource = dataSource;
            this.dataSink = dataSink;
            this.callback = callback;
        }

        @Override
        protected SMimeDataResult<T> doInBackground(Void... unused) {
            return executeApi(data, dataSource, dataSink);
        }

        protected void onPostExecute(SMimeDataResult<T> result) {
            callback.onReturn(result.apiResult, result.sinkResult);
        }

        @Override
        public void cancelOperation() {
            cancel(true);
            if (dataSource != null) {
                dataSource.cancel();
            }
        }
    }

    private class SMimeAsyncTask extends AsyncTask<Void, Integer, Intent> {
        Intent data;
        InputStream is;
        OutputStream os;
        ISMimeCallback callback;

        private SMimeAsyncTask(Intent data, InputStream is, OutputStream os, ISMimeCallback callback) {
            this.data = data;
            this.is = is;
            this.os = os;
            this.callback = callback;
        }

        @Override
        protected Intent doInBackground(Void... unused) {
            return executeApi(data, is, os);
        }

        protected void onPostExecute(Intent result) {
            callback.onReturn(result);
        }
    }

    public <T> CancelableBackgroundOperation executeApiAsync(
            Intent data, SMimeDataSource dataSource,
            SMimeDataSink<T> dataSink, final ISMimeSinkResultCallback<T> callback) {
        Messenger messenger = new Messenger(new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                callback.onProgress(message.arg1, message.arg2);
                return true;
            }
        }));
        data.putExtra(EXTRA_PROGRESS_MESSENGER, messenger);

        SMimeSourceSinkAsyncTask<T> task = new SMimeSourceSinkAsyncTask<>(data, dataSource, dataSink, callback);

        // don't serialize async tasks!
        // http://commonsware.com/blog/2012/04/20/asynctask-threading-regression-confirmed.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        } else {
            task.execute((Void[]) null);
        }

        return task;
    }

    public AsyncTask executeApiAsync(Intent data, SMimeDataSource dataSource, ISMimeSinkResultCallback<Void> callback) {
        SMimeSourceSinkAsyncTask<Void> task = new SMimeSourceSinkAsyncTask<>(data, dataSource, null, callback);

        // don't serialize async tasks!
        // http://commonsware.com/blog/2012/04/20/asynctask-threading-regression-confirmed.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        } else {
            task.execute((Void[]) null);
        }

        return task;
    }

    public void executeApiAsync(Intent data, InputStream is, OutputStream os, ISMimeCallback callback) {
        SMimeAsyncTask task = new SMimeAsyncTask(data, is, os, callback);

        // don't serialize async tasks!
        // http://commonsware.com/blog/2012/04/20/asynctask-threading-regression-confirmed.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        } else {
            task.execute((Void[]) null);
        }
    }

    public static class SMimeDataResult<T> {
        Intent apiResult;
        T sinkResult;

        public SMimeDataResult(Intent apiResult, T sinkResult) {
            this.apiResult = apiResult;
            this.sinkResult = sinkResult;
        }
    }

    public <T> SMimeDataResult<T> executeApi(Intent data, SMimeDataSource dataSource, SMimeDataSink<T> dataSink) {
        ParcelFileDescriptor input = null;
        ParcelFileDescriptor output = null;
        try {
            if (dataSource != null) {
                Long expectedSize = dataSource.getSizeForProgress();
                if (expectedSize != null) {
                    data.putExtra(EXTRA_DATA_LENGTH, (long) expectedSize);
                } else {
                    data.removeExtra(EXTRA_PROGRESS_MESSENGER);
                }
                input = dataSource.startPumpThread();
            }

            ParcelFileDescriptorUtil.DataSinkTransferThread<T> pumpThread = null;
            int outputPipeId = 0;

            if (dataSink != null) {
                outputPipeId = mPipeIdGen.incrementAndGet();
                output = mService.createOutputPipe(outputPipeId);
                pumpThread = ParcelFileDescriptorUtil.asyncPipeToDataSink(dataSink, output);
            }

            Intent result = executeApi(data, input, outputPipeId);

            if (pumpThread == null) {
                return new SMimeDataResult<>(result, null);
            }

            // wait for ALL data being pumped from remote side
            pumpThread.join();
            return new SMimeDataResult<>(result, pumpThread.getResult());
        } catch (Exception e) {
            Log.e(SMimeApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR,
                    new SMimeError(SMimeError.CLIENT_SIDE_ERROR, e.getMessage()));
            return new SMimeDataResult<>(result, null);
        } finally {
            closeLoudly(output);
        }
    }

    public Intent executeApi(Intent data, InputStream is, OutputStream os) {
        ParcelFileDescriptor input = null;
        ParcelFileDescriptor output = null;
        try {
            if (is != null) {
                input = ParcelFileDescriptorUtil.pipeFrom(is);
            }

            Thread pumpThread = null;
            int outputPipeId = 0;

            if (os != null) {
                outputPipeId = mPipeIdGen.incrementAndGet();
                output = mService.createOutputPipe(outputPipeId);
                pumpThread = ParcelFileDescriptorUtil.pipeTo(os, output);
            }

            Intent result = executeApi(data, input, outputPipeId);

            // wait for ALL data being pumped from remote side
            if (pumpThread != null) {
                pumpThread.join();
            }

            return result;
        } catch (Exception e) {
            Log.e(SMimeApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR,
                    new SMimeError(SMimeError.CLIENT_SIDE_ERROR, e.getMessage()));
            return result;
        } finally {
            closeLoudly(output);
        }
    }

    public static abstract class SMimeDataSource {
        private boolean isCancelled;
        private ParcelFileDescriptor writeSidePfd;


        public abstract void writeTo(OutputStream os) throws IOException;

        public Long getSizeForProgress() {
            return null;
        }

        public boolean isCancelled() {
            return isCancelled;
        }

        private ParcelFileDescriptor startPumpThread() throws IOException {
            if (writeSidePfd != null) {
                throw new IllegalStateException("startPumpThread() must only be called once!");
            }
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readSidePfd = pipe[0];
            writeSidePfd = pipe[1];

            new ParcelFileDescriptorUtil.DataSourceTransferThread(this,
                    new ParcelFileDescriptor.AutoCloseOutputStream(writeSidePfd)).start();

            return readSidePfd;
        }

        private void cancel() {
            isCancelled = true;
            try {
                writeSidePfd.close();
            } catch (IOException e) {
                // this is fine
            }
        }
    }

    public interface SMimeDataSink<T> {
        T processData(InputStream is) throws IOException;
    }

    public Intent executeApi(Intent data, SMimeDataSource dataSource, OutputStream os) {
        ParcelFileDescriptor input = null;
        ParcelFileDescriptor output;
        try {
            if (dataSource != null) {
                Long expectedSize = dataSource.getSizeForProgress();
                if (expectedSize != null) {
                    data.putExtra(EXTRA_DATA_LENGTH, (long) expectedSize);
                } else {
                    data.removeExtra(EXTRA_PROGRESS_MESSENGER);
                }
                input = dataSource.startPumpThread();
            }

            Thread pumpThread = null;
            int outputPipeId = 0;

            if (os != null) {
                outputPipeId = mPipeIdGen.incrementAndGet();
                output = mService.createOutputPipe(outputPipeId);
                pumpThread = ParcelFileDescriptorUtil.pipeTo(os, output);
            }

            Intent result = executeApi(data, input, outputPipeId);

            // wait for ALL data being pumped from remote side
            if (pumpThread != null) {
                pumpThread.join();
            }

            return result;
        } catch (Exception e) {
            Log.e(SMimeApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR,
                    new SMimeError(SMimeError.CLIENT_SIDE_ERROR, e.getMessage()));
            return result;
        }
    }

    /**
     * InputStream and OutputStreams are always closed after operating on them!
     */
    private Intent executeApi(Intent data, ParcelFileDescriptor input, int outputPipeId) {
        try {
            // always send version from client
            data.putExtra(EXTRA_API_VERSION, SMimeApi.API_VERSION);

            Intent result;

            // blocks until result is ready
            result = mService.execute(data, input, outputPipeId);

            // set class loader to current context to allow unparcelling
            // of SMimeError and SMimeSignatureResult
            // http://stackoverflow.com/a/3806769
            result.setExtrasClassLoader(mContext.getClassLoader());

            return result;
        } catch (Exception e) {
            Log.e(SMimeApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR,
                    new SMimeError(SMimeError.CLIENT_SIDE_ERROR, e.getMessage()));
            return result;
        } finally {
            // close() is required to halt the TransferThread
            closeLoudly(input);
        }
    }

    private static void closeLoudly(ParcelFileDescriptor input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException e) {
                Log.e(SMimeApi.TAG, "IOException when closing ParcelFileDescriptor!", e);
            }
        }
    }

    public interface PermissionPingCallback {
        void onSMimePermissionCheckResult(Intent result);
    }

    public void checkPermissionPing(final PermissionPingCallback permissionPingCallback) {
        Intent intent = new Intent(SMimeApi.ACTION_CHECK_PERMISSION);
        executeApiAsync(intent, null, null, new ISMimeCallback() {
            @Override
            public void onReturn(Intent result) {
                permissionPingCallback.onSMimePermissionCheckResult(result);
            }
        });
    }
}
