package com.termux.widget;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class TermuxWidgetPathLister {

    public static void listPaths(Consumer<String> onFile) {
        var visited = new HashSet<String>();
        for (var parentPath : new String[]{
            TermuxWidgetConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH,
            TermuxWidgetConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH_LEGACY,
        }) {
            var parentDir = new File(parentPath);
            listPathsInternal(parentDir, visited, onFile);

            if (!parentDir.exists()) {
                // Try to create an empty directory where the user should place files.
                if (!parentDir.mkdirs()) {
                    Log.w(TermuxWidgetConstants.LOG_TAG, "Unable to create directory: " + parentPath);
                }
            }
        }
    }

    private static void listPathsInternal(File directory, Set<String> visited, Consumer<String> onFile) {
        try {
            var canonicalPath = directory.getCanonicalPath();
            if (!visited.add(canonicalPath)) {
                Log.w(TermuxWidgetConstants.LOG_TAG, "Avoiding visiting directory again: " + directory.getAbsolutePath());
                return;
            }
        } catch (IOException e) {
            Log.e(TermuxWidgetConstants.LOG_TAG, "Exception resolving canonical path for: " + directory.getAbsolutePath(), e);
            return;
        }

        var children = directory.listFiles();

        if (children == null) {
            return;
        }

        for (var child : children) {
            if (child.isFile()) {
                if (!child.canExecute()) {
                    if (!child.setExecutable(true)) {
                        Log.e(TermuxWidgetConstants.LOG_TAG, "Unable to set file to executable: " + child.getAbsolutePath());
                    }
                }
                onFile.accept(child.getAbsolutePath());
            } else if (child.isDirectory()) {
                listPathsInternal(child, visited, onFile);
            }
        }
    }

}
