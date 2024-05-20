package com.termux.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public final class TermuxWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        return new ListRemoteViewsFactory(getApplicationContext(), appWidgetId);
    }

    public static class ListRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

        private final List<ShortcutFile> shortcutFiles = new ArrayList<>();
        private final Context mContext;
        private final int mAppWidgetId;

        public ListRemoteViewsFactory(Context context, int appWidgetId) {
            mContext = context;
            mAppWidgetId = appWidgetId;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDestroy() {
            shortcutFiles.clear();
        }

        @Override
        public int getCount() {
            return shortcutFiles.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            // Position will always range from 0 to getCount() - 1.
            return shortcutFiles.get(position).getListWidgetView(mContext);
        }

        @Override
        public RemoteViews getLoadingView() {
            // You can create a custom loading view (for instance when getViewAt() is slow.) If you
            // return null here, you will get the default loading view.
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public void onDataSetChanged() {
            Log.e("termux", "WIDGET: onDataSetChanged");

            // This is triggered when you call AppWidgetManager notifyAppWidgetViewDataChanged
            // on the collection view corresponding to this factory. You can do heaving lifting in
            // here, synchronously. For example, if you need to process an image, fetch something
            // from the network, etc., it is ok to do it here, synchronously. The widget will remain
            // in its current state while work is being done here, so you don't need to worry about
            // locking up the widget.
            shortcutFiles.clear();

            var contentUri = Uri.parse("com.termux.files://" + TermuxWidgetConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH);
            try (var cursor = mContext.getContentResolver().query(contentUri, null, null, null, null)) {
                if (cursor == null) {
                    Log.e("termux", "WIDGET: Null cursor");
                    return;
                }
                var displayNameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                var relativePathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                while (cursor.moveToNext()) {
                    var displayName = cursor.getString(displayNameIdx);
                    var relativePath = cursor.getString(relativePathIdx);
                    Log.e("termux", "WIDGET: path=" + relativePath + ", name=" + displayName);
                    var shortcutFile = new ShortcutFile()
                }
            }

            //ShortcutUtils.enumerateShortcutFiles(shortcutFiles, true);
        }
    }

}
