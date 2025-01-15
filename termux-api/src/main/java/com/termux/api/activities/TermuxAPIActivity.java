package com.termux.api.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.api.R;

public class TermuxAPIActivity extends AppCompatActivity {

    private static final int REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION = 1;

    private TextView mBatteryOptimizationNotDisabledWarning;
    private TextView mDisplayOverOtherAppsPermissionNotGrantedWarning;

    private Button mDisableBatteryOptimization;
    private Button mGrantDisplayOverOtherAppsPermission;

    private static final String LOG_TAG = "TermuxAPIActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux_api);

        //AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        //AppCompatActivityUtils.setToolbarTitle(this, com.termux.shared.R.id.toolbar, TermuxConstants.TERMUX_API_APP_NAME, 0);

        TextView pluginInfo = findViewById(R.id.textview_plugin_info);
        //pluginInfo.setText(getString(R.string.plugin_info, TermuxConstants.TERMUX_GITHUB_REPO_URL, TermuxConstants.TERMUX_API_GITHUB_REPO_URL, TermuxConstants.TERMUX_API_APT_PACKAGE_NAME, TermuxConstants.TERMUX_API_APT_GITHUB_REPO_URL));
        mBatteryOptimizationNotDisabledWarning = findViewById(R.id.textview_battery_optimization_not_disabled_warning);
        mDisableBatteryOptimization = findViewById(R.id.btn_disable_battery_optimizations);
        mDisableBatteryOptimization.setOnClickListener(v -> requestDisableBatteryOptimizations());

        mDisplayOverOtherAppsPermissionNotGrantedWarning = findViewById(R.id.textview_display_over_other_apps_not_granted_warning);
        mGrantDisplayOverOtherAppsPermission = findViewById(R.id.btn_grant_display_over_other_apps_permission);
        mGrantDisplayOverOtherAppsPermission.setOnClickListener(v -> requestDisplayOverOtherAppsPermission());
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkIfBatteryOptimizationNotDisabled();
        checkIfDisplayOverOtherAppsPermissionNotGranted();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_termux_api, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_info) {
            showInfo();
            return true;
        } else if (id == R.id.menu_settings) {
            openSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showInfo() {
        new Thread(() -> {
            /*
            String title = "About";

            StringBuilder aboutString = new StringBuilder();
            aboutString.append(TermuxUtils.getAppInfoMarkdownString(TermuxAPIActivity.this, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGE));
            aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(TermuxAPIActivity.this));
            aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(TermuxAPIActivity.this));

            ReportInfo reportInfo = new ReportInfo(title, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
            reportInfo.setReportString(aboutString.toString());
            reportInfo.setReportSaveFileLabelAndPath(title, Environment.getExternalStorageDirectory() + "/" + FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + title + ".log", true, true));
            ReportActivity.startReportActivity(TermuxAPIActivity.this, reportInfo);
             */
        }).start();
    }

    private void checkIfBatteryOptimizationNotDisabled() {
        if (mBatteryOptimizationNotDisabledWarning == null) return;

        /*
        // If battery optimizations not disabled
        if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) {
            ViewUtils.setWarningTextViewAndButtonState(this, mBatteryOptimizationNotDisabledWarning, mDisableBatteryOptimization, true, getString(R.string.action_disable_battery_optimizations));
        } else {
            ViewUtils.setWarningTextViewAndButtonState(this, mBatteryOptimizationNotDisabledWarning,
                    mDisableBatteryOptimization, false, getString(R.string.action_already_disabled));
        }
         */
    }

    private void requestDisableBatteryOptimizations() {
        Log.d(LOG_TAG, "Requesting to disable battery optimizations");
        //PermissionUtils.requestDisableBatteryOptimizations(this, PermissionUtils.REQUEST_DISABLE_BATTERY_OPTIMIZATIONS);
    }



    private void checkIfDisplayOverOtherAppsPermissionNotGranted() {
        if (mDisplayOverOtherAppsPermissionNotGrantedWarning == null) return;

        // If display over other apps permission not granted
        /*
        if (!PermissionUtils.checkDisplayOverOtherAppsPermission(this)) {
            ViewUtils.setWarningTextViewAndButtonState(this, mDisplayOverOtherAppsPermissionNotGrantedWarning,
                    mGrantDisplayOverOtherAppsPermission, true, getString(R.string.action_grant_display_over_other_apps_permission));
        } else {
            ViewUtils.setWarningTextViewAndButtonState(this, mDisplayOverOtherAppsPermissionNotGrantedWarning,
                    mGrantDisplayOverOtherAppsPermission, false, getString(R.string.action_already_granted));
        }
         */
    }

    private void requestDisplayOverOtherAppsPermission() {
        Log.d(LOG_TAG, "Requesting to grant display over other apps permission");
        //PermissionUtils.requestDisplayOverOtherAppsPermission(this, PermissionUtils.REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /*
        switch (requestCode) {
            case PermissionUtils.REQUEST_DISABLE_BATTERY_OPTIMIZATIONS:
                if(PermissionUtils.checkIfBatteryOptimizationsDisabled(this))
                    Logger.logDebug(LOG_TAG, "Battery optimizations disabled by user on request.");
                else
                    Logger.logDebug(LOG_TAG, "Battery optimizations not disabled by user on request.");
                break;
            case PermissionUtils.REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION:
                if(PermissionUtils.checkDisplayOverOtherAppsPermission(this))
                    Log.d(LOG_TAG, "Display over other apps granted by user on request.");
                else
                    Log.d(LOG_TAG, "Display over other apps denied by user on request.");
                break;
            default:
                Log.d(LOG_TAG, "Unknown request code \"" + requestCode + "\" passed to onRequestPermissionsResult");
        }
         */
    }



    private void openSettings() {
        //startActivity(new Intent().setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME));
    }

}
