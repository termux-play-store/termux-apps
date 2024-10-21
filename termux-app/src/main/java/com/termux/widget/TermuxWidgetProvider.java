package com.termux.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.termux.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Widget providing a list to launch scripts in ~/.shortcuts/.
 * <p>
 * See https://developer.android.com/guide/topics/appwidgets/index.html
 */
public final class TermuxWidgetProvider extends AppWidgetProvider {

    /**
     * "This is called to update the App Widget at intervals defined by the updatePeriodMillis attribute in the
     * AppWidgetProviderInfo (see Adding the AppWidgetProviderInfo Metadata above). This method is also called when the
     * user adds the App Widget, so it should perform the essential setup, such as define event handlers for Views and
     * start a temporary Service, if necessary. However, if you have declared a configuration Activity, this method is
     * not called when the user adds the App Widget, but is called for the subsequent updates. It is the responsibility
     * of the configuration Activity to perform the first update when configuration is done. (See Creating an App Widget
     * Configuration Activity below.)"
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidgetRemoteViews(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidgetRemoteViews(@NonNull Context context, @NonNull AppWidgetManager appWidgetManager, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        // The empty view is displayed when the collection has no items. It should be a sibling
        // of the collection view:
        remoteViews.setEmptyView(R.id.widget_list, R.id.empty_view);

        // Setup intent which points to the TermuxWidgetService which will provide the views for this collection.
        var intent = new Intent(context, TermuxWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // When intents are compared, the extras are ignored, so we need to embed the extras
        // into the data so that the extras will not be ignored.
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        remoteViews.setRemoteAdapter(R.id.widget_list, intent);

        // Setup refresh button:
        var refreshIntent = new Intent(context, TermuxWidgetProvider.class);
        refreshIntent.setAction(TermuxWidgetConstants.ACTION_REFRESH_WIDGET);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        refreshIntent.setData(Uri.parse(refreshIntent.toUri(Intent.URI_INTENT_SCHEME)));
        var refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        remoteViews.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent);

        // Here we setup the a pending intent template. Individuals items of a collection
        // cannot setup their own pending intents, instead, the collection as a whole can
        // setup a pending intent template, and the individual items can set a fillInIntent
        // to create unique before on an item to item basis.
        var clickedIntent = new Intent(context, TermuxWidgetProvider.class);
        clickedIntent.setAction(TermuxWidgetConstants.ACTION_WIDGET_ITEM_CLICKED);
        clickedIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        var clickedIntentPending = PendingIntent.getBroadcast(context, 0, clickedIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        remoteViews.setPendingIntentTemplate(R.id.widget_list, clickedIntentPending);

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        var action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        switch (action) {
            case AppWidgetManager.ACTION_APPWIDGET_UPDATE: {
                // The super class already handles this to call onUpdate to update remove views, but
                // we handle this ourselves and call notifyAppWidgetViewDataChanged as well afterwards.
                refreshAppWidgets(context, intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS), true);
                return;
            } case TermuxWidgetConstants.ACTION_WIDGET_ITEM_CLICKED: {
                var clickedFilePath = intent.getStringExtra(TermuxWidgetConstants.EXTRA_FILE_CLICKED);
                if (clickedFilePath == null || clickedFilePath.isEmpty()) {
                    Log.e(TermuxWidgetConstants.LOG_TAG, "Ignoring unset clicked file");
                    return;
                }

                var shortcut = new TermuxWidgetShortcutFile(new File(clickedFilePath));

                var executionIntent = shortcut.getExecutionIntent(false);
                if (shortcut.mIsTask) {
                    context.startForegroundService(executionIntent);
                    var message = context.getString(R.string.msg_executing_task, new File(clickedFilePath).getName());
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                } else {
                    context.startActivity(executionIntent);
                }

                return;
            } case TermuxWidgetConstants.ACTION_REFRESH_WIDGET: {
                int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
                int[] appWidgetIds;
                boolean updateRemoteViews = false;
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    appWidgetIds = new int[]{appWidgetId};
                } else {
                    appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, TermuxWidgetProvider.class));
                    Log.d(TermuxWidgetConstants.LOG_TAG, "Refreshing all widget ids: " + Arrays.toString(appWidgetIds));

                    // Only update remote views if sendIntentToRefreshAllWidgets() is called or if
                    // user sent intent with "am broadcast" command.
                    // A valid id would normally only be sent if refresh button of widget was successfully
                    // pressed and widget was not in a non-responsive state, so no need to update remote views.
                    updateRemoteViews = true;
                }

                var updatedAppWidgetIds = refreshAppWidgets(context, appWidgetIds, updateRemoteViews);
                if (updatedAppWidgetIds.isEmpty()) {
                    Log.e(TermuxWidgetConstants.LOG_TAG, "No widgets to reload");
                } else {
                    Log.i(TermuxWidgetConstants.LOG_TAG, "Reloaded widgets: " + updatedAppWidgetIds);
                }
                var msg = context.getString(R.string.msg_widgets_reloaded);
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                return;
            } default: {
                Log.w(TermuxWidgetConstants.LOG_TAG, "Unhandled action: " + action);
                break;
            }
        }

        super.onReceive(context, intent);
    }

    public static @NonNull List<Integer> refreshAppWidgets(@NonNull Context context, int[] appWidgetIds, boolean updateRemoteViews) {
        if (appWidgetIds == null) return Collections.emptyList();
        var updatedAppWidgetIds = new ArrayList<Integer>();
        for (int appWidgetId : appWidgetIds) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) continue;
            updatedAppWidgetIds.add(appWidgetId);
            if (updateRemoteViews) {
                updateAppWidgetRemoteViews(context, AppWidgetManager.getInstance(context), appWidgetId);
            }

            AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
        }

        return updatedAppWidgetIds;
    }

}
