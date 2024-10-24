package com.termux.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TermuxBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.e(TermuxConstants.LOG_TAG, "Unexpected intent: " + intent.getAction());
            return;
        }

        Log.i(TermuxConstants.LOG_TAG, "Received BOOT_COMPLETED on boot");

        var executeIntent = new Intent(TermuxService.ACTION_BOOT_COMPLETED)
            .setClass(context, TermuxService.class);

        // See https://developer.android.com/guide/components/foreground-services#background-start-restriction-exemptions
        // - it is ok to start a foreground service from the background after the device reboots and receives
        // the ACTION_BOOT_COMPLETED intent action in a broadcast receiver.
        context.startForegroundService(executeIntent);
    }

}
