package com.termux.app.api;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.termux.app.TermuxConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class TermuxApiHandler {

    /**
     * An extra intent parameter which specifies a linux abstract namespace socket address where output from the API
     * call should be written.
     */
    private static final String SOCKET_OUTPUT_EXTRA = "socket_output";

    /**
     * An extra intent parameter which specifies a linux abstract namespace socket address where input to the API call
     * can be read from.
     */
    private static final String SOCKET_INPUT_EXTRA = "socket_input";

    public static void handleApiIntent(Context context, Intent intent) {
        String apiMethod = intent.getStringExtra("api_method");
        if (apiMethod == null) {
            Log.e(TermuxConstants.LOG_TAG, "Missing 'api_method' extra");
            return;
        }

        switch (apiMethod) {
            case "Clipboard":
                ClipboardApi.onReceive(context, intent);
                break;
            case "SAF":
                SAFAPI.onReceive(context, intent);
                break;
            default:
                Log.e(TermuxConstants.LOG_TAG, "Unhandled 'api_method' extra: " + apiMethod);
                break;
        }
    }

}
