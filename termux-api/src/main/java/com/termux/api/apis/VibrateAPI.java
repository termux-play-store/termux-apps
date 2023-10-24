package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;

public class VibrateAPI {

    private static final String LOG_TAG = "VibrateAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        new Thread() {
            @Override
            public void run() {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                int milliseconds = intent.getIntExtra("duration_ms", 1000);
                boolean force = intent.getBooleanExtra("force", false);

                AudioManager am = context.getSystemService(AudioManager.class);
                // Do not vibrate if in silent mode and -f/--force option is not used.
                if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT || force) {
                    try {
                        vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to run vibrator", e);
                    }
                }
            }
        }.start();

        ResultReturner.noteDone(apiReceiver, intent);
    }

}
