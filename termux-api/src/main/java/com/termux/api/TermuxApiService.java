package com.termux.api;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.termux.api.activities.TermuxApiPermissionActivity;
import com.termux.api.apis.BrightnessAPI;
import com.termux.api.apis.CallLogAPI;
import com.termux.api.apis.CameraInfoAPI;
import com.termux.api.apis.CameraPhotoAPI;
import com.termux.api.apis.ContactListAPI;
import com.termux.api.apis.FingerprintAPI;
import com.termux.api.apis.InfraredAPI;
import com.termux.api.apis.JobSchedulerAPI;
import com.termux.api.apis.LocationAPI;
import com.termux.api.apis.MediaPlayerAPI;
import com.termux.api.apis.MicRecorderAPI;
import com.termux.api.apis.NfcAPI;
import com.termux.api.apis.NotificationAPI;
import com.termux.api.apis.NotificationListAPI;
import com.termux.api.apis.SensorAPI;
import com.termux.api.apis.SmsInboxAPI;
import com.termux.api.apis.SmsSendAPI;
import com.termux.api.apis.SpeechToTextAPI;
import com.termux.api.apis.TelephonyAPI;
import com.termux.api.apis.TextToSpeechAPI;
import com.termux.api.apis.TorchAPI;
import com.termux.api.apis.WallpaperAPI;
import com.termux.api.apis.WifiAPI;

public class TermuxApiService extends Service {

    private static final String LOG_TAG = "termux-api";

    /**
     * Command to the service to display a message.
     */
    static final int MSG_PERFORM_API_INTENT = 1;

    /**
     * Handler of incoming messages from clients.
     */
    static class IncomingHandler extends Handler {
        private final Context applicationContext;

        IncomingHandler(TermuxApiService context) {
            applicationContext = context.getApplicationContext();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PERFORM_API_INTENT:
                    var intent = (Intent) msg.obj;
                    doWork(applicationContext, intent);
                    break;
                default:
                    Log.e(TermuxAPIConstants.LOG_TAG, "Unhandled msg.what: " + msg.what);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    Messenger mMessenger;

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = new Messenger(new IncomingHandler(this));
        return mMessenger.getBinder();
    }

    private static void doWork(Context context, Intent intent) {
        String apiMethod = intent.getStringExtra("api_method");
        if (apiMethod == null) {
            Log.e(LOG_TAG, "Missing 'api_method' extra");
            return;
        }

        switch (apiMethod) {
            case "Brightness":
                if (!Settings.System.canWrite(context)) {
                    TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.WRITE_SETTINGS);
                    Toast.makeText(context, "Please enable permission for Termux:API", Toast.LENGTH_LONG).show();

                    // user must enable WRITE_SETTINGS permission this special way
                    var settingsIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(settingsIntent);
                    return;
                }
                BrightnessAPI.onReceive(context, intent);
                break;
            case "CameraInfo":
                CameraInfoAPI.onReceive(context, intent);
                break;
            case "CameraPhoto":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.CAMERA)) {
                    CameraPhotoAPI.onReceive(context, intent);
                }
                break;
            case "CallLog":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.READ_CALL_LOG)) {
                    CallLogAPI.onReceive(context, intent);
                }
                break;
            case "ContactList":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.READ_CONTACTS)) {
                    ContactListAPI.onReceive(context, intent);
                }
                break;
            case "Fingerprint":
                FingerprintAPI.onReceive(context, intent);
                break;
            case "InfraredFrequencies":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.TRANSMIT_IR)) {
                    InfraredAPI.onReceiveCarrierFrequency(context, intent);
                }
                break;
            case "InfraredTransmit":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.TRANSMIT_IR)) {
                    InfraredAPI.onReceiveTransmit(context, intent);
                }
                break;
            case "JobScheduler":
                JobSchedulerAPI.onReceive(context, intent);
                break;
            case "Location":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                    LocationAPI.onReceive(context, intent);
                }
                break;
            case "MediaPlayer":
                MediaPlayerAPI.onReceive(context, intent);
                break;
            case "MicRecorder":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.RECORD_AUDIO)) {
                    MicRecorderAPI.onReceive(context, intent);
                }
                break;
            case "Nfc":
                NfcAPI.onReceive(context, intent);
                break;
            case "NotificationList":
                ComponentName cn = new ComponentName(context, NotificationListAPI.NotificationService.class);
                String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
                final boolean NotificationServiceEnabled = flat != null && flat.contains(cn.flattenToString());
                if (!NotificationServiceEnabled) {
                    Toast.makeText(context,"Please give Termux:API Notification Access", Toast.LENGTH_LONG).show();
                    context.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    NotificationListAPI.onReceive(context, intent);
                }
                break;
            case "Notification":
                NotificationAPI.onReceiveShowNotification(context, intent);
                break;
            case "NotificationChannel":
                NotificationAPI.onReceiveChannel(context, intent);
                break;
            case "NotificationRemove":
                NotificationAPI.onReceiveRemoveNotification(context, intent);
                break;
            case "NotificationReply":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, Manifest.permission.POST_NOTIFICATIONS)) {
                    NotificationAPI.onReceiveReplyToNotification(context, intent);
                }
                break;
            case "Sensor":
                SensorAPI.onReceive(context, intent);
                break;
            case "SmsInbox":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.READ_SMS, android.Manifest.permission.READ_CONTACTS)) {
                    SmsInboxAPI.onReceive(context, intent);
                }
                break;
            case "SmsSend":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.SEND_SMS)) {
                    SmsSendAPI.onReceive(context, intent);
                }
                break;
            case "SpeechToText":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.RECORD_AUDIO)) {
                    SpeechToTextAPI.onReceive(context, intent);
                }
                break;
            case "TelephonyCall":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.CALL_PHONE)) {
                    TelephonyAPI.onReceiveTelephonyCall(context, intent);
                }
                break;
            case "TelephonyCellInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    TelephonyAPI.onReceiveTelephonyCellInfo(context, intent);
                }
                break;
            case "TelephonyDeviceInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, android.Manifest.permission.READ_PHONE_STATE)) {
                    TelephonyAPI.onReceiveTelephonyDeviceInfo(context, intent);
                }
                break;
            case "TextToSpeech":
                TextToSpeechAPI.onReceive(context, intent);
                break;
            case "Torch":
                TorchAPI.onReceive(context, intent);
                break;
            case "Wallpaper":
                WallpaperAPI.onReceive(context, intent);
                break;
            case "WifiConnectionInfo":
                WifiAPI.onReceiveWifiConnectionInfo(context, intent);
                break;
            case "WifiScanInfo":
                if (TermuxApiPermissionActivity.checkAndRequestPermission(context, intent, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    WifiAPI.onReceiveWifiScanInfo(context, intent);
                }
                break;
            case "WifiEnable":
                WifiAPI.onReceiveWifiEnable(context, intent);
                break;
            default:
                Log.e(LOG_TAG, "Unrecognized 'api_method' extra: '" + apiMethod + "'");
        }
    }

}
