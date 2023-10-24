package com.termux.app;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class TermuxProperties {

    private final Properties properties = new Properties();

    void reloadProperties() {
        properties.clear();
        try {
            for (String subPath : new String[]{".termux.properties", ".config/termux.properties"}) {
                File propertiesFile = new File(TermuxConstants.HOME_PATH + '/' + subPath);
                if (propertiesFile.exists()) {
                    try (FileInputStream in = new FileInputStream(propertiesFile)) {
                        try {
                            properties.load(in);
                        } catch (Exception e) {
                            Log.e(TermuxConstants.LOG_TAG, "Error reading termux properties", e);
                            // TODO: Show toast
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TermuxConstants.LOG_TAG, "Failed to reload properties", e);
        }
    }

    boolean isBackKeyTheEscapeKey() {
        return properties.getProperty("back-key", "back").equalsIgnoreCase("escape");
    }

    boolean isEnforcingCharBasedInput() {
        return properties.getProperty("enforce-char-based-input", "false").equalsIgnoreCase("true");
    }

    boolean areVirtualVolumeKeysDisabled() {
        return properties.getProperty("volume-keys", "normal").equalsIgnoreCase("volume");
    }

    public String getExtraKeys() {
        String defaultValue = "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]";
        return properties.getProperty("extra-keys", defaultValue);
    }

    public String getExtraKeysStyle() {
        return properties.getProperty("extra-keys-style", "default");
    }
}
