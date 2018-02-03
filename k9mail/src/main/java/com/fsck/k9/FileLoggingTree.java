package com.fsck.k9;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import timber.log.Timber;


public class FileLoggingTree extends Timber.DebugTree {

    private static final String TAG = FileLoggingTree.class.getSimpleName();
    private final int priority;

    private Context context;

    public FileLoggingTree(Context context, int priority) {
        this.context = context;
        this.priority = priority;
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        if (priority > this.priority) {
            return;
        }
        try {
            String directoryPath = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS) + "/K2MailLogs";
            File directory = createDirectoryIfNeccessary(directoryPath);

            if (!directory.exists()) {
                return;
            }

            File file = createFileIfNecessary(directoryPath);

            if (file.exists()) {
                String logTimeStamp = new SimpleDateFormat("E MMM dd yyyy 'at' hh:mm:ss:SSS aaa",
                        Locale.getDefault()).format(new Date());
                writeToFile(file, logTimeStamp + " :&nbsp&nbsp</strong>&nbsp&nbsp" + message);
            }
        } catch (Exception e) {
            androidLog(e.getMessage());
        }

    }

    private void writeToFile(File file, String message) throws IOException {
        OutputStream fileOutputStream = new FileOutputStream(file, true);
        fileOutputStream.write((
                "<p style=\"background:lightgray;\"><strong style=\"background:lightblue;\">&nbsp&nbsp"
                        + message + "</p>").getBytes());
        fileOutputStream.close();

    }

    private File createFileIfNecessary(String directoryPath) throws IOException {
        String fileNameTimeStamp = new SimpleDateFormat("dd-MM-yyyy",
                Locale.getDefault()).format(new Date());
        String fileName = fileNameTimeStamp + ".html";

        File file = new File(directoryPath + File.separator + fileName);

        if (!file.exists()) {
            boolean createdFile = file.createNewFile();
            if (!createdFile) {
                androidLog("Failed to create log file:" + file.getAbsolutePath());
            }
        }
        return file;
    }

    private File createDirectoryIfNeccessary(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            boolean createdDirectory = directory.mkdir();
            if (!createdDirectory) {
                androidLog("Failed to create log file:" + directory.getAbsolutePath());
            }
        }
        return directory;
    }

    @SuppressLint("LogNotTimber")
    private void androidLog(String errorMessage) {
        Log.e(TAG, "Error while logging into file : " + errorMessage);
    }
}