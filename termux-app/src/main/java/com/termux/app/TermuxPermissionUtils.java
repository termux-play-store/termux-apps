package com.termux.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TermuxPermissionUtils {

    public static final int REQUEST_GRANT_STORAGE_PERMISSION = 1000;
    public static final int REQUEST_DISABLE_BATTERY_OPTIMIZATIONS = 2000;
    public static final int REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION = 2001;
    public static final int REQUEST_POST_NOTIFICATIONS = 1001;

    private static final String LOG_TAG = "PermissionUtils";


    /**
     * Check if app has been granted the required permission.
     *
     * @param context    The context for operations.
     * @param permission The {@link String} name for permission to check.
     * @return Returns {@code true} if permission is granted, otherwise {@code false}.
     */
    public static boolean checkPermission(@NonNull Context context, @NonNull String permission) {
        return checkPermissions(context, new String[]{permission});
    }

    /**
     * Check if app has been granted the required permissions.
     *
     * @param context     The context for operations.
     * @param permissions The {@link String[]} names for permissions to check.
     * @return Returns {@code true} if permissions are granted, otherwise {@code false}.
     */
    public static boolean checkPermissions(@NonNull Context context, @NonNull String[] permissions) {
        // checkSelfPermission may return true for permissions not even requested
        List<String> permissionsNotRequested = getPermissionsNotRequested(context, permissions);
        if (!permissionsNotRequested.isEmpty()) {
            Log.e(LOG_TAG, "Attempted to check for permissions that have not been requested in app manifest: " + permissionsNotRequested);
            return false;
        }
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    public static boolean requestPermission(@NonNull Context context, @NonNull String permission, int requestCode) {
        return requestPermissions(context, new String[]{permission}, requestCode);
    }

    public static boolean requestPermissions(@NonNull Context context, @NonNull String[] permissions, int requestCode) {
        List<String> permissionsNotRequested = getPermissionsNotRequested(context, permissions);
        if (!permissionsNotRequested.isEmpty()) {
            throw new RuntimeException("Requested permissions not in the manifest: " + permissionsNotRequested);
        }

        for (String permission : permissions) {
            int result = ContextCompat.checkSelfPermission(context, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                Log.i(LOG_TAG, "Requesting Permissions: " + Arrays.toString(permissions));
                ((Activity) context).requestPermissions(permissions, requestCode);
                break;
            }
        }

        return true;
    }


    /**
     * Check if app has requested the required permission in the manifest.
     *
     * @param context    The context for operations.
     * @param permission The {@link String} name for permission to check.
     * @return Returns {@code true} if permission has been requested, otherwise {@code false}.
     */
    public static boolean isPermissionRequested(@NonNull Context context, @NonNull String permission) {
        return getPermissionsNotRequested(context, new String[]{permission}).size() == 0;
    }

    /**
     * Check if app has requested the required permissions or not in the manifest.
     *
     * @param context     The context for operations.
     * @param permissions The {@link String[]} names for permissions to check.
     * @return Returns {@link List<String>} of permissions that have not been requested. It will have
     * size 0 if all permissions have been requested.
     */
    @NonNull
    public static List<String> getPermissionsNotRequested(@NonNull Context context, @NonNull String[] permissions) {
        List<String> permissionsNotRequested = new ArrayList<>();
        Collections.addAll(permissionsNotRequested, permissions);

        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        // If no permissions are requested, then nothing to check
        if (packageInfo.requestedPermissions == null || packageInfo.requestedPermissions.length == 0)
            return permissionsNotRequested;

        List<String> requestedPermissionsList = Arrays.asList(packageInfo.requestedPermissions);
        for (String permission : permissions) {
            if (requestedPermissionsList.contains(permission)) {
                permissionsNotRequested.remove(permission);
            }
        }

        return permissionsNotRequested;
    }

    public static boolean checkStoragePermission(@NonNull Context context, boolean checkLegacyStoragePermission) {
        if (checkLegacyStoragePermission) {
            return checkPermissions(context,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE});
        } else {
            return Environment.isExternalStorageManager();
        }
    }

    /**
     * Check if {@link Manifest.permission#REQUEST_IGNORE_BATTERY_OPTIMIZATIONS} permission has been
     * granted.
     *
     * @param context The context for operations.
     * @return Returns {@code true} if permission is granted, otherwise {@code false}.
     */
    public static boolean checkIfBatteryOptimizationsDisabled(@NonNull Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    @SuppressLint("BatteryLife")
    public static void requestDisableBatteryOptimizations(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));

        // Flag must not be passed for activity contexts, otherwise onActivityResult() will not be called with permission grant result.
        // Flag must be passed for non-activity contexts like services, otherwise "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag" exception will be raised.
        if (!(context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

}
