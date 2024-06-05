package com.termux.widget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import java.io.File;

public final class ShortcutFile {

    public final String mPath;
    public final String mLabel;
    public final boolean mDisplaysDirectory;
    public final boolean mIsTask;

    public ShortcutFile(@NonNull File file) {
        mPath = file.getAbsolutePath();
        var parentDirName = file.getParentFile().getName();
        mDisplaysDirectory = !parentDirName.equals(TermuxWidgetConstants.SHORTCUTS_DIR_NAME);
        mIsTask = parentDirName.equals(TermuxWidgetConstants.TASKS_DIR_NAME);
        mLabel = mDisplaysDirectory ? (parentDirName + "/" + file.getName()) : file.getName();
    }

    @NonNull
    public String getPath() {
        return mPath;
    }

    @NonNull
    public String getLabel() {
        return mLabel;
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
        Intent fillInIntent = new Intent()
            .putExtra(TermuxWidgetConstants.EXTRA_FILE_CLICKED, getPath())
            .putExtra(TermuxWidgetConstants.EXTRA_IS_TASK, mIsTask);

        remoteViews.setOnClickFillInIntent(R.id.widget_item_layout, fillInIntent);

        return remoteViews;
    }

}
