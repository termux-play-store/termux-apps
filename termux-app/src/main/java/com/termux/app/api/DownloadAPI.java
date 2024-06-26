package com.termux.app.api;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

public class DownloadAPI {

    public static void onReceive(final Context context, final Intent intent) {
        ResultReturner.returnData(intent, out -> {
            var downloadUri = intent.getData();
            if (downloadUri == null) {
                out.println("No download URI specified");
                return;
            }

            var title = intent.getStringExtra("title");
            var description = intent.getStringExtra("description");
            var path = intent.getStringExtra("path");

            var manager = context.getSystemService(DownloadManager.class);
            var req = new Request(downloadUri)
                .setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            if (title != null) {
                req.setTitle(title);
            }

            if (description != null) {
                req.setDescription(description);
            }

            if (path != null) {
                req.setDestinationUri(Uri.fromFile(new File(path)));
            }

            manager.enqueue(req);
        });
    }
}
