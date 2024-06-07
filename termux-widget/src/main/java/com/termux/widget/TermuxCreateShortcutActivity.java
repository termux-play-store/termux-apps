package com.termux.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ShortcutManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class TermuxCreateShortcutActivity extends Activity {

    private ListView mListView;
    private final List<ShortcutFile> mAllFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shortcuts_listview);
        mListView = findViewById(R.id.list);

        TermuxPathLister.listPaths(this, TermuxWidgetConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH, path -> {
            mAllFiles.add(new ShortcutFile(new File(path)));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateListview();

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            var context = TermuxCreateShortcutActivity.this;
            var clickedFile = mAllFiles.get(position);
            createShortcut(context, clickedFile);
            finish();
        });
    }

    private void updateListview() {
        ShortcutFile.sort(mAllFiles);

        if (mAllFiles.isEmpty()) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.msg_no_shortcut_scripts)
                .setOnDismissListener(dialog -> finish()).show();
            return;
        }

        final String[] values = new String[mAllFiles.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = mAllFiles.get(i).mLabel;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, values);
        mListView.setAdapter(adapter);
    }

    private void createShortcut(Context context, ShortcutFile shortcutFile) {
        var shortcutManager = context.getSystemService(ShortcutManager.class);

        if (shortcutManager.isRequestPinShortcutSupported()) {
            shortcutManager.requestPinShortcut(shortcutFile.getShortcutInfo(context), null);
        } else {
            Log.w(TermuxWidgetConstants.LOG_TAG, "Pinned shortcuts not supported");
            Toast.makeText(this, R.string.msg_pinned_shortcuts_not_supported, Toast.LENGTH_SHORT).show();
        }
    }

}
