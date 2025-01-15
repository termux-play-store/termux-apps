package com.termux.app;

import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class TermuxAppShell {

    private static class StreamGobbler extends Thread {
        @NonNull
        private final String shell;
        @NonNull
        private final InputStream inputStream;
        @NonNull
        private final BufferedReader reader;

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
                    Log.e(TermuxConstants.LOG_TAG, "Task (" + shell + "): " + line);
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

    public static @Nullable TermuxAppShell execute(File executable,
                                                   @NonNull String[] arguments,
                                                   @NonNull final TermuxService termuxService,
                                                   @Nullable String workingDirectoryString) {
        var command = TermuxShellUtils.setupShellCommandArguments(executable, arguments, false);
        var environmentArray = TermuxShellUtils.setupEnvironment(false);
        final Process process;
        try {
            var runtimeExecArgs = new String[command.arguments.length];
            runtimeExecArgs[0] = command.executablePath;
            // TODO: Skipping first arg such as "sh" since it's not possible using the java exec() api to set it:
            System.arraycopy(command.arguments, 1, runtimeExecArgs, 1, command.arguments.length - 1);
            var workingDirectory = new File(workingDirectoryString == null ? TermuxConstants.HOME_PATH : workingDirectoryString);
            process = Runtime.getRuntime().exec(runtimeExecArgs, environmentArray, workingDirectory);
        } catch (IOException e) {
            Log.e(TermuxConstants.LOG_TAG, "Error executing task", e);
            return null;
        }

        var appShell = new TermuxAppShell(process, termuxService);
        new Thread(() -> {
            try {
                int mPid = TermuxShellUtils.getPid(process);

                DataOutputStream STDIN = new DataOutputStream(process.getOutputStream());
                StreamGobbler STDOUT = new StreamGobbler(mPid + "-stdout-gobbler", process.getInputStream());
                StreamGobbler STDERR = new StreamGobbler(mPid + "-stderr-gobbler", process.getErrorStream());

                STDOUT.start();
                STDERR.start();

                int exitCode = process.waitFor();

                try {
                    STDIN.close();
                } catch (IOException e) {
                    // might be closed already
                }
                STDOUT.join();
                STDERR.join();
                new Handler(Looper.getMainLooper()).post(() -> termuxService.onAppShellExited(appShell, exitCode));
            } catch (IllegalThreadStateException | InterruptedException e) {
                Log.e(TermuxConstants.LOG_TAG, "Background task error: " + e);
            }
        }).start();
        return appShell;
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
