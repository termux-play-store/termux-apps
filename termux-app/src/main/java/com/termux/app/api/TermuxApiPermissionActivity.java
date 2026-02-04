package com.termux.app.api;

import android.app.Activity;
import android.content.Intent;

import com.termux.app.TermuxConstants;

public class TermuxApiPermissionActivity extends Activity {

    /**
     * Intent extra containing the permissions to request.
     */
    public static final String PERMISSIONS_EXTRA = TermuxConstants.PACKAGE_NAME + ".permission_extra";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        var permissionValues = getIntent().getStringArrayListExtra(PERMISSIONS_EXTRA);
        if (permissionValues != null) {
            requestPermissions(permissionValues.toArray(new String[0]), 0);
        }
        finish();
    }

}
