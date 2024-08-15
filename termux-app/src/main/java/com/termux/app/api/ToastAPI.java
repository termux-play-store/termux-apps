package com.termux.app.api;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.PrintWriter;

public class ToastAPI {

    public static void onReceive(final Context context, Intent intent) {
        final int durationExtra = intent.getBooleanExtra("short", false) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;

        ResultReturner.returnData(intent, new ResultReturner.WithStringInput() {
            @Override
            public void writeResult(PrintWriter out) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    var toast = Toast.makeText(context, inputString, durationExtra);
                    toast.show();
                });
            }
        });
    }

}
