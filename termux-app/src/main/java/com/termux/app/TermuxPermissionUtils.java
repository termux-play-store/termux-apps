package com.termux.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class TermuxPermissionUtils {

    public static final int REQUEST_DISABLE_BATTERY_OPTIMIZATIONS = 2001;
    public static final int REQUEST_POST_NOTIFICATIONS = 2002;

    /**
     * Check which permissions that has not been granted.
     *
     * @param context     The context for operations.
     * @param permissions The {@link String[]} names for permissions to check.
     * @return Returns the list of permissions not granted.
     */
    public static ArrayList<String> checkNonGrantedPermissions(@NonNull Context context, @NonNull String ... permissions) {
        var result = new ArrayList<String>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED) {
                result.add(permission);
            }
        }
        return result;
    }

    public static void requestPermissions(@NonNull Context context, int requestCode, @NonNull String ... desiredPermissions) {
        var permissionsToRequest = new ArrayList<String>();
        for (String permission : desiredPermissions) {
            int result = ContextCompat.checkSelfPermission(context, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            Log.i(TermuxConstants.LOG_TAG, "Requesting Permissions: " + permissionsToRequest);
            ((Activity) context).requestPermissions(permissionsToRequest.toArray(new String[0]), requestCode);
        }
    }

    /**
     * Asks the user to allow this app to ignore battery optimizations if necessary.
     */
    @SuppressLint("BatteryLife")
    public static void requestDisableBatteryOptimizationsIfNecessary(@NonNull Context context) {
        var powerManager = context.getSystemService(PowerManager.class);
        if (!powerManager.isIgnoringBatteryOptimizations(context.getPackageName())) {
            var intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + context.getPackageName()));
            if (!(context instanceof Activity)) {
                // Flag must not be passed for activity contexts, otherwise onActivityResult() will not be called with permission grant result.
                // Flag must be passed for non-activity contexts like services, otherwise "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag" exception will be raised.
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        }
    }

}
