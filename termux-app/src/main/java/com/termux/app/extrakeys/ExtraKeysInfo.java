package com.termux.app.extrakeys;

import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Defines the info needed by {@link ExtraKeysView} to display the extra key views.
 *
 * The {@code propertiesInfo} passed to the constructors of this class must be json array of arrays.
 * Each array element of the json array will be considered a separate row of keys.
 * Each key can either be simple string that defines the name of the key or a json dict that defines
 * advance info for the key. The syntax can be `'KEY'` or `{key: 'KEY'}`.
 * For example `HOME` or `{key: 'HOME', ...}.
 *
 * In advance json dict mode, the key can also be a sequence of space separated keys instead of one
 * key. This can be done by replacing `key` key/value pair of the dict with a `macro` key/value pair.
 * The syntax is `{macro: 'KEY COMBINATION'}`. For example {macro: 'HOME RIGHT', ...}.
 *
 * In advance json dict mode, you can define a nested json dict with the `popup` key which will be
 * used as the popup key and will be triggered on swipe up. The syntax can be
 * `{key: 'KEY', popup: 'POPUP_KEY'}` or `{key: 'KEY', popup: {macro: 'KEY COMBINATION', display: 'Key combo'}}`.
 * For example `{key: 'HOME', popup: {KEY: 'END', ...}, ...}`.
 *
 * In advance json dict mode, the key can also have a custom display name that can be used as the
 * text to display on the button by defining the `display` key. The syntax is `{display: 'DISPLAY'}`.
 * For example {display: 'Custom name', ...}.
 *
 * Examples:
 * <pre>{@code
 * # Empty:
 * []
 *
 * # Single row:
 * [[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]
 *
 * # 2 row:
 * [['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'],
 * ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]
 *
 * # Advance:
 * [[
 *   {key: ESC, popup: {macro: "CTRL f d", display: "tmux exit"}},
 *   {key: CTRL, popup: {macro: "CTRL f BKSP", display: "tmux ←"}},
 *   {key: ALT, popup: {macro: "CTRL f TAB", display: "tmux →"}},
 *   {key: TAB, popup: {macro: "ALT a", display: A-a}},
 *   {key: LEFT, popup: HOME},
 *   {key: DOWN, popup: PGDN},
 *   {key: UP, popup: PGUP},
 *   {key: RIGHT, popup: END},
 *   {macro: "ALT j", display: A-j, popup: {macro: "ALT g", display: A-g}},
 *   {key: KEYBOARD, popup: {macro: "CTRL d", display: exit}}
 * ]]
 *
 * }</pre>
 *
 * Aliases are also allowed for the keys that you can pass as {@code extraKeyAliasMap}. Check
 * {@link ExtraKeysConstants#CONTROL_CHARS_ALIASES}.
 *
 * Its up to the {@link ExtraKeysView.IExtraKeysView} client on how to handle individual key values
 * of an {@link ExtraKeyButton}. They are sent as is via
 * {@link ExtraKeysView.IExtraKeysView#onExtraKeyButtonClick(View, ExtraKeyButton, MaterialButton)}. The
 * {@link TerminalExtraKeys} which is an implementation of the interface,
 * checks if the key is one of {@link ExtraKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS} and generates
 * a {@link android.view.KeyEvent} for it, and if its not, then converts the key to code points by
 * calling {@link CharSequence#codePoints()} and passes them to the terminal as literal strings.
 *
 * Examples:
 * {@code
 * "ENTER" will trigger the ENTER keycode
 * "LEFT" will trigger the LEFT keycode and be displayed as "←"
 * "→" will input a "→" character
 * "−" will input a "−" character
 * "-_-" will input the string "-_-"
 * }
 *
 * For more info, check https://wiki.termux.com/wiki/Touch_Keyboard.
 */
public final class ExtraKeysInfo {

    /**
     * Matrix of buttons to be displayed in {@link ExtraKeysView}.
     */
    private final ExtraKeyButton[][] mButtons;

    public ExtraKeysInfo(@NonNull String propertiesInfo,
                         String style,
                         @NonNull ExtraKeysConstants.ExtraKeyDisplayMap extraKeyAliasMap) throws JSONException {
        var extraKeyDisplayMap = getCharDisplayMapForStyle(style);
        // Convert String propertiesInfo to Array of Arrays
        var arr = new JSONArray(propertiesInfo);
        Object[][] matrix = new Object[arr.length()][];
        for (int i = 0; i < arr.length(); i++) {
            JSONArray line = arr.getJSONArray(i);
            matrix[i] = new Object[line.length()];
            for (int j = 0; j < line.length(); j++) {
                matrix[i][j] = line.get(j);
            }
        }

        // convert matrix to buttons
        mButtons = new ExtraKeyButton[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            mButtons[i] = new ExtraKeyButton[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                Object key = matrix[i][j];

                JSONObject jobject = normalizeKeyConfig(key);

                ExtraKeyButton button;

                if (!jobject.has(ExtraKeyButton.KEY_POPUP)) {
                    // no popup
                    button = new ExtraKeyButton(jobject, extraKeyDisplayMap, extraKeyAliasMap);
                } else {
                    // a popup
                    JSONObject popupJobject = normalizeKeyConfig(jobject.get(ExtraKeyButton.KEY_POPUP));
                    ExtraKeyButton popup = new ExtraKeyButton(popupJobject, extraKeyDisplayMap, extraKeyAliasMap);
                    button = new ExtraKeyButton(jobject, popup, extraKeyDisplayMap, extraKeyAliasMap);
                }

                mButtons[i][j] = button;
            }
        }
    }

    /**
     * Convert "value" -> {"key": "value"}. Required by
     * {@link ExtraKeyButton#ExtraKeyButton(JSONObject, ExtraKeyButton, ExtraKeysConstants.ExtraKeyDisplayMap, ExtraKeysConstants.ExtraKeyDisplayMap)}.
     */
    private static JSONObject normalizeKeyConfig(Object key) throws JSONException {
        JSONObject jobject;
        if (key instanceof String) {
            jobject = new JSONObject();
            jobject.put(ExtraKeyButton.KEY_KEY_NAME, key);
        } else if (key instanceof JSONObject) {
            jobject = (JSONObject) key;
        } else {
            throw new JSONException("An key in the extra-key matrix must be a string or an object");
        }
        return jobject;
    }

    public ExtraKeyButton[][] getMatrix() {
        return mButtons;
    }

    @NonNull
    public static ExtraKeysConstants.ExtraKeyDisplayMap getCharDisplayMapForStyle(String style) {
        switch (style) {
            case "arrows-only":
                return ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.ARROWS_ONLY_CHAR_DISPLAY;
            case "arrows-all":
                return ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.LOTS_OF_ARROWS_CHAR_DISPLAY;
            case "all":
                return ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.FULL_ISO_CHAR_DISPLAY;
            case "none":
                return new ExtraKeysConstants.ExtraKeyDisplayMap();
            default:
                return ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY;
        }
    }

}
