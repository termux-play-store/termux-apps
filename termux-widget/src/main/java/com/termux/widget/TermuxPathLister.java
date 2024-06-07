package com.termux.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.function.Consumer;

public class TermuxPathLister {

    private static int getColumnIndexOrThrow(Cursor cursor, String columnName) {
        var columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex < -1) {
            throw new RuntimeException("No '" + columnName + "' column");
        }
        return columnIndex;
    }

    public static void listPaths(Context context, String prefix, Consumer<String> onFile) {
        var contentUri = new Uri.Builder()
            .scheme("content")
            .authority("com.termux.files")
            .path(prefix)
            .build();
        try (var cursor = context.getContentResolver().query(contentUri, null, null, null, null)) {
            if (cursor == null) {
                Log.e(TermuxWidgetConstants.LOG_TAG, "termux-widget: Cursor from content resolver is null");
                return;
            }
            var absolutePathColumn = getColumnIndexOrThrow(cursor, "termux_path");
            while (cursor.moveToNext()) {
                var absolutePath = cursor.getString(absolutePathColumn);
                onFile.accept(absolutePath);
            }
        }
    }

}
