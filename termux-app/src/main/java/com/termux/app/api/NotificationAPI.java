package com.termux.app.api;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.util.Pair;

import com.termux.R;
import com.termux.app.TermuxConstants;
import com.termux.app.TermuxService;

import java.io.File;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.UUID;

public class NotificationAPI {

    private static final String LOG_TAG = "NotificationAPI";

    // public static final String BIN_SH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh";
    private static final String CHANNEL_ID = "termux-notification";
    private static final String CHANNEL_TITLE = "Termux API notification channel";
    private static final String KEY_TEXT_REPLY = "TERMUX_TEXT_REPLY";

    /**
     * Show a notification. Driven by the termux-notification script.
     */
    public static void onReceiveShowNotification(final Context context, final Intent intent) {
        Pair<Notification.Builder, String> pair = buildNotification(context, intent);
        Notification.Builder notification = pair.first;
        String notificationId = pair.second;
        ResultReturner.returnData(intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                if (!TextUtils.isEmpty(inputString)) {
                    if (inputString.contains("\n")) {
                        Notification.BigTextStyle style = new Notification.BigTextStyle();
                        style.bigText(inputString);
                        notification.setStyle(style);
                    } else {
                        notification.setContentText(inputString);
                    }
                }

                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        CHANNEL_TITLE, priorityFromIntent(intent));
                manager.createNotificationChannel(channel);

                manager.notify(notificationId, 0, notification.build());
            }
        });
    }

    public static void onReceiveChannel(final Context context, final Intent intent) {
        try {
            NotificationManager m = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = intent.getStringExtra("id");
            String channelName = intent.getStringExtra("name");

            if (channelId == null || channelId.isEmpty()) {
                ResultReturner.returnData(intent, out -> out.println("Channel id not specified."));
                return;
            }

            if (intent.getBooleanExtra("delete",false)) {
                m.deleteNotificationChannel(channelId);
                ResultReturner.returnData(intent, out -> out.println("Deleted channel with id \""+channelId+"\"."));
                return;
            }

            if (channelName == null || channelName.isEmpty()) {
                ResultReturner.returnData(intent, out -> out.println("Cannot create a channel without a name."));
            }

            NotificationChannel c = new NotificationChannel(channelId, channelName, priorityFromIntent(intent));
            m.createNotificationChannel(c);
            ResultReturner.returnData(intent, out -> out.println("Created channel with id \""+channelId+"\" and name \""+channelName+"\"."));
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception in onReceiveChannel()", e);
            ResultReturner.returnData(intent, out -> out.println("Could not create/delete channel."));
        }
    }
    
    private static int priorityFromIntent(Intent intent) {
        var priorityExtra = intent.getStringExtra("priority");
        return switch (priorityExtra) {
            case "high", "max" -> NotificationManager.IMPORTANCE_HIGH;
            case "low" -> NotificationManager.IMPORTANCE_LOW;
            case "min" -> NotificationManager.IMPORTANCE_MIN;
            case null, default -> NotificationManager.IMPORTANCE_DEFAULT;
        };
    }

    static Pair<Notification.Builder, String> buildNotification(final Context context, final Intent intent) {
        var priorityExtra = intent.getStringExtra("priority");
        int priority = switch (priorityExtra) {
            case "high" -> Notification.PRIORITY_HIGH;
            case "low" -> Notification.PRIORITY_LOW;
            case "max" -> Notification.PRIORITY_MAX;
            case "min" -> Notification.PRIORITY_MIN;
            case null, default -> Notification.PRIORITY_DEFAULT;
        };

        String title = intent.getStringExtra("title");
        String lightsArgbExtra = intent.getStringExtra("led-color");

        int ledColor = 0;
        if (lightsArgbExtra != null) {
            try {
                ledColor = Integer.parseInt(lightsArgbExtra, 16) | 0xff000000;
            } catch (NumberFormatException e) {
                Log.e(LOG_TAG, "Invalid LED color format! Ignoring!");
            }
        }

        int ledOnMs = intent.getIntExtra("led-on", 800);
        int ledOffMs = intent.getIntExtra("led-off", 800);

        long[] vibratePattern = intent.getLongArrayExtra("vibrate");
        boolean useSound = intent.getBooleanExtra("sound", false);
        boolean ongoing = intent.getBooleanExtra("ongoing", false);
        boolean alertOnce = intent.getBooleanExtra("alert-once", false);

        String actionExtra = intent.getStringExtra("action");

        final String notificationId = getNotificationId(intent);

        String groupKey = intent.getStringExtra("group");
        
        String channel = intent.getStringExtra("channel");
        if (channel == null) {
            channel = CHANNEL_ID;
        }
        
        var notification = new Notification.Builder(context, channel);
        notification.setSmallIcon(R.drawable.ic_event_note_black_24dp);
        notification.setColor(0xFF000000);
        notification.setContentTitle(title);
        notification.setPriority(priority);
        notification.setOngoing(ongoing);
        notification.setOnlyAlertOnce(alertOnce);
        notification.setWhen(System.currentTimeMillis());
        notification.setShowWhen(true);

        var imagePath = intent.getStringExtra("image-path");
        if (imagePath != null) {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                notification.setLargeIcon(myBitmap)
                        .setStyle(new Notification.BigPictureStyle().bigPicture(myBitmap));
            }
        }

        String styleType = intent.getStringExtra("type");
        if (Objects.equals(styleType, "media")) {
            String mediaPrevious = intent.getStringExtra("media-previous");
            String mediaPause = intent.getStringExtra("media-pause");
            String mediaPlay = intent.getStringExtra("media-play");
            String mediaNext = intent.getStringExtra("media-next");

            if (mediaPrevious != null && mediaPause != null && mediaPlay != null && mediaNext != null) {
                notification.setSmallIcon(android.R.drawable.ic_media_play);

                PendingIntent previousIntent = createAction(context, mediaPrevious);
                PendingIntent pauseIntent = createAction(context, mediaPause);
                PendingIntent playIntent = createAction(context, mediaPlay);
                PendingIntent nextIntent = createAction(context, mediaNext);

                notification.addAction(new Notification.Action(android.R.drawable.ic_media_previous, "previous", previousIntent));
                notification.addAction(new Notification.Action(android.R.drawable.ic_media_pause, "pause", pauseIntent));
                notification.addAction(new Notification.Action(android.R.drawable.ic_media_play, "play", playIntent));
                notification.addAction(new Notification.Action(android.R.drawable.ic_media_next, "next", nextIntent));

                notification.setStyle(new android.app.Notification.MediaStyle().setShowActionsInCompactView(0, 1, 3));
            }
        }

        if (groupKey != null) {
            notification.setGroup(groupKey);
        }

        if (ledColor != 0) {
            notification.setLights(ledColor, ledOnMs, ledOffMs);

            if (vibratePattern == null) {
                // Hack to make led work without vibrating.
                vibratePattern = new long[]{0};
            }
        }

        if (vibratePattern != null) {
            // Do not force the user to specify a delay first element, let it be 0.
            long[] vibrateArg = new long[vibratePattern.length + 1];
            System.arraycopy(vibratePattern, 0, vibrateArg, 1, vibratePattern.length);
            notification.setVibrate(vibrateArg);
        }

        if (useSound) {
            notification.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        }

        notification.setAutoCancel(true);

        if (actionExtra != null) {
            PendingIntent pi = createAction(context, actionExtra);
            notification.setContentIntent(pi);
        }

        for (int button = 1; button <= 3; button++) {
            String buttonText = intent.getStringExtra("button_text_" + button);
            String buttonAction = intent.getStringExtra("button_action_" + button);

            if (buttonText != null && buttonAction != null) {
                if (buttonAction.contains("$REPLY")) {
                    var action = createReplyAction(context, intent, button, buttonText, buttonAction, notificationId);
                    notification.addAction(action);
                } else {
                    PendingIntent pi = createAction(context, buttonAction);
                    notification.addAction(new Notification.Action(android.R.drawable.ic_input_add, buttonText, pi));
                }
            }
        }

        String onDeleteActionExtra = intent.getStringExtra("on_delete_action");
        if (onDeleteActionExtra != null) {
            PendingIntent pi = createAction(context, onDeleteActionExtra);
            notification.setDeleteIntent(pi);
        }

        return new Pair<>(notification, notificationId);
    }

    private static String getNotificationId(Intent intent) {
        String id = intent.getStringExtra("id");
        if (id == null) id = UUID.randomUUID().toString();
        return id;
    }

    public static void onReceiveRemoveNotification(final Context context, final Intent intent) {
        ResultReturner.noteDone(intent);
        var notificationId = intent.getStringExtra("id");
        if (notificationId != null) {
            var manager = context.getSystemService(NotificationManager.class);
            manager.cancel(notificationId, 0);
        }
    }

    static Notification.Action createReplyAction(Context context,
                                                 Intent intent,
                                                 int buttonNum,
                                                 String buttonText,
                                                 String buttonAction,
                                                 String notificationId) {
        var replyIntent = ((Intent) intent.clone())
            .setClass(context, TermuxService.class)
            .putExtra("api_method", "NotificationReply")
            .putExtra("id", notificationId)
            .putExtra("action", buttonAction);
        var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        var replyPendingIntent = PendingIntent.getService(context, buttonNum, replyIntent, pendingFlags);
        var remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(buttonText)
            .build();
        return new Notification.Action.Builder(com.termux.R.drawable.ic_event_note_black_24dp, buttonText, replyPendingIntent)
                        .addRemoteInput(remoteInput)
                        .build();
    }

    static CharSequence shellEscape(CharSequence input) {
        return "\"" + input.toString().replace("\"", "\\\"") + "\"";
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public static void onReceiveReplyToNotification(Context context, Intent intent) {
        var remoteInput = RemoteInput.getResultsFromIntent(intent);
        CharSequence reply = (remoteInput == null) ? null : remoteInput.getCharSequence(KEY_TEXT_REPLY);

        var action = intent.getStringExtra("action");
        if (action != null && reply != null) {
            action = action.replace("$REPLY", shellEscape(reply));
        }

        try {
            createAction(context, action).send();
        } catch (PendingIntent.CanceledException e) {
            Log.e(LOG_TAG, "CanceledException when performing action: " + action);
        }

        String notificationId = intent.getStringExtra("id");
        boolean ongoing = intent.getBooleanExtra("ongoing", false);
        Notification repliedNotification;
        var notificationManager = context.getSystemService(NotificationManager.class);
        if (ongoing) {
            // Re-issue the new notification to clear the spinner
            repliedNotification = buildNotification(context, intent).first.build();
            notificationManager.notify(notificationId, 0, repliedNotification);
        } else {
            // Cancel the notification
            notificationManager.cancel(notificationId, 0);
        }
    }

    static Intent createExecuteIntent(Context context, String action){
        return new Intent(TermuxService.ACTION_SERVICE_EXECUTE)
            .setClass(context, TermuxService.class)
            .setData(Uri.fromFile(new File(TermuxConstants.BIN_PATH + "/bash")))
            .putExtra(TermuxService.TERMUX_EXECUTE_EXTRA_ARGUMENTS, new String[]{"-c", action})
            .putExtra(TermuxService.TERMUX_EXECUTE_EXTRA_BACKGROUND, true);
    }

    static PendingIntent createAction(final Context context, String action){
        Intent executeIntent = createExecuteIntent(context, action);
        return PendingIntent.getService(context, 0, executeIntent, PendingIntent.FLAG_IMMUTABLE);
    }
}
