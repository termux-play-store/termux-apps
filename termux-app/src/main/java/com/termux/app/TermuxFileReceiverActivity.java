package com.termux.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class TermuxFileReceiverActivity extends AppCompatActivity {

    static final String TERMUX_RECEIVEDIR = TermuxConstants.HOME_PATH + "/downloads";
    static final String EDITOR_PROGRAM = TermuxConstants.HOME_PATH + "/bin/termux-file-editor";
    static final String URL_OPENER_PROGRAM = TermuxConstants.HOME_PATH + "/bin/termux-url-opener";

    /**
     * If the activity should be finished when the name input dialog is dismissed. This is disabled
     * before showing an error dialog, since the act of showing the error dialog will cause the
     * name input dialog to be implicitly dismissed, and we do not want to finish the activity directly
     * when showing the error dialog.
     */
    boolean mFinishOnDismissNameDialog = true;

    private static final String LOG_TAG = "FileReceiverActivity";

    static boolean isSharedTextAnUrl(String sharedText) {
        return Patterns.WEB_URL.matcher(sharedText).matches()
            || Pattern.matches("magnet:\\?xt=urn:btih:.*?", sharedText);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();
        final String scheme = intent.getScheme();

        final String sharedTitle = intent.getStringExtra(Intent.EXTRA_TITLE);

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            final String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            final Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            if (sharedUri != null) {
                handleContentUri(sharedUri, sharedTitle);
            } else if (sharedText != null) {
                if (isSharedTextAnUrl(sharedText)) {
                    handleUrlAndFinish(sharedText);
                } else {
                    String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                    if (subject == null) subject = sharedTitle;
                    if (subject != null) subject += ".txt";
                    promptNameAndSave(new ByteArrayInputStream(sharedText.getBytes(StandardCharsets.UTF_8)), subject);
                }
            } else {
                showErrorDialogAndQuit("Send action without content - nothing to save.");
            }
        } else {
            Uri dataUri = intent.getData();

            if (dataUri == null) {
                showErrorDialogAndQuit("Data uri not passed.");
                return;
            }

            if ("content".equals(scheme)) {
                handleContentUri(dataUri, sharedTitle);
            } else if ("file".equals(scheme)) {
                Log.v(LOG_TAG, "uri: \"" + dataUri + "\", path: \"" + dataUri.getPath() + "\", fragment: \"" + dataUri.getFragment() + "\"");

                String path = dataUri.getPath();
                if (path == null || path.isEmpty()) {
                    showErrorDialogAndQuit("File path from data uri is null, empty or invalid.");
                    return;
                }

                File file = new File(path);
                try {
                    FileInputStream in = new FileInputStream(file);
                    promptNameAndSave(in, file.getName());
                } catch (FileNotFoundException e) {
                    showErrorDialogAndQuit("Cannot open file: " + e.getMessage() + ".");
                }
            } else {
                showErrorDialogAndQuit("Unable to receive any file or URL.");
            }
        }
    }

    void showErrorDialogAndQuit(String message) {
        mFinishOnDismissNameDialog = false;
        TermuxMessageDialogUtils.showMessage(this, "Termux", message, null, (dialog, which) -> finish(), null, null, dialog -> finish());
    }

    void handleContentUri(@NonNull final Uri uri, String subjectFromIntent) {
        try {
            Log.v(LOG_TAG, "uri: \"" + uri + "\", path: \"" + uri.getPath() + "\", fragment: \"" + uri.getFragment() + "\"");
            String attachmentFileName = null;
            String[] projection = new String[]{OpenableColumns.DISPLAY_NAME};
            try (Cursor c = getContentResolver().query(uri, projection, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    final int fileNameColumnId = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (fileNameColumnId >= 0) attachmentFileName = c.getString(fileNameColumnId);
                }
            }

            if (attachmentFileName == null) {
                attachmentFileName = subjectFromIntent;
            }
            if (attachmentFileName == null) {
                attachmentFileName = new File(uri.getPath()).getName();
            }

            InputStream in = getContentResolver().openInputStream(uri);
            promptNameAndSave(in, attachmentFileName);
        } catch (Exception e) {
            showErrorDialogAndQuit("Unable to handle shared content:\n\n" + e.getMessage());
            Log.e(LOG_TAG, "handleContentUri(uri=" + uri + ") failed", e);
        }
    }

    void promptNameAndSave(final InputStream in, final String attachmentFileName) {
        TermuxMessageDialogUtils.textInput(this,
            R.string.title_file_received,
            R.string.hint_file_name,
            attachmentFileName,
            R.string.action_file_received_edit,
            text -> {
                File outFile = saveStreamWithName(in, text);
                if (outFile == null) return;

                final File editorProgramFile = new File(EDITOR_PROGRAM);
                if (!editorProgramFile.isFile()) {
                    showErrorDialogAndQuit("The following file does not exist:\n$HOME/bin/termux-file-editor\n\n"
                        + "Create this file as a script or a symlink - it will be called with the received file as only argument.");
                    return;
                }

                // Do this for the user if necessary:
                //noinspection ResultOfMethodCallIgnored
                editorProgramFile.setExecutable(true);

                final Uri scriptUri = new Uri.Builder().scheme("file").path(EDITOR_PROGRAM).build();

                Intent executeIntent = new Intent(TermuxService.ACTION_SERVICE_EXECUTE, scriptUri);
                executeIntent.setClass(TermuxFileReceiverActivity.this, TermuxService.class);
                executeIntent.putExtra(TermuxService.TERMUX_EXECUTE_EXTRA_ARGUMENTS, new String[]{outFile.getAbsolutePath()});
                startService(executeIntent);
                finish();
            },
            R.string.action_file_received_open_directory,
            text -> {
                if (saveStreamWithName(in, text) == null) return;

                Intent executeIntent = new Intent(TermuxService.ACTION_SERVICE_EXECUTE);
                executeIntent.putExtra(TermuxService.TERMUX_EXECUTE_WORKDIR, TERMUX_RECEIVEDIR);
                executeIntent.setClass(TermuxFileReceiverActivity.this, TermuxService.class);
                startService(executeIntent);
                finish();
            },
            R.string.cancel,
            text -> finish(), dialog -> {
                if (mFinishOnDismissNameDialog) finish();
            });
    }

    public File saveStreamWithName(InputStream in, String attachmentFileName) {
        File receiveDir = new File(TERMUX_RECEIVEDIR);

        if (attachmentFileName == null || attachmentFileName.isEmpty()) {
            showErrorDialogAndQuit("File name cannot be null or empty");
            return null;
        }

        if (!receiveDir.isDirectory() && !receiveDir.mkdirs()) {
            showErrorDialogAndQuit("Cannot create directory: " + receiveDir.getAbsolutePath());
            return null;
        }

        try {
            final File outFile = new File(receiveDir, attachmentFileName);
            try (FileOutputStream f = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                int readBytes;
                while ((readBytes = in.read(buffer)) > 0) {
                    f.write(buffer, 0, readBytes);
                }
            }
            return outFile;
        } catch (IOException e) {
            showErrorDialogAndQuit("Error saving file:\n\n" + e);
            Log.e(LOG_TAG, "Error saving file", e);
            return null;
        }
    }

    void handleUrlAndFinish(final String url) {
        final File urlOpenerProgramFile = new File(URL_OPENER_PROGRAM);
        if (!urlOpenerProgramFile.isFile()) {
            showErrorDialogAndQuit("The following file does not exist:\n$HOME/bin/termux-url-opener\n\n"
                + "Create this file as a script or a symlink - it will be called with the shared URL as the first argument.");
            return;
        }

        // Do this for the user if necessary:
        //noinspection ResultOfMethodCallIgnored
        urlOpenerProgramFile.setExecutable(true);

        final Uri urlOpenerProgramUri = new Uri.Builder().scheme("file").path(URL_OPENER_PROGRAM).build();

        Intent executeIntent = new Intent(TermuxService.ACTION_SERVICE_EXECUTE, urlOpenerProgramUri);
        executeIntent.setClass(TermuxFileReceiverActivity.this, TermuxService.class);
        executeIntent.putExtra(TermuxService.TERMUX_EXECUTE_EXTRA_ARGUMENTS, new String[]{url});
        startService(executeIntent);
        finish();
    }

}
