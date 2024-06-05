package com.termux.widget;

import android.annotation.SuppressLint;

public class TermuxWidgetConstants {

    public static final String LOG_TAG = "termux";

    public static final String ACTION_REFRESH_WIDGET = "com.termux.widget.ACTION_REFRESH_WIDGET";

    public static final String ACTION_WIDGET_ITEM_CLICKED = "com.termux.widget.ACTION_WIDGET_ITEM_CLICKED";

    public static final String EXTRA_FILE_CLICKED = "com.termux.widget.EXTRA_FILE_CLICKED";

    public static final String EXTRA_IS_TASK = "com.termux.widget.EXTRA_IS_TASK";

    public static final String SHORTCUTS_DIR_NAME = ".shortcuts";

    public static final String TASKS_DIR_NAME = "tasks";

    @SuppressLint("SdCardPath")
    public static final String TERMUX_SHORTCUT_SCRIPTS_DIR_PATH = "/data/data/com.termux/files/home/" + SHORTCUTS_DIR_NAME;

}
