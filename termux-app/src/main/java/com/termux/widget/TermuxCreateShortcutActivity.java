package com.termux.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ShortcutManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.termux.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TermuxCreateShortcutActivity extends Activity {

    private ListView mListView;
    private final List<TermuxWidgetShortcutFile> mAllFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shortcuts_listview);
        mListView = findViewById(R.id.list);

        mListView.setOnApplyWindowInsetsListener((v, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });

        TermuxWidgetPathLister.listPaths(path -> mAllFiles.add(new TermuxWidgetShortcutFile(new File(path))));
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
        TermuxWidgetShortcutFile.sort(mAllFiles);

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

    private void createShortcut(Context context, TermuxWidgetShortcutFile shortcutFile) {
        var shortcutManager = context.getSystemService(ShortcutManager.class);

        if (shortcutManager.isRequestPinShortcutSupported()) {
            shortcutManager.requestPinShortcut(shortcutFile.getShortcutInfo(context), null);
        } else {
            Log.w(TermuxWidgetConstants.LOG_TAG, "Pinned shortcuts not supported");
            Toast.makeText(this, R.string.msg_pinned_shortcuts_not_supported, Toast.LENGTH_SHORT).show();
        }
    }

}
