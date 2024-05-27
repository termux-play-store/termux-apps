package com.termux.app;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.Locale;

public class TermuxOpenReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "TermuxOpenReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Uri data = intent.getData();
        if (data == null) {
            Log.e(LOG_TAG, "Called without intent data");
            return;
        }

        Log.v(LOG_TAG, "uri: \"" + data + "\", path: \"" + data.getPath() + "\", fragment: \"" + data.getFragment() + "\"");

        final String contentTypeExtra = intent.getStringExtra("content-type");
        final boolean useChooser = intent.getBooleanExtra("chooser", false);
        final String intentAction = intent.getAction() == null ? Intent.ACTION_VIEW : intent.getAction();
        switch (intentAction) {
            case Intent.ACTION_SEND:
            case Intent.ACTION_VIEW:
                // Ok.
                break;
            default:
                Log.e(LOG_TAG, "Invalid action '" + intentAction + "', using 'view'");
                break;
        }

        String scheme = data.getScheme();
        if (scheme != null && !"file".equals(scheme)) {
            Intent urlIntent = new Intent(intentAction, data);
            if (intentAction.equals(Intent.ACTION_SEND)) {
                urlIntent.putExtra(Intent.EXTRA_TEXT, data.toString());
                urlIntent.setData(null);
            } else if (contentTypeExtra != null) {
                urlIntent.setDataAndType(data, contentTypeExtra);
            }
            urlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(urlIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(LOG_TAG, "No app handles the url " + data);
            }
            return;
        }

        // Get full path including fragment (anything after last "#")
        String filePath = data.getPath();
        if (filePath == null || filePath.isEmpty()) {
            Log.e(LOG_TAG, "filePath is null or empty");
            return;
        }

        final File fileToShare = new File(filePath);
        if (!(fileToShare.isFile() && fileToShare.canRead())) {
            Log.e(LOG_TAG, "Not a readable file: '" + fileToShare.getAbsolutePath() + "'");
            return;
        }

        var sendIntent = new Intent()
            .setAction(intentAction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        String contentTypeToUse;
        if (contentTypeExtra == null) {
            String fileName = fileToShare.getName();
            int lastDotIndex = fileName.lastIndexOf('.');
            String fileExtension = fileName.substring(lastDotIndex + 1);
            MimeTypeMap mimeTypes = MimeTypeMap.getSingleton();
            // Lower casing makes it work with e.g. "JPG":
            contentTypeToUse = mimeTypes.getMimeTypeFromExtension(fileExtension.toLowerCase(Locale.ROOT));
            if (contentTypeToUse == null) contentTypeToUse = "application/octet-stream";
        } else {
            contentTypeToUse = contentTypeExtra;
        }

        // Do not create Uri with Uri.parse() and use Uri.Builder().path(), check UriUtils.getUriFilePath().
        Uri uriToShare = new Uri.Builder().scheme("content").authority("com.termux.files").path(fileToShare.getAbsolutePath()).build();

        if (Intent.ACTION_SEND.equals(intentAction)) {
            sendIntent.putExtra(Intent.EXTRA_STREAM, uriToShare);
            sendIntent.setType(contentTypeToUse);
        } else {
            sendIntent.setDataAndType(uriToShare, contentTypeToUse);
        }

        if (useChooser) {
            sendIntent = Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(sendIntent);
        } catch (ActivityNotFoundException e) {
            Log.e(LOG_TAG, "No app handles the url " + data);
        }
    }

}
