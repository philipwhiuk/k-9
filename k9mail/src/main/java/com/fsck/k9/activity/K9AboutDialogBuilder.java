package com.fsck.k9.activity;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.webkit.WebView;

import com.fsck.k9.R;

import java.util.Calendar;

import de.cketti.library.changelog.ChangeLog;

public class K9AboutDialogBuilder {

    static String[][] USED_LIBRARIES = new String[][] {
            new String[] {"jutf7", "http://jutf7.sourceforge.net/"},
            new String[] {"JZlib", "http://www.jcraft.com/jzlib/"},
            new String[] {"Commons IO", "http://commons.apache.org/io/"},
            new String[] {"Mime4j", "http://james.apache.org/mime4j/"},
            new String[] {"HtmlCleaner", "http://htmlcleaner.sourceforge.net/"},
            new String[] {"Android-PullToRefresh", "https://github.com/chrisbanes/Android-PullToRefresh"},
            new String[] {"ckChangeLog", "https://github.com/cketti/ckChangeLog"},
            new String[] {"HoloColorPicker", "https://github.com/LarsWerkman/HoloColorPicker"},
            new String[] {"Glide", "https://github.com/bumptech/glide"},
            new String[] {"TokenAutoComplete", "https://github.com/splitwise/TokenAutoComplete/"},
    };

    public static AlertDialog.Builder create(final Accounts accounts) {

        String appName = accounts.getString(R.string.app_name);
        String webpageUrlLink = "<a href=\"" + accounts.getString(R.string.app_webpage_url) + "\">"+appName+"</a>";
        int year = Calendar.getInstance().get(Calendar.YEAR);
        WebView wv = new WebView(accounts);
        StringBuilder html = new StringBuilder()
                .append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\" />")
                .append("<img src=\"file:///android_asset/icon.png\" alt=\"").append(appName).append("\"/>")
                .append("<h1>")
                .append(String.format(accounts.getString(R.string.about_title_fmt), webpageUrlLink))
                .append("</h1><p>")
                .append(appName)
                .append(" ")
                .append(String.format(accounts.getString(R.string.debug_version_fmt), getVersionNumber(accounts)))
                .append("</p><p>")
                .append(String.format(accounts.getString(R.string.app_authors_fmt),
                        accounts.getString(R.string.app_authors)))
                .append("</p><p>")
                .append(String.format(accounts.getString(R.string.app_revision_fmt),
                        "<a href=\"" + accounts.getString(R.string.app_revision_url) + "\">" +
                                accounts.getString(R.string.app_revision_url) +
                                "</a>"))
                .append("</p><hr/><p>")
                .append(String.format(accounts.getString(R.string.app_copyright_fmt), year, year))
                .append("</p><hr/><p>")
                .append(accounts.getString(R.string.app_license))
                .append("</p><hr/><p>");

        StringBuilder libs = new StringBuilder().append("<ul>");
        for (String[] library : USED_LIBRARIES) {
            libs.append("<li><a href=\"").append(library[1]).append("\">").append(library[0]).append("</a></li>");
        }
        libs.append("</ul>");

        html.append(String.format(accounts.getString(R.string.app_libraries), libs.toString()))
                .append("</p><hr/><p>")
                .append(String.format(accounts.getString(R.string.app_emoji_icons),
                        "<div>TypePad \u7d75\u6587\u5b57\u30a2\u30a4\u30b3\u30f3\u753b\u50cf " +
                                "(<a href=\"http://typepad.jp/\">Six Apart Ltd</a>) / " +
                                "<a href=\"http://creativecommons.org/licenses/by/2.1/jp/\">CC BY 2.1</a></div>"))
                .append("</p><hr/><p>")
                .append(accounts.getString(R.string.app_htmlcleaner_license));


        wv.loadDataWithBaseURL("file:///android_res/drawable/", html.toString(), "text/html", "utf-8", null);
        return new AlertDialog.Builder(accounts)
                .setView(wv)
                .setCancelable(true)
                .setPositiveButton(R.string.okay_action, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int c) {
                        d.dismiss();
                    }
                })
                .setNeutralButton(R.string.changelog_full_title, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int c) {
                        new ChangeLog(accounts).getFullLogDialog().show();
                    }
                });
    }

    /**
     * Get current version number.
     *
     * @return String version
     */
    static String getVersionNumber(Activity activity) {
        String version = "?";
        try {
            PackageInfo pi = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version = pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            //Log.e(TAG, "Package name not found", e);
        }
        return version;
    }
}
