package com.termux.api.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.JsonWriter;
import android.util.Log;

import com.termux.api.TermuxAPIConstants;
import com.termux.api.util.ResultReturner;

import java.util.ArrayList;

public class TermuxApiPermissionActivity extends Activity {

    private static final String LOG_TAG = "TermuxApiPermissionActivity";

    /**
     * Intent extra containing the permissions to request.
     */
    public static final String PERMISSIONS_EXTRA = TermuxAPIConstants.TERMUX_API_PACKAGE_NAME + ".permission_extra";

    /**
     * Check for and request permissions if necessary.
     *
     * @return if all permissions were already granted
     */
    public static boolean checkAndRequestPermission(Context context, Intent intent, String... permissions) {
        var permissionsToRequest = new ArrayList<String>();
        for (var permission : permissions) {
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            return true;
        } else {
            ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    String errorMessage = "Please grant the following permission"
                            + (permissionsToRequest.size() > 1 ? "s" : "")
                            + " (at Settings > Apps > Termux:API > Permissions) to use this command: "
                            + TextUtils.join(", ", permissionsToRequest);
                    out.beginObject().name("error").value(errorMessage).endObject();
                }
            });

            var startIntent = new Intent(context, TermuxApiPermissionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putStringArrayListExtra(TermuxApiPermissionActivity.PERMISSIONS_EXTRA, permissionsToRequest);
            try {
                context.startActivity(startIntent);
            } catch (Exception e) {
                Log.e(TermuxAPIConstants.LOG_TAG, "Unable to start permission activity", e);
            }
            return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(LOG_TAG, "onNewIntent");
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(LOG_TAG, "onResume");
        var permissionValues = getIntent().getStringArrayListExtra(PERMISSIONS_EXTRA);
        if (permissionValues != null) {
            requestPermissions(permissionValues.toArray(new String[0]), 0);
        }
        finish();
    }

}
