package com.termux.api;

import android.app.Application;
import android.content.Context;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;


public class TermuxAPIApplication extends Application {

    public void onCreate() {
        super.onCreate();
        // Set crash handler for the app
        //ResultReturner.setContext(this);
        SocketListener.createSocketListener(this);
    }

}
