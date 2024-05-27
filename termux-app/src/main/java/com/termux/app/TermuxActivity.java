package com.termux.app;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.os.Environment;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
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
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_ID = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

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
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

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

        mProperties.reloadProperties();
        mPreferences = new TermuxPreferences(this);

        setContentView(R.layout.activity_termux);

        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        mTerminalView.setTextSize(mPreferences.getFontSize());

        mTermuxTerminalSessionActivityClient.onCreate();

        setTerminalToolbarView(savedInstanceState);

        setupDrawerMenu();

        /*
        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        MenuItem newSessionItem = menu.findItem(R.id.new_session);
        newSessionItem.setOnMenuItemClickListener(item -> {
            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
            return true;
        });
        View newSessionButton = findViewById()
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TermuxMessageDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
        MenuItem toggleKeyboardButton = menu.findItem(R.id.toggle_keyboard);
        toggleKeyboardButton.setOnMenuItemClickListener(item -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
            return true;
        });
        toggleKeyboardButton.setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });
        toggleKeyboardButton.setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
         */

        registerForContextMenu(mTerminalView);

        // Start the {@link TermuxService} and make it run regardless of who is bound to it
        var serviceIntent = new Intent(this, TermuxService.class);
        startForegroundService(serviceIntent);

        // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
        // callback if it succeeds.
        if (!bindService(serviceIntent, this, 0)) {
            throw new RuntimeException("bindService() failed");
        }

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
                        getDrawer().closeDrawers();
                    } else {
                        getDrawer().openDrawer(Gravity.LEFT);
                    }

                }
            });
        }

        //var intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        //startActivityForResult(intent, 2332,null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            Log.e("termux", "Failed activity result - request=" + requestCode + ", result=" + resultCode);
            return;
        }
        if (requestCode == 2332) {
            Log.e("termux", "OK: " + data.getData());
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, data.getData());
            Log.e("termux", "Environment.getExternalStorageDirectory().getPath(): " + Environment.getExternalStorageDirectory().getPath());
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
    }

    @Override
    public void onResume() {
        super.onResume();
        mTermuxTerminalSessionActivityClient.onResume();
        if (!mPreferences.isFullscreen()) {
            mTerminalView.requestFocus();
        }
        applyFullscreenSetting(mPreferences.isFullscreen());

        if (Build.VERSION.SDK_INT >= 33) {
            TermuxPermissionUtils.requestPermission(this, Manifest.permission.POST_NOTIFICATIONS, TermuxPermissionUtils.REQUEST_POST_NOTIFICATIONS);
        }
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try {
            if ("com.termux.app.NEW_STYLE".equals(intent.getAction())) {
                var clipData = intent.getClipData();
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
                        if (bytesReceived.length == 0) {
                            if (!fileToWrite.delete()) {
                                Log.e("termux", "Unable to delete file: " + fileToWrite.getAbsolutePath());
                            }
                        } else {
                            try (var fos = new FileOutputStream(fileToWrite)) {
                                fos.write(bytesReceived);
                            }
                        }
                        mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
                    }

                }
            }
        } catch (Exception e) {
            Log.e(TermuxConstants.LOG_TAG, "Error handling new intent", e);
            TermuxMessageDialogUtils.showToast(this, "Error handling new intent: " + e.getMessage());
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

/*
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
 */

        final Intent intent = getIntent();
        if (intent != null) {
            Log.e("termux", "SHORT CLASS: " + intent.getComponent().getShortClassName());
            Log.e("termux", "LONG CLASS: " + intent.getComponent().getClassName());
        }
        setIntent(null);

        boolean isFailSafe = intent.getBooleanExtra(EXTRA_FAILSAFE_SESSION, false);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                if (mTermuxService == null) {
                    // Activity might have been destroyed.
                    return;
                }
                try {
                    mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
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
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);

        termuxSessionListNotifyUpdated();
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
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toggleShowTerminalToolbar();
        TermuxMessageDialogUtils.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)));
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

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            getDrawer().openDrawer(Gravity.LEFT);
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing()) {
            finish();
        }
    }

    /**
     * Show a toast and dismiss the last one if still visible.
     */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        Snackbar.make(mTerminalView, text, longDuration ? BaseTransientBottomBar.LENGTH_LONG : BaseTransientBottomBar.LENGTH_SHORT).show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean addAutoFillMenu = false;
        var autofillManager = getSystemService(AutofillManager.class);
        if (autofillManager != null && autofillManager.isEnabled()) {
            addAutoFillMenu = true;
        }

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);

        if (mTerminalView.getStoredSelectedText() != null) {
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        }
        if (addAutoFillMenu)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_ID, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mTerminalView.getKeepScreenOn());
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
            case CONTEXT_MENU_AUTOFILL_ID:
                requestAutoFill();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                if (session != null) {
                    session.reset();
                    showToast(getResources().getString(R.string.msg_terminal_reset), true);
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
            case CONTEXT_MENU_HELP_ID:
                startActivity(new Intent(this, TermuxHelpActivity.class));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void showStylingDialog() {
        try {
            //noinspection deprecation
            startActivity(new Intent().setClassName("com.termux.styling", "com.termux.styling.TermuxStyleActivity"));
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            Log.i(TermuxConstants.LOG_TAG, "Error starting Termux:Style - app needs to be installed", e);

            var validInstallers = Arrays.asList("com.android.vending", "com.google.android.feedback");
            var installer = getPackageManager().getInstallerPackageName(getPackageName());
            var installedFromGooglePlay = installer != null && validInstallers.contains(installer);

            var installationUrl = installedFromGooglePlay
                ? "https://play.google.com/store/apps/details?id=com.termux.styling"
                : "https://f-droid.org/en/packages/com.termux.styling";
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(installationUrl))))
                .setNegativeButton(android.R.string.cancel, null).show();
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


    private SubMenu mSessionsSubMenu;
    private SubMenu mTasksSubMenu;

    public void setupDrawerMenu() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        menu.clear();
        navigationView.inflateMenu(R.menu.drawer_menu);
        menu = navigationView.getMenu();

        navigationView.getRootView().setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Log.e("termux", "ON LONG CLICK: " + v);
                return false;
            }
        });

        mSessionsSubMenu = menu.addSubMenu("Sessions");
        SubMenu toolsSubMenu = menu.addSubMenu("Tools");

        if (mTermuxService != null) {
            int i = 0;
            for (TermuxSession session : mTermuxService.getTermuxSessions()) {
                i++;

                String numberPart = "[" + i + "] ";
                String sessionNamePart = (TextUtils.isEmpty(session.mTerminalSession.mSessionName) ? "" : session.mTerminalSession.mSessionName);
                String sessionTitlePart = (TextUtils.isEmpty(session.mTerminalSession.getTitle()) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + session.mTerminalSession.getTitle()));

                String title = numberPart + sessionTitlePart;
                MenuItem sessionsItem = mSessionsSubMenu.add(title);
                sessionsItem.setOnMenuItemClickListener(item -> {
                    getTermuxTerminalSessionClient().setCurrentSession(session.mTerminalSession);
                    getDrawer().closeDrawers();
                    return true;
                });
            }
        }

        MenuItem keyboardItem = toolsSubMenu.add("Keyboard");
        keyboardItem.setIcon(R.drawable.icon_keyboard);
        keyboardItem.setOnMenuItemClickListener(item -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
            return true;
        });

        MenuItem newSessionItem = toolsSubMenu.add("New session");
        newSessionItem.setIcon(R.drawable.icon_add);
        newSessionItem.setOnMenuItemClickListener(item -> {
            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
            getDrawer().closeDrawers();
            return true;
        });
        mTasksSubMenu = menu.addSubMenu("Sessions");
    }

    public void termuxSessionListNotifyUpdated() {
        setupDrawerMenu();
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
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_RELOAD_STYLE);
        intentFilter.addAction(ACTION_REQUEST_PERMISSIONS);

        var flag = Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter, flag);
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("termux", "ONRECEIVE: " + intent.getAction() + ", isVisible=" + isVisible());
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
    }

    private void reloadActivityStyling() {
        mProperties.reloadProperties();

        if (mExtraKeysView != null) {
            //mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
            mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
        }

        setTerminalToolbarHeight();

        mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
    }

    void applyFullscreenSetting(boolean doFullscreen) {
        WindowInsetsControllerCompat windowInsetsController =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        View rootView = findViewById(R.id.activity_termux_root_relative_layout);
        if (doFullscreen) {
            rootView.setFitsSystemWindows(false);
            rootView.setPadding(0, 0, 0, 0);

            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            InputMethodManager imm = getSystemService(InputMethodManager.class);
            imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
        } else {
            rootView.setFitsSystemWindows(true);
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        }
    }

}
