package com.termux.widget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;

public final class ShortcutFile {

    private static final String LOG_TAG = "ShortcutFile";

    public final String mPath;
    public String mLabel;

    public ShortcutFile(@NonNull String path) {
        this(path, null);
    }

    public ShortcutFile(@NonNull File file) {
        this(file.getAbsolutePath(), null);
    }

    public ShortcutFile(@NonNull File file, int depth) {
        this(file.getAbsolutePath(),
                (depth > 0 && file.getParentFile() != null ? (file.getParentFile().getName() + "/") : "") + file.getName());
    }

    public ShortcutFile(@NonNull String path, @Nullable String defaultLabel) {
        mPath = path;
        mLabel = getLabelForShortcut(defaultLabel);
    }

    @NonNull
    public String getPath() {
        return mPath;
    }

    @NonNull
    public String getLabel() {
        return mLabel;
    }

    @NonNull
    public String getLabelForShortcut(@Nullable String defaultLabel) {
        return (defaultLabel == null || defaultLabel.isEmpty()) ? new File(mPath).getName() : defaultLabel;
    }

    public Intent getExecutionIntent(Context context) {
        /*
        Uri scriptUri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(getPath()).build();
        Intent executionIntent = new Intent(context, TermuxLaunchShortcutActivity.class);
        executionIntent.setAction(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE); // Mandatory for pinned shortcuts
        executionIntent.setData(scriptUri);
        executionIntent.putExtra(TERMUX_WIDGET.EXTRA_TOKEN_NAME, TermuxWidgetAppSharedPreferences.getGeneratedToken(context));
        return executionIntent;
         */
        return null;
    }

    public ShortcutInfo getShortcutInfo(Context context, boolean showToastForIconUsed) {
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, getPath());
        builder.setIntent(getExecutionIntent(context));
        builder.setShortLabel(getLabel());
        return builder.build();
    }

    public RemoteViews getListWidgetView(Context context) {
        // Position will always range from 0 to getCount() - 1.
        // Construct remote views item based on the item xml file and set text based on position.
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_item);
        remoteViews.setTextViewText(R.id.widget_item, getLabel());

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in TermuxAppWidgetProvider.
        Log.e("TERMUX", "FILLING IN: " + getPath());
        Intent fillInIntent = new Intent().putExtra(TermuxWidgetConstants.EXTRA_FILE_CLICKED, getPath());
        remoteViews.setOnClickFillInIntent(R.id.widget_item_layout, fillInIntent);

        return remoteViews;
    }

}
