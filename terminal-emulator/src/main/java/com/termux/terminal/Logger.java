package com.termux.terminal;

import android.util.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Logger {

    public static void logStackTraceWithMessage(TerminalSessionClient client, String tag, String message, Throwable throwable) {
        Log.e(tag, getMessageAndStackTraceString(message, throwable));
    }

    public static String getMessageAndStackTraceString(String message, Throwable throwable) {
        if (message == null && throwable == null)
            return null;
        else if (message != null && throwable != null)
            return message + ":\n" + getStackTraceString(throwable);
        else if (throwable == null)
            return message;
        else
            return getStackTraceString(throwable);
    }

    public static String getStackTraceString(Throwable throwable) {
        if (throwable == null) return null;

        String stackTraceString = null;

        try {
            StringWriter errors = new StringWriter();
            PrintWriter pw = new PrintWriter(errors);
            throwable.printStackTrace(pw);
            pw.close();
            stackTraceString = errors.toString();
            errors.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stackTraceString;
    }

}
