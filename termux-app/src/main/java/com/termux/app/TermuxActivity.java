package com.termux.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.extrakeys.ExtraKeysView;
import com.termux.app.extrakeys.TermuxTerminalExtraKeys;
import com.termux.app.extrakeys.TerminalToolbarViewPager;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    public static final String ACTION_RELOAD_STYLE = "com.termux.app.reload_style";
    public static final String ACTION_REQUEST_PERMISSIONS = "com.termux.app.request_storage_permissions";
    public static final String EXTRA_FAILSAFE_SESSION = "com.termux.app.failsafe_session";

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 2;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 3;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 4;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 5;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 6;
    private static final int CONTEXT_MENU_STYLING_ID = 7;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 8;
    private static final int CONTEXT_MENU_FULLSCREEN_ID = 9;
    private static final int CONTEXT_MENU_HELP_ID = 10;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final int REQUEST_CODE_TERMUX_STYLING = 1;

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     * The {@link TerminalViewClient} interface implementation to allow for communication between
     * {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     * The {@link TerminalSessionClient} interface implementation to allow for communication between
     * {@link TerminalSession} and {@link TermuxActivity}.
     */
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mIsVisible) {
                if (ACTION_RELOAD_STYLE.equals(intent.getAction())) {
                    if ("storage".equals(intent.getStringExtra(ACTION_RELOAD_STYLE))) {
                        TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                    } else {
                        reloadActivityStyling();
                    }
                }
            }
        }
    };

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showTransientMessage(String, boolean)}}.
     */
    Toast mLastToast;

    /**
     * If between onStart() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    private float mTerminalToolbarDefaultHeight;

    public final TermuxProperties mProperties = new TermuxProperties();

    public TermuxPreferences mPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            // mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);
        }

        mProperties.reloadProperties(this);
        mPreferences = new TermuxPreferences(this);

        setContentView(R.layout.activity_termux);

        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        mTerminalView.setTextSize(mPreferences.getFontSize());

        mTermuxTerminalSessionActivityClient.onCreate();

        setTerminalToolbarView(savedInstanceState);

        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null, null, null));
        newSessionButton.setOnLongClickListener(v -> {
            TermuxMessageDialogUtils.textInput(TermuxActivity.this,
                R.string.title_create_named_session,
                R.string.hint_session_name,
                null,
                R.string.action_create_named_session_confirm, sessionName -> mTermuxTerminalSessionActivityClient.addNewSession(false, sessionName, null, null),
                R.string.action_new_session_failsafe, sessionName -> mTermuxTerminalSessionActivityClient.addNewSession(true, sessionName, null, null),
                -1, null, null);
            return true;
        });
        View toggleKeyboardButton = findViewById(R.id.toggle_keyboard_button);
        toggleKeyboardButton.setOnClickListener(item -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });
        toggleKeyboardButton.setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });

        registerForContextMenu(mTerminalView);

        // Start the {@link TermuxService} and make it run regardless of who is bound to it
        var serviceIntent = new Intent(this, TermuxService.class);
        startForegroundService(serviceIntent);

        // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
        // callback if it succeeds.
        if (!bindService(serviceIntent, this, 0)) {
            throw new RuntimeException("bindService() failed");
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mTerminalView.isSelectingText()) {
                    mTerminalView.stopTextSelectionMode();
                } else if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
                    getDrawer().closeDrawers();
                } else {
                    getDrawer().openDrawer(Gravity.LEFT);
                }
            }
        });

        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
            var terminalToolbarViewPager = getTerminalToolbarViewPager();
            if (insets.isVisible(WindowInsets.Type.ime())) {
                if (mPreferences.isShowTerminalToolbar()) {
                    terminalToolbarViewPager.setVisibility(View.VISIBLE);
                }
            } else {
                terminalToolbarViewPager.setVisibility(View.GONE);
            }
            TermuxFullscreen.updatePadding(this, insets);
            return insets;
        });

        getDrawer().addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                findViewById(R.id.terminal_sessions_list).requestFocus();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                mTerminalView.requestFocus();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.i(TermuxConstants.LOG_TAG, "onActivityResult(request=" + requestCode + ", result=" + resultCode + ")");

        if (resultCode != Activity.RESULT_OK) {
            Log.e(TermuxConstants.LOG_TAG, "Failed activity result - request=" + requestCode + ", result=" + resultCode);
            return;
        }

        if (requestCode == REQUEST_CODE_TERMUX_STYLING) {
            try {
                var clipData = data.getClipData();
                if (clipData != null && clipData.getItemCount() == 1) {
                    var styleFileUri = clipData.getItemAt(0).getUri();
                    try (var in = getContentResolver().openInputStream(styleFileUri)) {
                        var out = new ByteArrayOutputStream();
                        var buffer = new byte[8196];
                        if (in != null) {
                            // Null input stream means default style.
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                        var bytesReceived = out.toByteArray();
                        var isColors = styleFileUri.getPath().startsWith("/colors/");
                        var fileToWrite = new File(isColors ? TermuxConstants.COLORS_PATH : TermuxConstants.FONT_PATH);
                        var parentDir = fileToWrite.getParentFile();
                        if (parentDir == null || (!parentDir.isDirectory() && !parentDir.mkdirs())) {
                            showTransientMessage("Cannot create ~/.termux/ directory - check permissions in $HOME", true);
                            return;
                        }
                        if (bytesReceived.length == 0) {
                            if (!fileToWrite.delete()) {
                                Log.e(TermuxConstants.LOG_TAG, "Unable to delete file: " + fileToWrite.getAbsolutePath());
                            }
                        } else {
                            try (var fos = new FileOutputStream(fileToWrite)) {
                                fos.write(bytesReceived);
                            }
                        }
                        mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
                    }
                }
            } catch (IOException e) {
                Log.e(TermuxConstants.LOG_TAG, "Error updating files", e);
                showTransientMessage("Error updating files - check file permissions in $HOME", true);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsVisible = true;

        mTermuxTerminalSessionActivityClient.onStart();
        if (mTermuxTerminalViewClient != null) {
            mTermuxTerminalViewClient.onStart();
        }
        registerTermuxActivityBroadcastReceiver();

        if (Build.VERSION.SDK_INT >= 33) {
            TermuxPermissionUtils.requestPermissions(this,
                TermuxPermissionUtils.REQUEST_POST_NOTIFICATIONS,
                Manifest.permission.POST_NOTIFICATIONS
            );
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mTermuxTerminalSessionActivityClient.onResume();
        if (!mPreferences.isFullscreen()) {
            mTerminalView.requestFocus();
        }
        applyFullscreenSetting(mPreferences.isFullscreen());
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsVisible = false;
        mTermuxTerminalSessionActivityClient.onStop();
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    private static class ExecuteIntentInfo {
        public final Intent intent;

        public ExecuteIntentInfo(Intent intent) {
            this.intent = intent;
        }

        public File executable() {
            var path = intent.getData() == null ? null : intent.getData().getPath();
            return path == null ? null : new File(path);
        }

        @Nullable public String sessionName() {
            var executable = executable();
            return executable == null ? null : executable.getName();
        }
    }

    ExecuteIntentInfo executableFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (intent.getComponent() != null &&
            TermuxConstants.TERMUX_INTERNAL_ACTIVITY.equals(intent.getComponent().getClassName()) &&
            Intent.ACTION_RUN.equals(intent.getAction())) {
            return new ExecuteIntentInfo(intent);
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mTermuxService != null) {
            var executeIntentInfo = executableFromIntent(intent);
            if (mTermuxService != null && executeIntentInfo != null) {
                // A connection to the termux service has already been established in
                // onServiceConnected(), so handle the execute intent here now.
                mTermuxTerminalSessionActivityClient.addNewSession(false, executeIntentInfo.sessionName(), executeIntentInfo.executable(), executeIntentInfo.intent);
            }
        }
    }

    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        mTermuxService = ((TermuxService.LocalBinder) service).service;

        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);

        final Intent intent = getIntent();
        setIntent(null);

        var executeIntentInfo = executableFromIntent(intent);
        var sessionName = executeIntentInfo == null ? null : executeIntentInfo.sessionName();
        var executable = executeIntentInfo == null ? null : executeIntentInfo.executable();
        var executableIntent = executeIntentInfo == null ? null : executeIntentInfo.intent;
        boolean isFailSafe = intent.getBooleanExtra(EXTRA_FAILSAFE_SESSION, false);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                if (mTermuxService == null) {
                    // Activity might have been destroyed.
                    return;
                }
                try {
                    mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, sessionName, executable, executableIntent);
                } catch (WindowManager.BadTokenException e) {
                    // Activity finished - ignore.
                }
            });
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, sessionName, executable, executableIntent);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);

        // TODO: mTermuxSessionListViewController.notifyDataSetChanged();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.isShowTerminalToolbar()) {
            terminalToolbarViewPager.setVisibility(View.VISIBLE);
        }

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = savedInstanceState == null ? null :
            savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            1
        );
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        var terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toggleShowTerminalToolbar();
        showTransientMessage((showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), false);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty())
                savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing()) {
            finish();
        }
    }

    /**
     * Show a transient message and dismiss the last one if still visible.
     */
    public void showTransientMessage(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        var autofillManager = getSystemService(AutofillManager.class);
        boolean addAutoFillMenu = (autofillManager != null && autofillManager.isEnabled());

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);

        if (mTerminalView.getStoredSelectedText() != null) {
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        }
        if (addAutoFillMenu) {
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        }
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mTerminalView.getKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_FULLSCREEN_ID, Menu.NONE, R.string.action_fullscreen).setCheckable(true).setChecked(mPreferences.isFullscreen());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
    }

    /**
     * Hook system menu to show context menu instead.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFill(View.AUTOFILL_HINT_USERNAME);
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFill(View.AUTOFILL_HINT_PASSWORD);
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                if (session != null) {
                    session.reset();
                    showTransientMessage(getResources().getString(R.string.msg_terminal_reset), true);
                }
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_FULLSCREEN_ID:
                mPreferences.toggleFullscreen();
                applyFullscreenSetting(mPreferences.isFullscreen());
                return true;
            case CONTEXT_MENU_HELP_ID:
                startActivity(new Intent(this, TermuxHelpActivity.class));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(@NonNull Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.ok, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    private void showStylingDialog() {
        try {
            //noinspection deprecation
            startActivityForResult(new Intent().setClassName("com.termux.styling", "com.termux.styling.TermuxStyleActivity"), REQUEST_CODE_TERMUX_STYLING);
        } catch (ActivityNotFoundException | IllegalArgumentException | SecurityException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            // The SecurityException may happen if app is not allowed to start TermuxStyleActivity (old installation or non-google play build).
            Log.i(TermuxConstants.LOG_TAG, "Error starting Termux:Style - app needs to be installed", e);

            var installationUrl = "https://play.google.com/store/apps/details?id=com.termux.styling";
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(installationUrl))))
                .setNegativeButton(R.string.cancel, null).show();
        }
    }

    private void toggleKeepScreenOn() {
        boolean newValue = !mTerminalView.getKeepScreenOn();
        mTerminalView.setKeepScreenOn(newValue);
        mPreferences.setKeepScreenOn(newValue);
    }

    public void requestAutoFill() {
        var autofillManager = getSystemService(AutofillManager.class);
        if (autofillManager != null && autofillManager.isEnabled()) {
            autofillManager.requestAutofill(mTerminalView);
        }
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        return mTerminalView == null ? null : mTerminalView.getCurrentSession();
    }

    private void registerTermuxActivityBroadcastReceiver() {
        var intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_RELOAD_STYLE);
        intentFilter.addAction(ACTION_REQUEST_PERMISSIONS);

        var flag = Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter, flag);
    }

    private void reloadActivityStyling() {
        mProperties.reloadProperties(this);

        if (mExtraKeysView != null) {
            //mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
            mTermuxTerminalExtraKeys.loadExtraKeysFromProperties();
            mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
        }

        setTerminalToolbarHeight();

        mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
    }

    void applyFullscreenSetting(boolean doFullscreen) {
        var rootView = findViewById(R.id.activity_termux_root_relative_layout);
        //var rootView = getWindow().getDecorView();
        var windowInsetsController = WindowCompat.getInsetsController(getWindow(), rootView);
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        if (doFullscreen) {
            var imm = getSystemService(InputMethodManager.class);
            imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);

            getWindow().setFlags(flags, flags);
            rootView.setFitsSystemWindows(false);

            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            rootView.setFitsSystemWindows(true);
            getWindow().clearFlags(flags);
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        }
    }

}
