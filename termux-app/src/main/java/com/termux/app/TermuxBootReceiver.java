package com.termux.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class TermuxBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.e(TermuxConstants.LOG_TAG, "Unexpected intent: " + intent.getAction());
            return;
        }

        Log.i(TermuxConstants.LOG_TAG, "Received BOOT_COMPLETED on boot");

        for (var scriptDirSuffix : new String[]{"/.config/termux/boot", "/.termux/boot"}) {
            var bootScriptsPath = TermuxConstants.HOME_PATH + scriptDirSuffix;
            var bootScriptsDir = new File(bootScriptsPath);
            var files = bootScriptsDir.listFiles();
            Log.i(TermuxConstants.LOG_TAG, "Boot scripts in " + bootScriptsPath + ": " + Arrays.toString(files));
            if (files == null) continue;
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (var scriptFile : files) {
                if (!scriptFile.canRead() && !scriptFile.setReadable(true)) {
                    Log.e(TermuxConstants.LOG_TAG, "Cannot set file readable: " + scriptFile.getAbsolutePath());
                } else if (!scriptFile.canExecute() && !scriptFile.setExecutable(true)) {
                    Log.e(TermuxConstants.LOG_TAG, "Cannot set file executable: " + scriptFile.getAbsolutePath());
                } else {
                    var executeIntent = new Intent(TermuxService.ACTION_SERVICE_EXECUTE)
                        .setData(Uri.fromFile(scriptFile))
                        .setClass(context, TermuxService.class)
                        .putExtra(TermuxService.TERMUX_EXECUTE_EXTRA_BACKGROUND, true);
                    // See https://developer.android.com/guide/components/foreground-services#background-start-restriction-exemptions
                    // - it is ok to start a foreground service from the background after the device reboots and receives
                    // the ACTION_BOOT_COMPLETED intent action in a broadcast receiver.
                    context.startForegroundService(executeIntent);
                }
            }
        }
    }

}
