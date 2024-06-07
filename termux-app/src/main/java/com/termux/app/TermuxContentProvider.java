package com.termux.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * A {@link ContentProvider} providing access to files inside the Termux environment.
 * Used by {@link TermuxOpenReceiver} when opening a file with an external app.
 */
public class TermuxContentProvider extends ContentProvider {

    public static final String URI_AUTHORITY = "com.termux.files";
    public static final String TERMUX_PATH_COLUMN_NAME = "termux_path";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = new File(uri.getPath());

        if (projection == null) {
            projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                TERMUX_PATH_COLUMN_NAME,
            };
        }

        var cursor = new MatrixCursor(projection);
        if (file.exists()) {
            addFileRow(cursor, file, projection);
        }
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
                case TERMUX_PATH_COLUMN_NAME:
                    value = file.getAbsolutePath();
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
