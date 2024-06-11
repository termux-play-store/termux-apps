package com.termux.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TermuxUrlUtils {

    public static Pattern URL_MATCH_REGEX;

    public static Pattern getUrlMatchRegex() {
        if (URL_MATCH_REGEX != null) return URL_MATCH_REGEX;


        String regex_sb = "(" +             // Begin first matching group.
                "(?:" +                     // Begin scheme group.
                "dav|" +                    // The DAV proto.
                "dict|" +                   // The DICT proto.
                "dns|" +                    // The DNS proto.
                "file|" +                   // File path.
                "finger|" +                 // The Finger proto.
                "ftp(?:s?)|" +              // The FTP proto.
                "git|" +                    // The Git proto.
                "gemini|" +                 // The Gemini proto.
                "gopher|" +                 // The Gopher proto.
                "http(?:s?)|" +             // The HTTP proto.
                "imap(?:s?)|" +             // The IMAP proto.
                "irc(?:[6s]?)|" +           // The IRC proto.
                "ip[fn]s|" +                // The IPFS proto.
                "ldap(?:s?)|" +             // The LDAP proto.
                "pop3(?:s?)|" +             // The POP3 proto.
                "redis(?:s?)|" +            // The Redis proto.
                "rsync|" +                  // The Rsync proto.
                "rtsp(?:[su]?)|" +          // The RTSP proto.
                "sftp|" +                   // The SFTP proto.
                "smb(?:s?)|" +              // The SAMBA proto.
                "smtp(?:s?)|" +             // The SMTP proto.
                "svn(?:(?:\\+ssh)?)|" +     // The Subversion proto.
                "tcp|" +                    // The TCP proto.
                "telnet|" +                 // The Telnet proto.
                "tftp|" +                   // The TFTP proto.
                "udp|" +                    // The UDP proto.
                "vnc|" +                    // The VNC proto.
                "ws(?:s?)" +                // The Websocket proto.
                ")://" +                    // End scheme group.
                ")" +                       // End first matching group.


                // Begin second matching group.
                "(" +

                // User name and/or password in format 'user:pass@'.
                "(?:\\S+(?::\\S*)?@)?" +

                // Begin host group.
                "(?:" +

                // IP address (from http://www.regular-expressions.info/examples.html).
                "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|" +

                // Host name or domain.
                "(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*){1,}[a-z\\u00a1-\\uffff0-9]{1,}))?|" +

                // Just path. Used in case of 'file://' scheme.
                "/(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)" +

                // End host group.
                ")" +

                // Port number.
                "(?::\\d{1,5})?" +

                // Resource path with optional query string.
                "(?:/[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?" +

                // Fragment.
                "(?:#[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?" +

                // End second matching group.
                ")";

        URL_MATCH_REGEX = Pattern.compile(
                regex_sb,
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

        return URL_MATCH_REGEX;
    }

    public static LinkedHashSet<CharSequence> extractUrls(String text) {
        LinkedHashSet<CharSequence> urlSet = new LinkedHashSet<>();
        Matcher matcher = getUrlMatchRegex().matcher(text);

        while (matcher.find()) {
            int matchStart = matcher.start(1);
            int matchEnd = matcher.end();
            String url = text.substring(matchStart, matchEnd);
            urlSet.add(url);
        }

        return urlSet;
    }


    /**
     * Open a url.
     *
     * @param context The context for operations.
     * @param url The url to open.
     */
    public static void openUrl(@NonNull Context context, @NonNull String url) {
        if (url.isEmpty()) return;
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // If no activity found to handle intent, show system chooser
            openSystemAppChooser(context, intent, context.getString(com.termux.R.string.title_open_url_with));
        } catch (Exception e) {
            Log.e(TermuxConstants.LOG_TAG, "Failed to open url \"" + url + "\"", e);
        }
    }

    public static void openSystemAppChooser(@NonNull Context context, final Intent intent, final String title) {
        var chooserIntent = new Intent(Intent.ACTION_CHOOSER)
            .putExtra(Intent.EXTRA_INTENT, intent)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(chooserIntent);
        } catch (Exception e) {
            Log.e(TermuxConstants.LOG_TAG, "Failed to open system chooser for: " + chooserIntent, e);
        }
    }

    /**
     * Share text.
     *
     * @param context The context for operations.
     * @param subject The subject for sharing.
     * @param text The text to share.
     * @param title The title for share menu.
     */
    public static void shareText(@NonNull Context context, String subject, @NonNull String text, @Nullable final String title) {
        var shareTextIntent = new Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text);

        openSystemAppChooser(context, shareTextIntent, (title == null) ? context.getString(com.termux.R.string.title_share_with) : title);
    }



    /** Wrapper for {@link #copyTextToClipboard(Context, String, String, String)} with `null` `clipDataLabel` and `toastString`. */
    public static void copyTextToClipboard(Context context, final String text) {
        copyTextToClipboard(context, null, text, null);
    }

    /** Wrapper for {@link #copyTextToClipboard(Context, String, String, String)} with `null` `clipDataLabel`. */
    public static void copyTextToClipboard(Context context, final String text, final String toastString) {
        copyTextToClipboard(context, null, text, toastString);
    }

    /**
     * Copy the text to primary clip of the clipboard.
     *
     * @param context The context for operations.
     * @param clipDataLabel The label to show to the user describing the copied text.
     * @param text The text to copy.
     * @param toastString If this is not {@code null} or empty, then a toast is shown if copying to
     *                    clipboard is successful.
     */
    public static void copyTextToClipboard(Context context, @Nullable final String clipDataLabel,
                                           final String text, final String toastString) {
        ClipboardManager clipboardManager = context.getSystemService(ClipboardManager.class);
        clipboardManager.setPrimaryClip(ClipData.newPlainText(clipDataLabel, text));
        if (toastString != null && !toastString.isEmpty()) {
            TermuxMessageDialogUtils.showToast(context, toastString);
        }
    }

    /**
     * Wrapper for {@link #getTextFromClipboard(Context, boolean)} that returns primary text {@link String}
     * if its set and not empty.
     */
    @Nullable
    public static String getTextStringFromClipboardIfSet(Context context, boolean coerceToText) {
        CharSequence textCharSequence = getTextFromClipboard(context, coerceToText);
        if (textCharSequence == null) return null;
        String textString = textCharSequence.toString();
        return !textString.isEmpty() ? textString : null;
    }

    /**
     * Get the text from primary clip of the clipboard.
     *
     * @param context The context for operations.
     * @param coerceToText Whether to call {@link ClipData.Item#coerceToText(Context)} to coerce
     *                     non-text data to text.
     * @return Returns the {@link CharSequence} of primary text. This will be `null` if failed to get it.
     */
    @Nullable
    public static CharSequence getTextFromClipboard(Context context, boolean coerceToText) {
        if (context == null) return null;

        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) return null;

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null) return null;

        ClipData.Item clipItem = clipData.getItemAt(0);
        if (clipItem == null) return null;

        return coerceToText ? clipItem.coerceToText(context) : clipItem.getText();
    }


}
