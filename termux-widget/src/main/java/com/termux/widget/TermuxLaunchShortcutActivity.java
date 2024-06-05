package com.termux.widget;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.UUID;

/**
 * An activity to launch a shortcut. We want to a launch a service directly, but a shortcut
 * cannot be used to launch a service, only activities, so have to go through this activity.
 */
public class TermuxLaunchShortcutActivity extends Activity {

    private static final String LOG_TAG = "TermuxLaunchShortcutActivity";

    static final String TOKEN_NAME = "com.termux.shortcut.token";

    public static String getGeneratedToken(Context context) {
        var prefs = context.getSharedPreferences("token", Context.MODE_PRIVATE);
        var token = prefs.getString("token", null);
        if (token == null) {
            token = UUID.randomUUID().toString();
            prefs.edit().putString("token", token).apply();
        }
        return token;
    }

    @Override
    protected void onResume() {
        super.onResume();

        var intent = getIntent();
        var token = intent.getStringExtra(TOKEN_NAME);
        if (token == null || !token.equals(getGeneratedToken(this))) {
            Log.w("termux", "Strange token: " + token);
            Toast.makeText(this, R.string.msg_bad_token, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        //TermuxWidgetProvider.handleTermuxShortcutExecutionIntent(this, getIntent(), LOG_TAG);

        finish();
    }

}
