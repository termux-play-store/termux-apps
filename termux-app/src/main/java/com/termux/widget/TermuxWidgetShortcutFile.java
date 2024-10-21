package com.termux.widget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.termux.R;

import java.io.File;
import java.util.Comparator;
import java.util.List;

final class TermuxWidgetShortcutFile {

    public static void sort(List<TermuxWidgetShortcutFile> shortcutFiles) {
        shortcutFiles.sort(Comparator
            .comparingInt((TermuxWidgetShortcutFile a) -> (a.mDisplaysDirectory ? 1 : -1))
            .thenComparing((a, b) -> NaturalOrderComparator.compare(a.mLabel, b.mLabel))
        );
    }

    public final String mPath;
    public final String mLabel;
    public final boolean mDisplaysDirectory;
    public final boolean mIsTask;

    public TermuxWidgetShortcutFile(@NonNull File file) {
        mPath = file.getAbsolutePath();
        var parentDirName = file.getParentFile().getName();
        var parentAbsolutePath = file.getParentFile().getAbsolutePath();
        mDisplaysDirectory = !(TermuxWidgetConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH.equals(parentAbsolutePath)
            || TermuxWidgetConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH_LEGACY.equals(parentAbsolutePath));
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

    public Intent getExecutionIntent(boolean forceActivity) {
        var data = new Uri.Builder().scheme("path").path(mPath).build();
        if (mIsTask && !forceActivity) {
            return new Intent()
                .setClassName("com.termux", "com.termux.app.TermuxService")
                .setAction("com.termux.service.action.service_execute")
                .putExtra("com.termux.execute.background", true)
                .setData(data);
        } else {
            return new Intent()
                .setClassName("com.termux", "com.termux.app.TermuxActivityInternal")
                .setAction(Intent.ACTION_RUN)
                .setData(data)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }

    public ShortcutInfo getShortcutInfo(Context context) {
        return new ShortcutInfo.Builder(context, getPath())
            .setIntent(getExecutionIntent(true))
            .setShortLabel(getLabel())
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_icon))
            .build();
    }

    public RemoteViews getListWidgetView(Context context) {
        // Position will always range from 0 to getCount() - 1.
        // Construct remote views item based on the item xml file and set text based on position.
        var remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_item);
        remoteViews.setTextViewText(R.id.widget_item, getLabel());

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in TermuxAppWidgetProvider.
        var fillInIntent = new Intent()
            .putExtra(TermuxWidgetConstants.EXTRA_FILE_CLICKED, getPath());

        remoteViews.setOnClickFillInIntent(R.id.widget_item_layout, fillInIntent);

        return remoteViews;
    }

}
