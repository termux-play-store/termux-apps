package com.termux.app;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

public final class TermuxProperties {

    private final Properties properties = new Properties();

    public static final String EXTRA_KEYS_DEFAULT = "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]";
    public static final String EXTRA_KEYS_STYLE_DEFAULT = "default";

    void reloadProperties(TermuxActivity activity) {
        properties.clear();
        try {
            for (String subPath : new String[]{".termux/termux.properties", ".config/termux/termux.properties"}) {
                File propertiesFile = new File(TermuxConstants.HOME_PATH + '/' + subPath);
                if (propertiesFile.isFile()) {
                    try (FileInputStream in = new FileInputStream(propertiesFile)) {
                        try {
                            properties.load(in);
                        } catch (Exception e) {
                            Log.e(TermuxConstants.LOG_TAG, "Error reading termux properties", e);
                            activity.showTransientMessage("Cannot read termux.properties - check syntax", true);
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
        return properties.getProperty("extra-keys", EXTRA_KEYS_DEFAULT);
    }

    public String getExtraKeysStyle() {
        return properties.getProperty("extra-keys-style", EXTRA_KEYS_STYLE_DEFAULT);
    }

    public enum BellBehaviour {
        VIBRATE, BEEP, IGNORE
    }

    public BellBehaviour getBellBehaviour() {
        var prop = properties.getProperty("bell-character", "vibrate").trim().toLowerCase(Locale.ROOT);
        return switch (prop) {
            case "vibrate" -> BellBehaviour.VIBRATE;
            case "beep" -> BellBehaviour.BEEP;
            case "ignore" -> BellBehaviour.IGNORE;
            default -> {
                Log.w(TermuxConstants.LOG_TAG, "Invalid 'bell-character' value: '" + prop + "'");
                yield BellBehaviour.VIBRATE;
            }
        };
    }

}
