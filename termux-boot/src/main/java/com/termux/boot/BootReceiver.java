package com.termux.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Intent executeIntent = new Intent("com.termux.app.ACTION_ON_BOOT");
        executeIntent.setClassName("com.termux", "com.termux.app.TermuxService");

        // See https://developer.android.com/guide/components/foreground-services#background-start-restriction-exemptions
        // - it is ok to start a foreground service from the background after the device reboots and receives
        // the ACTION_BOOT_COMPLETED intent action in a broadcast receiver.
        context.startForegroundService(executeIntent);

        Log.i("termux", "Termux:Boot done");
    }

}
