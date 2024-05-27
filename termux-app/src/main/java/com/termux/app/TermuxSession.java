package com.termux.app;

import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;
import java.util.Arrays;

public class TermuxSession {

    public final TerminalSession mTerminalSession;
    private final TermuxService mTermuxService;

    private TermuxSession(@NonNull final TerminalSession terminalSession, final TermuxService termuxService) {
        this.mTerminalSession = terminalSession;
        this.mTermuxService = termuxService;
    }

    public static TermuxSession execute(@NonNull TerminalSessionClient terminalSessionClient,
                                        TermuxService termuxSessionClient,
                                        @Nullable String executablePath,
                                        boolean failSafe) {
        String loginShellPath = null;
        if (!failSafe) {
            File shellFile = new File(com.termux.app.TermuxConstants.BIN_PATH, "login");
            if (shellFile.isFile()) {
                if (!shellFile.canExecute()) {
                    shellFile.setExecutable(true);
                }
                loginShellPath = shellFile.getAbsolutePath();
            } else {
                Log.e(TermuxConstants.LOG_TAG, "bin/login not found");
            }
        }

        boolean isLoginShell = false;
        if (loginShellPath == null) {
            loginShellPath = "/system/bin/sh";
        } else {
            isLoginShell = true;
        }

        String[] arguments = new String[0];
        TermuxShellUtils.ExecuteCommand command = TermuxShellUtils.setupShellCommandArguments(loginShellPath, arguments, isLoginShell);
        Log.e("termux", "command.executablePath=" + command.executablePath + ", arguments=" + Arrays.toString(command.arguments));

        String[] environmentArray = TermuxShellUtils.setupEnvironment(failSafe);

        TerminalSession terminalSession = new TerminalSession(
            command.executablePath,
            com.termux.app.TermuxConstants.HOME_PATH,
            command.arguments,
            environmentArray,
            4000,
            terminalSessionClient
        );

        return new TermuxSession(terminalSession, termuxSessionClient);
    }

    public void finish() {
        // If process is still running, then ignore the call
        if (mTerminalSession.isRunning()) return;
        mTermuxService.onTermuxSessionExited(this);
    }

    public void killIfExecuting() {
        // Send SIGKILL to process
        mTerminalSession.finishIfRunning();
    }

}
