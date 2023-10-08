package com.termux.app;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class TermuxAppShell {
    public class StreamGobbler extends Thread {
        @NonNull
        private final String shell;
        @NonNull
        private final InputStream inputStream;
        @NonNull
        private final BufferedReader reader;
        private static final String LOG_TAG = "termux-tasks";

        public StreamGobbler(@NonNull String shell, @NonNull InputStream inputStream) {
            super("TermuxStreamGobbler");
            this.shell = shell;
            this.inputStream = inputStream;
            reader = new BufferedReader(new InputStreamReader(inputStream));
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    // TODO: Is this wait necessary?
                    // TODO: log
                    try {
                        this.wait(128);
                    } catch (InterruptedException e) {
                        // no action
                    }
                }
            } catch (IOException e) {
                // reader probably closed, expected exit condition
            }

            // make sure our stream is closed and resources will be freed
            try {
                reader.close();
            } catch (IOException e) {
                // read already closed
            }
        }
    }

    private final Process mProcess;
    private final TermuxService mAppShellClient;

    private TermuxAppShell(@NonNull final Process process, final TermuxService appShellClient) {
        this.mProcess = process;
        this.mAppShellClient = appShellClient;
    }

    public static TermuxAppShell execute(String executable,
                                         String[] arguments,
                                         @NonNull final TermuxService termuxService) {
        final String[] commandArray = TermuxShellUtils.setupShellCommandArguments(executable, arguments);
        String[] environmentArray = TermuxShellUtils.setupEnvironment(false);
        final Process process;
        try {
            process = Runtime.getRuntime().exec(commandArray, environmentArray, new File(TermuxConstants.HOME_PATH));
        } catch (IOException e) {
            Log.e(TermuxConstants.LOG_TAG, "Error executing task", e);
            return null;
        }

        final TermuxAppShell appShell = new TermuxAppShell(process, termuxService);
        new Thread() {
            @Override
            public void run() {
                try {
                    appShell.executeInner(termuxService);
                } catch (IllegalThreadStateException | InterruptedException e) {
                    Log.e(TermuxConstants.LOG_TAG, "Error: " + e);
                }
            }
        }.start();

        return appShell;
    }

    private void executeInner(@NonNull final Context context) throws IllegalThreadStateException, InterruptedException {
        int mPid = TermuxShellUtils.getPid(mProcess);

        DataOutputStream STDIN = new DataOutputStream(mProcess.getOutputStream());
        StreamGobbler STDOUT = new StreamGobbler(mPid + "-stdout-gobbler", mProcess.getInputStream());
        StreamGobbler STDERR = new StreamGobbler(mPid + "-stderr-gobbler", mProcess.getErrorStream());

        STDOUT.start();
        STDERR.start();

        int exitCode = mProcess.waitFor();

        try {
            STDIN.close();
        } catch (IOException e) {
            // might be closed already
        }
        STDOUT.join();
        STDERR.join();
        mProcess.destroy();

        // TODO: handle exit code, (notify on success, show something more on error)?

    }

    /**
     * Kill this {@link TermuxAppShell} by sending a {@link OsConstants#SIGILL} to its {@link #mProcess}.
     */
    public void kill() {
        int pid = TermuxShellUtils.getPid(mProcess);
        try {
            // Send SIGKILL to process
            Os.kill(pid, OsConstants.SIGKILL);
        } catch (ErrnoException e) {
            Log.w(TermuxConstants.LOG_TAG, "Failed to send SIGKILL to AppShell with pid " + pid + ": " + e.getMessage());
        }
    }

}
