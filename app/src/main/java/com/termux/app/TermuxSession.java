package com.termux.app;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;

public class TermuxSession {

    public static final String[] LOGIN_SHELL_BINARIES = new String[]{"login", "bash", "zsh", "fish", "sh"};

    public final TerminalSession mTerminalSession;
    private final TermuxService mTermuxService;

    private TermuxSession(@NonNull final TerminalSession terminalSession, final TermuxService termuxService) {
        this.mTerminalSession = terminalSession;
        this.mTermuxService = termuxService;
    }

    public static TermuxSession execute(@NonNull final TerminalSessionClient terminalSessionClient,
                                        final TermuxService termuxSessionClient,
                                        boolean failSafe) {
        String executable = null;
        if (!failSafe) {
            for (String shellBinary : LOGIN_SHELL_BINARIES) {
                File shellFile = new File(com.termux.app.TermuxConstants.BIN_PATH, shellBinary);
                if (shellFile.canExecute()) {
                    executable = shellFile.getAbsolutePath();
                    break;
                }
            }
        }

        boolean isLoginShell = false;
        if (executable == null) {
            // Fall back to system shell as last resort:
            // Do not start a login shell since ~/.profile may cause startup failure if its invalid.
            // /system/bin/sh is provided by mksh (not toybox) and does load .mkshrc but for android its set
            // to /system/etc/mkshrc even though its default is ~/.mkshrc.
            // So /system/etc/mkshrc must still be valid for failsafe session to start properly.
            // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/src/main.c;l=663
            // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/src/main.c;l=41
            // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/Android.bp;l=114
            executable = "/system/bin/sh";
        } else {
            isLoginShell = true;
        }

        String[] commandArgs = TermuxShellUtils.setupShellCommandArguments(executable, new String[0]);
        executable = commandArgs[0];
        String processName = (isLoginShell ? "-" : "") + new File(executable).getName();

        String[] arguments = new String[commandArgs.length];
        arguments[0] = processName;
        if (commandArgs.length > 1) {
            System.arraycopy(commandArgs, 1, arguments, 1, commandArgs.length - 1);
        }

        if (!failSafe) {
            // Cannot execute written files directly on Android 10 or later.
            String wrappedExecutable = executable;
            executable = "/system/bin/linker" + (Process.is64Bit() ? "64" : "");

            String[] origArguments = arguments;
            arguments = new String[commandArgs.length + 2];
            arguments[0] = processName;
            arguments[1] = TermuxConstants.BIN_PATH + "/sh";
            arguments[2] = wrappedExecutable;
            if (origArguments.length > 1) {
                System.arraycopy(origArguments, 1, arguments, 3, origArguments.length - 1);
            }
        }

        // Setup command environment
        String[] environmentArray = TermuxShellUtils.setupEnvironment(failSafe);

        TerminalSession terminalSession = new TerminalSession(
            executable,
            com.termux.app.TermuxConstants.HOME_PATH,
            arguments,
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
