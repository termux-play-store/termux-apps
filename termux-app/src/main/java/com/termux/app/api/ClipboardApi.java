package com.termux.app.api;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.io.PrintWriter;

public class ClipboardApi {

    public static void onReceive(final Context context, Intent intent) {
        var clipboard = context.getSystemService(ClipboardManager.class);
        var clipData = clipboard.getPrimaryClip();

        boolean set = intent.getBooleanExtra("set", false);
        if (set) {
            ResultReturner.returnData(intent, new ResultReturner.WithStringInput() {
                @Override
                protected boolean trimInput() {
                    return false;
                }

                @Override
                public void writeResult(PrintWriter out) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", inputString));
                }
            });
        } else {
            ResultReturner.returnData(intent, out -> {
                if (clipData == null) {
                    out.print("");
                } else {
                    int itemCount = clipData.getItemCount();
                    for (int i = 0; i < itemCount; i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        CharSequence text = item.coerceToText(context);
                        if (!TextUtils.isEmpty(text)) {
                            out.print(text);
                        }
                    }
                }
            });
        }
    }
}
