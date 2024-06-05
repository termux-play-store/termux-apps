package com.termux.app;

import android.annotation.SuppressLint;

public class TermuxConstants {

    public static final String LOG_TAG = "termux";

    @SuppressLint("SdCardPath")
    public static final String FILES_PATH = "/data/data/com.termux/files";
    public static final String PREFIX_PATH = FILES_PATH + "/usr";
    public static final String BIN_PATH = PREFIX_PATH + "/bin";
    public static final String HOME_PATH = FILES_PATH + "/home";

    public static final String FONT_PATH = TermuxConstants.HOME_PATH + "/.termux/font.ttf";
    public static final String COLORS_PATH = TermuxConstants.HOME_PATH + "/.termux/colors.properties";

    public static final int TERMUX_APP_NOTIFICATION_ID = 1337;

    public static final String TERMUX_INTERNAL_ACTIVITY = "com.termux.app.TermuxActivityInternal";

}
