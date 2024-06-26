package com.termux.app.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class VibrateAPI {

    private static final String LOG_TAG = "VibrateAPI";

    public static void onReceive(Context context, Intent intent) {
        var vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        var milliseconds = intent.getIntExtra("duration_ms", 1000);
        var force = intent.getBooleanExtra("force", false);

        var am = context.getSystemService(AudioManager.class);
        // Do not vibrate if "Silent" ringer mode or "Do Not Disturb" is enabled and -f/--force option is not used.
        if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT || force) {
            try {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE),
                    new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build());
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to run vibrator", e);
            }
        }

        ResultReturner.noteDone(intent);
    }

}
