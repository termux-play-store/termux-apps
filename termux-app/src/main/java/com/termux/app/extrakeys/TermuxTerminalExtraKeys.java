package com.termux.app.extrakeys;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxConstants;
import com.termux.app.TermuxProperties;
import com.termux.app.TermuxTerminalSessionActivityClient;
import com.termux.app.TermuxTerminalViewClient;
import com.termux.view.TerminalView;

import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;

public final class TermuxTerminalExtraKeys {
    public static final Map<String, Integer> PRIMARY_KEY_CODES_FOR_STRINGS = new HashMap<>() {{
        put("SPACE", KeyEvent.KEYCODE_SPACE);
        put("ESC", KeyEvent.KEYCODE_ESCAPE);
        put("TAB", KeyEvent.KEYCODE_TAB);
        put("HOME", KeyEvent.KEYCODE_MOVE_HOME);
        put("END", KeyEvent.KEYCODE_MOVE_END);
        put("PGUP", KeyEvent.KEYCODE_PAGE_UP);
        put("PGDN", KeyEvent.KEYCODE_PAGE_DOWN);
        put("INS", KeyEvent.KEYCODE_INSERT);
        put("DEL", KeyEvent.KEYCODE_FORWARD_DEL);
        put("BKSP", KeyEvent.KEYCODE_DEL);
        put("UP", KeyEvent.KEYCODE_DPAD_UP);
        put("LEFT", KeyEvent.KEYCODE_DPAD_LEFT);
        put("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT);
        put("DOWN", KeyEvent.KEYCODE_DPAD_DOWN);
        put("ENTER", KeyEvent.KEYCODE_ENTER);
        put("F1", KeyEvent.KEYCODE_F1);
        put("F2", KeyEvent.KEYCODE_F2);
        put("F3", KeyEvent.KEYCODE_F3);
        put("F4", KeyEvent.KEYCODE_F4);
        put("F5", KeyEvent.KEYCODE_F5);
        put("F6", KeyEvent.KEYCODE_F6);
        put("F7", KeyEvent.KEYCODE_F7);
        put("F8", KeyEvent.KEYCODE_F8);
        put("F9", KeyEvent.KEYCODE_F9);
        put("F10", KeyEvent.KEYCODE_F10);
        put("F11", KeyEvent.KEYCODE_F11);
        put("F12", KeyEvent.KEYCODE_F12);
    }};

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        loadExtraKeysFromProperties();
    }


    /**
     * Set the terminal extra keys and style.
     */
    public void loadExtraKeysFromProperties() {
        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            String extrakeys = mActivity.mProperties.getExtraKeys();
            String extraKeysStyle = mActivity.mProperties.getExtraKeysStyle();

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !"default".equals(extraKeysStyle)) {
                mActivity.showTransientMessage("The style \"" + extraKeysStyle + "\" is invalid. Using default style instead.", true);
                extraKeysStyle = "default";
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Log.e(TermuxConstants.LOG_TAG, "Cannot load extra keys JSON", e);
            mActivity.showTransientMessage("Could not load and set the extra keys property from the properties file: " + e, true);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(TermuxProperties.EXTRA_KEYS_DEFAULT,
                    TermuxProperties.EXTRA_KEYS_STYLE_DEFAULT,
                    ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                // We should be able to handle our own defaults!
                throw new RuntimeException(e2);
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @SuppressLint("RtlHardcoded")
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            if (PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(key)) {
                Integer keyCode = PRIMARY_KEY_CODES_FOR_STRINGS.get(key);
                if (keyCode == null) return;
                int metaState = 0;
                if (ctrlDown) metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
                if (altDown) metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
                if (shiftDown) metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
                if (fnDown) metaState |= KeyEvent.META_FUNCTION_ON;

                KeyEvent keyEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState);
                mActivity.getTerminalView().onKeyDown(keyCode, keyEvent);
            } else {
                // not a control char
                key.codePoints().forEach(codePoint -> mActivity.getTerminalView().inputCodePoint(KeyCharacterMap.VIRTUAL_KEYBOARD, codePoint, ctrlDown, altDown));
            }
        }
    }

    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (buttonInfo.isMacro()) {
            String[] keys = buttonInfo.getKey().split(" ");
            boolean ctrlDown = false;
            boolean altDown = false;
            boolean shiftDown = false;
            boolean fnDown = false;
            for (String key : keys) {
                if (SpecialButton.CTRL.getKey().equals(key)) {
                    ctrlDown = true;
                } else if (SpecialButton.ALT.getKey().equals(key)) {
                    altDown = true;
                } else if (SpecialButton.SHIFT.getKey().equals(key)) {
                    shiftDown = true;
                } else if (SpecialButton.FN.getKey().equals(key)) {
                    fnDown = true;
                } else {
                    onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
                    ctrlDown = false; altDown = false; shiftDown = false; fnDown = false;
                }
            }
        } else {
            onTerminalExtraKeyButtonClick(view, buttonInfo.getKey(), false, false, false, false);
        }
    }

    public boolean performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        return false;
    }
}
