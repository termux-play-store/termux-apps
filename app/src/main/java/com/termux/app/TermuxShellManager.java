package com.termux.app;

import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class TermuxShellManager {

    private static int SHELL_ID = 0;

    protected final Context mContext;

    /**
     * The foreground TermuxSessions which this service manages.
     * Note that this list is observed by an activity, like TermuxActivity.mTermuxSessionListViewController,
     * so any changes must be made on the UI thread and followed by a call to
     * {@link ArrayAdapter#notifyDataSetChanged()}.
     */
    public final List<TermuxSession> mTermuxSessions = new ArrayList<>();

    /**
     * The background TermuxTasks which this service manages.
     */
    public final List<TermuxAppShell> mTermuxTasks = new ArrayList<>();

    public TermuxShellManager(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized int getNextShellId() {
        return SHELL_ID++;
    }
}
