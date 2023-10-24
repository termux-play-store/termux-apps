package com.termux.api;

import android.app.Application;

public class TermuxAPIApplication extends Application {

    public void onCreate() {
        super.onCreate();
        SocketListener.createSocketListener(this);
    }

}
