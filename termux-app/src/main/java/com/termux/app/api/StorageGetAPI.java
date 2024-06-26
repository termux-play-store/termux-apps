package com.termux.app.api;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.FileUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;

public class StorageGetAPI {

    private static final String FILE_EXTRA = "com.termux.storage.file";

    public static void onReceive(final Context context, final Intent intent) {
        ResultReturner.returnData(intent, out -> {
            var fileExtra = intent.getStringExtra("file");
            if (fileExtra == null || fileExtra.isEmpty()) {
                out.println("ERROR: File path not passed");
                return;
            }

            var storageActivityIntent = new Intent(context, StorageActivity.class)
                .putExtra(FILE_EXTRA, fileExtra)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(storageActivityIntent);
        });
    }

    public static class StorageActivity extends Activity {

        private String outputFile;

        private static final String LOG_TAG = "StorageActivity";

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onResume() {
            super.onResume();
            outputFile = getIntent().getStringExtra(FILE_EXTRA);

            // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
            var openDocumentIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");

            startActivityForResult(openDocumentIntent, 42);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
            super.onActivityResult(requestCode, resultCode, resultData);

            Log.v(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + resultData);

            if (resultCode == RESULT_OK) {
                var data = resultData.getData();
                if (data == null) {
                    Log.e(LOG_TAG, "No result data uri");
                } else {
                    try {
                        try (var in = getContentResolver().openInputStream(data)) {
                            if (in == null) {
                                Log.e(LOG_TAG, "Unable to open content uri: " + data);
                            } else {
                                try (var out = new FileOutputStream(outputFile)) {
                                    FileUtils.copy(in, out);
                                }
                            }
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error copying " + data + " to " + outputFile, e);
                    }
                }
            }
            finish();
        }

    }

}
