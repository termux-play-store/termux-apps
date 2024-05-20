package com.termux.app;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

import androidx.annotation.NonNull;

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

    public static class ContentProvider extends android.content.ContentProvider {

        private static final String LOG_TAG = "TermuxContentProvider";

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            File file = new File(uri.getPath());

            if (projection == null) {
                projection = new String[]{
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns._ID
                };
            }

            MatrixCursor cursor = new MatrixCursor(projection);
            addFileRow(cursor, file, projection);
            return cursor;
        }

        private void addFileRow(MatrixCursor cursor, File file, String[] projection) {
            if (file.isDirectory()) {
                var contentFiles = file.listFiles();
                if (contentFiles != null) {
                    for (var contentFile : contentFiles) {
                        addFileRow(cursor, contentFile, projection);
                    }
                }
                return;
            }
            Object[] row = new Object[projection.length];
            for (int i = 0; i < projection.length; i++) {
                String column = projection[i];
                Object value;
                switch (column) {
                    case MediaStore.MediaColumns.DISPLAY_NAME:
                        value = file.getName();
                        break;
                    case MediaStore.MediaColumns.RELATIVE_PATH:
                        // TODO: Test and validate
                        value = file.getParentFile().getAbsolutePath().substring(TermuxConstants.FILES_PATH.length()) + "/";
                        break;
                    case MediaStore.MediaColumns.SIZE:
                        value = (int) file.length();
                        break;
                    case MediaStore.MediaColumns._ID:
                        value = 1;
                        break;
                    default:
                        value = null;
                }
                row[i] = value;
            }
            cursor.addRow(row);
        }

        @Override
        public String getType(@NonNull Uri uri) {
            String path = uri.getLastPathSegment();
            if (path != null) {
                int extIndex = path.lastIndexOf('.') + 1;
                if (extIndex > 0) {
                    MimeTypeMap mimeMap = MimeTypeMap.getSingleton();
                    String ext = path.substring(extIndex).toLowerCase(Locale.ROOT);
                    return mimeMap.getMimeTypeFromExtension(ext);
                }
            }
            return null;
        }

        @Override
        public Uri insert(@NonNull Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
            File file = new File(uri.getPath());
            try {
                String path = file.getCanonicalPath();
                // String callingPackageName = getCallingPackage();
                String storagePath = Environment.getExternalStorageDirectory().getCanonicalPath();
                // See https://support.google.com/faqs/answer/7496913:
                if (!(path.startsWith(TermuxConstants.FILES_PATH) || path.startsWith(storagePath))) {
                    throw new IllegalArgumentException("Invalid path: " + path);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }

            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
        }
    }

}
