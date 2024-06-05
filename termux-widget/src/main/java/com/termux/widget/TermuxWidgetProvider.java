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
                var isTask = intent.getBooleanExtra(TermuxWidgetConstants.EXTRA_IS_TASK, false);

                if (isTask) {
                    var startTerminalSessionIntent = new Intent();
                    startTerminalSessionIntent.setClassName("com.termux", "com.termux.app.TermuxService");
                    startTerminalSessionIntent.setAction("com.termux.service.action.service_execute");
                    startTerminalSessionIntent.putExtra("com.termux.execute.background", true);
                    var data = new Uri.Builder().scheme("path").path(clickedFilePath).build();
                    startTerminalSessionIntent.setData(data);
                    context.startForegroundService(startTerminalSessionIntent);

                    var message = context.getString(R.string.msg_executing_task, new File(clickedFilePath).getName());
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                } else {
                    var startTerminalSessionIntent = new Intent();
                    startTerminalSessionIntent.setClassName("com.termux", "com.termux.app.TermuxActivityInternal");
                    startTerminalSessionIntent.setAction(Intent.ACTION_RUN);
                    var data = new Uri.Builder().scheme("path").path(clickedFilePath).build();
                    startTerminalSessionIntent.setData(data);
                    startTerminalSessionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(startTerminalSessionIntent);
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
        List<Integer> updatedAppWidgetIds = new ArrayList<>();
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

    /**
     * Extract termux shortcut file path from an intent and send intent to TermuxService to execute it.
     *
     * @param context The {@link Context} that will be used to send execution intent to the TermuxService.
     * @param intent The {@link Intent} received for the shortcut file.
     */
    public static void handleTermuxShortcutExecutionIntent(Context context, Intent intent, String logTag) {
        /*
        if (context == null || intent == null) return;
        logTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG);
        String token = intent.getStringExtra(TermuxConstants.TERMUX_WIDGET.EXTRA_TOKEN_NAME);
        if (token == null || !token.equals(TermuxWidgetAppSharedPreferences.getGeneratedToken(context))) {
            Logger.logWarn(logTag, "Invalid token \"" + token + "\" for intent:\n" + IntentUtils.getIntentString(intent));
            Toast.makeText(context, R.string.msg_bad_token, Toast.LENGTH_LONG).show();
            return;
        }

        String errmsg;
        Error error;

        ExecutionCommand executionCommand = new ExecutionCommand();
        executionCommand.executable = intent.getData().getPath();

        // If executable is null or empty, then exit here instead of getting canonical path which would expand to "/"
        if (executionCommand.executable == null || executionCommand.executable.isEmpty()) {
            errmsg  = context.getString(R.string.error_null_or_empty_executable);
            Logger.logErrorAndShowToast(context, logTag, errmsg);
            return;
        }

        // Get canonical path of executable
        executionCommand.executable = FileUtils.getCanonicalPath(executionCommand.executable, null);

        // If executable is not under SHORTCUT_FILES_ALLOWED_PATHS_LIST
        if (!FileUtils.isPathInDirPaths(executionCommand.executable, ShortcutUtils.SHORTCUT_FILES_ALLOWED_PATHS_LIST, true)) {
            errmsg = context.getString(R.string.error_executable_not_under_shortcuts_directories,
                    String.join(", ", TermuxFileUtils.getUnExpandedTermuxPaths(ShortcutUtils.SHORTCUT_FILES_ALLOWED_PATHS_LIST))) +
                    "\n" + context.getString(R.string.msg_executable_absolute_path, executionCommand.executable);
            Logger.logErrorAndShowToast(context, logTag, errmsg);
            return;
        }

        // If executable is not a regular file, or is not readable or executable, then return
        // RESULT_CODE_FAILED to plugin host app
        // Setting of read and execute permissions are only done if executable is under TermuxConstants#TERMUX_SHORTCUT_SCRIPTS_DIR_PATH
        error = FileUtils.validateRegularFileExistenceAndPermissions("executable", executionCommand.executable,
                TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH,
                FileUtils.APP_EXECUTABLE_FILE_PERMISSIONS,
                true, true,
                false);
        if (error != null) {
            error.appendMessage("\n" + context.getString(R.string.msg_executable_absolute_path, executionCommand.executable));
            executionCommand.setStateFailed(error);
            Logger.logErrorAndShowToast(context, logTag, ResultData.getErrorsListMinimalString(executionCommand.resultData));
            return;
        }


        // If executable is under a directory with the basename matching TermuxConstants#TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME
        File shortcutFile = new File(executionCommand.executable);
        File shortcutParentDirFile = shortcutFile.getParentFile();
        if (shortcutParentDirFile != null && shortcutParentDirFile.getName().equals(TermuxConstants.TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME)) {
            executionCommand.inBackground = true;
            // Show feedback for background task
            Toast toast = Toast.makeText(context, context.getString(R.string.msg_executing_task,
                    ShellUtils.getExecutableBasename(executionCommand.executable)),
                    Toast.LENGTH_SHORT);
            // Put the toast at the top of the screen, to avoid blocking eventual
            // toasts made from the task with termux-toast.
            // See https://github.com/termux/termux-widget/issues/33
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
        }


        // Create execution intent with the action TERMUX_SERVICE#ACTION_SERVICE_EXECUTE to be sent to the TERMUX_SERVICE
        executionCommand.executableUri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(executionCommand.executable).build();
        Intent executionIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executionCommand.executableUri);
        executionIntent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, executionCommand.inBackground);
        executionIntent.putExtra(TERMUX_SERVICE.EXTRA_PLUGIN_API_HELP, context.getString(R.string.plugin_api_help, TermuxConstants.TERMUX_WIDGET_GITHUB_REPO_URL));

            // Send execution intent to execution service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // https://developer.android.com/about/versions/oreo/background.html
                context.startForegroundService(executionIntent);
            } else {
                context.startService(executionIntent);
            }
         */
    }

}
