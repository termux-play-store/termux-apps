package com.termux.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowInsetsController;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * The {@link TerminalSessionClient} implementation that may require an {@link Activity} for its interface methods.
 */
public final class TermuxTerminalSessionActivityClient implements TerminalSessionClient {

    private static class BellHandler {
        private static final long DURATION = 50;
        private static final long MIN_PAUSE = 3 * DURATION;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private long lastBell = 0;
        private final Runnable bellRunnable;

        private BellHandler(final Vibrator vibrator) {
            bellRunnable = () -> {
                if (vibrator != null) {
                    vibrator.vibrate(VibrationEffect.createOneShot(DURATION, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            };
        }

        public synchronized void doBell() {
            long now = now();
            long timeSinceLastBell = now - lastBell;

            if (timeSinceLastBell < 0) {
                // there is a next bell pending; don't schedule another one
            } else if (timeSinceLastBell < MIN_PAUSE) {
                // there was a bell recently, schedule the next one
                handler.postDelayed(bellRunnable, MIN_PAUSE - timeSinceLastBell);
                lastBell = lastBell + MIN_PAUSE;
            } else {
                // the last bell was long ago, do it now
                bellRunnable.run();
                lastBell = now;
            }
        }

        private long now() {
            return SystemClock.uptimeMillis();
        }
    }

    private final TermuxActivity mActivity;

    private BellHandler mBellHandler;

    private static final int MAX_SESSIONS = 8;

    private SoundPool mBellSoundPool;

    private int mBellSoundId;

    public TermuxTerminalSessionActivityClient(TermuxActivity activity) {
        this.mActivity = activity;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public void onCreate() {
        // Set terminal fonts and colors
        onReloadActivityStyling();
        this.mBellHandler = new BellHandler(mActivity.getSystemService(Vibrator.class));
    }

    /**
     * Called when mActivity.onStart() is called
     */
    public void onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in shared preferences stored by {@link #onStop} if its valid,
        // otherwise get the last session currently running.
        if (mActivity.getTermuxService() != null) {
            setCurrentSession(getCurrentStoredSessionOrLast());
            termuxSessionListNotifyUpdated();
        }

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal.
        mActivity.getTerminalView().onScreenUpdated();
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    public void onResume() {
        // Just initialize the mBellSoundPool and load the sound, otherwise bell might not run
        // the first time bell key is pressed and play() is called, since sound may not be loaded
        // quickly enough before the call to play(). https://stackoverflow.com/questions/35435625
        loadBellSoundPool();
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    public void onStop() {
        // Store current session in shared preferences so that it can be restored later in
        // {@link #onStart} if needed.
        TerminalSession currentSession = mActivity.getCurrentSession();
        mActivity.mPreferences.setCurrentSession(currentSession == null ? null : currentSession.mHandle);

        // Release mBellSoundPool resources, specially to prevent exceptions like the following to be thrown
        // java.util.concurrent.TimeoutException: android.media.SoundPool.finalize() timed out after 10 seconds
        // Bell is not played in background anyways
        // Related: https://stackoverflow.com/a/28708351/14686958
        releaseBellSoundPool();
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    public void onReloadActivityStyling() {
        try {
            File fontFile = new File(TermuxConstants.FONT_PATH);
            File colorsFile = new File(TermuxConstants.COLORS_PATH);

            final Properties props = new Properties();
            if (colorsFile.isFile()) {
                try (InputStream in = new FileInputStream(colorsFile)) {
                    props.load(in);
                }
            }

            TerminalColors.COLOR_SCHEME.updateWith(props);
            TerminalSession session = mActivity.getCurrentSession();
            if (session != null && session.getEmulator() != null) {
                session.getEmulator().mColors.reset();
            }
            updateBackgroundColor();

            final Typeface newTypeface = (fontFile.exists() && fontFile.length() > 0) ? Typeface.createFromFile(fontFile) : Typeface.MONOSPACE;
            mActivity.getTerminalView().setTypeface(newTypeface);
        } catch (Exception e) {
            Log.e(TermuxConstants.LOG_TAG, "Error in onReloadActivityStyling()", e);
        }
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
        if (mActivity.isVisible() && mActivity.getCurrentSession() == changedSession) {
            mActivity.getTerminalView().onScreenUpdated();
        }
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
        if (!mActivity.isVisible()) return;

        if (updatedSession != mActivity.getCurrentSession()) {
            // Only show toast for other sessions than the current one, since the user
            // probably consciously caused the title change to change in the current session
            // and don't want an annoying toast for that.
            mActivity.showTransientMessage(toToastTitle(updatedSession), true);
        }

        termuxSessionListNotifyUpdated();
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
        TermuxService service = mActivity.getTermuxService();

        if (service == null || service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            mActivity.finishActivityIfNotFinishing();
            return;
        }

        int index = service.getIndexOfSession(finishedSession);

        // For plugin commands that expect the result back, we should immediately close the session
        // and send the result back instead of waiting fo the user to press enter.
        // The plugin can handle/show errors itself.
        var termuxSession = service.getTermuxSession(index);

        if (mActivity.isVisible() && finishedSession != mActivity.getCurrentSession()) {
            // Show toast for non-current sessions that exit.
            // Verify that session was not removed before we got told about it finishing:
            if (index >= 0)
                mActivity.showTransientMessage(toToastTitle(finishedSession) + " - exited", true);
        }

        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // On Android TV devices we need to use older behaviour because we may
            // not be able to have multiple launcher icons.
            if (service.getTermuxSessionsSize() > 1) {
                removeFinishedSession(finishedSession);
            }
        } else {
            // Once we have a separate launcher icon for the failsafe session, it
            // should be safe to auto-close session on exit code '0' or '130'.
            if ((finishedSession.getExitStatus() == 0 || finishedSession.getExitStatus() == 130)) {
                removeFinishedSession(finishedSession);
            }
        }
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (!mActivity.isVisible()) return;
        TermuxUrlUtils.copyTextToClipboard(mActivity, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (!mActivity.isVisible()) return;

        String text = TermuxUrlUtils.getTextStringFromClipboardIfSet(mActivity, true);
        if (text != null)
            mActivity.getTerminalView().mEmulator.paste(text);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        if (!mActivity.isVisible()) return;

        switch (mActivity.mProperties.getBellBehaviour()) {
            case VIBRATE:
                mBellHandler.doBell();
                break;
            case BEEP:
                loadBellSoundPool();
                if (mBellSoundPool != null) {
                    mBellSoundPool.play(mBellSoundId, 1.f, 1.f, 1, 0, 1.f);
                }
                break;
            case IGNORE:
                break;
        }
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession changedSession) {
        if (mActivity.getCurrentSession() == changedSession) {
            updateBackgroundColor();
        }
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {

    }

    /**
     * Load mBellSoundPool
     */
    private synchronized void loadBellSoundPool() {
        if (mBellSoundPool == null) {
            mBellSoundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()).build();

            try {
                mBellSoundId = mBellSoundPool.load(mActivity, com.termux.R.raw.bell, 1);
            } catch (Exception e) {
                // Catch java.lang.RuntimeException: Unable to resume activity {com.termux/com.termux.app.TermuxActivity}: android.content.res.Resources$NotFoundException: File res/raw/bell.ogg from drawable resource ID
                Log.e(TermuxConstants.LOG_TAG, "Failed to load bell sound pool", e);
            }
        }
    }

    /**
     * Release mBellSoundPool resources
     */
    private synchronized void releaseBellSoundPool() {
        if (mBellSoundPool != null) {
            mBellSoundPool.release();
            mBellSoundPool = null;
        }
    }


    /**
     * Try switching to session.
     */
    public void setCurrentSession(TerminalSession session) {
        if (mActivity.getTerminalView().attachSession(session)) {
            // notify about switched session if not already displaying the session
            notifyOfSessionChange();
        }

        // We call the following even when the session is already being displayed since config may
        // be stale, like current session not selected or scrolled to.
        checkAndScrollToSession(session);
        updateBackgroundColor();
    }

    void notifyOfSessionChange() {
        if (!mActivity.isVisible()) return;
        TerminalSession session = mActivity.getCurrentSession();
        mActivity.showTransientMessage(toToastTitle(session), false);
    }

    public void switchToSession(boolean forward) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        TerminalSession currentTerminalSession = mActivity.getCurrentSession();
        int index = service.getIndexOfSession(currentTerminalSession);
        int size = service.getTermuxSessionsSize();
        if (forward) {
            if (++index >= size) index = 0;
        } else {
            if (--index < 0) index = size - 1;
        }

        var termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) {
            setCurrentSession(termuxSession);
        }
    }

    public void switchToSession(int index) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        var termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) {
            setCurrentSession(termuxSession);
        }
    }

    @SuppressLint("InflateParams")
    public void renameSession(final TerminalSession sessionToRename) {
        if (sessionToRename == null) return;

        TermuxMessageDialogUtils.textInput(mActivity,
            R.string.title_rename_session,
            R.string.hint_session_name,
            sessionToRename.mSessionName,
            R.string.action_rename_session_confirm,
            text -> {
                renameSession(sessionToRename, text);
                termuxSessionListNotifyUpdated();
            },
            -1,
            null,
            -1,
            null,
            null
        );
    }

    private void renameSession(TerminalSession sessionToRename, String text) {
        if (sessionToRename == null) return;
        sessionToRename.mSessionName = text;
    }

    public void addNewSession(boolean isFailSafe, String sessionName, File executable, @Nullable Intent sessionIntent) {
        var service = mActivity.getTermuxService();
        if (service == null) {
            return;
        }

        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            new AlertDialog.Builder(mActivity)
                .setTitle(R.string.title_max_terminals_reached)
                .setMessage(R.string.msg_max_terminals_reached)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        } else {
            if ((sessionName == null || sessionName.isEmpty()) && isFailSafe) {
                sessionName = "Failsafe";
            }
            var currentSession = mActivity.getCurrentSession();
            var workingDirectory = (sessionIntent == null) ? null : sessionIntent.getStringExtra(TermuxService.TERMUX_EXECUTE_WORKDIR);
            var arguments = (sessionIntent == null) ? null : sessionIntent.getStringArrayExtra(TermuxService.TERMUX_EXECUTE_EXTRA_ARGUMENTS);
            if (arguments == null) {
                arguments = new String[0];
            }
            if (workingDirectory == null) {
                workingDirectory = currentSession == null ? TermuxConstants.HOME_PATH : currentSession.getCwd();
            }
            var newTermuxSession = service.createTermuxSession(executable, arguments, null, workingDirectory, isFailSafe, sessionName);
            setCurrentSession(newTermuxSession);
            mActivity.getDrawer().closeDrawers();
        }
    }

    /**
     * The current session as stored or the last one if that does not exist.
     */
    public TerminalSession getCurrentStoredSessionOrLast() {
        // Check if the session handle found matches one of the currently running sessions
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;

        String currentSessionHandle = mActivity.mPreferences.getCurrentSession();
        TerminalSession currentSession = service.getTerminalSessionForHandle(currentSessionHandle);

        if (currentSession == null) {
            return service.getLastTermuxSession();
        } else {
            return currentSession;
        }
    }

    public void removeFinishedSession(TerminalSession finishedSession) {
        // Return pressed with finished session - remove it.
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        int index = service.removeTermuxSession(finishedSession);

        int size = service.getTermuxSessionsSize();
        if (size == 0) {
            // There are no sessions to show, so finish the activity.
            mActivity.finishActivityIfNotFinishing();
        } else {
            if (index >= size) {
                index = size - 1;
            }
            var termuxSession = service.getTermuxSession(index);
            if (termuxSession != null) {
                setCurrentSession(termuxSession);
            }
        }
    }

    public void termuxSessionListNotifyUpdated() {
        mActivity.mTermuxSessionListViewController.notifyDataSetChanged();
    }

    public void checkAndScrollToSession(TerminalSession session) {
        if (!mActivity.isVisible()) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0) return;
        final ListView termuxSessionsListView = mActivity.findViewById(R.id.terminal_sessions_list);
        // TODO: Can this be null? if (termuxSessionsListView == null) return;

        termuxSessionsListView.setItemChecked(indexOfSession, true);
        termuxSessionsListView.setSelection(indexOfSession);
        termuxSessionListNotifyUpdated();

        // Delay is necessary otherwise sometimes scroll to newly added session does not happen
        termuxSessionsListView.postDelayed(() -> termuxSessionsListView.smoothScrollToPosition(indexOfSession), 1000);
    }


    String toToastTitle(TerminalSession session) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;

        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0) return null;
        StringBuilder toastTitle = new StringBuilder("[" + (indexOfSession + 1) + "]");
        if (!TextUtils.isEmpty(session.mSessionName)) {
            toastTitle.append(" ").append(session.mSessionName);
        }
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(session.mSessionName == null ? " " : "\n");
            toastTitle.append(title);
        }
        return toastTitle.toString();
    }

    public void updateBackgroundColor() {
        if (!mActivity.isVisible()) return;
        TerminalSession session = mActivity.getCurrentSession();
        if (session != null && session.getEmulator() != null) {
            var backgroundColor = session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND];
            var decorView = mActivity.getWindow().getDecorView();
            decorView.setBackgroundColor(backgroundColor);
            var wic = decorView.getWindowInsetsController();
            if (wic != null) {
                var mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                var isDark = ColorUtils.calculateLuminance(backgroundColor) <= 0.5;
                var flag = isDark ? 0 : mask;
                wic.setSystemBarsAppearance(flag, mask);
            }
        }
    }

}
