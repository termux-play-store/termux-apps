package com.termux.app;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TermuxShellUtils {

    public static class ExecuteCommand {
        public ExecuteCommand(String executablePath, String[] arguments) {
            this.executablePath = executablePath;
            this.arguments = arguments;
        }

        final String executablePath;
        final String[] arguments;
    }

    @NonNull
    public static ExecuteCommand setupShellCommandArguments(@NonNull File executable, @NonNull String[] arguments, boolean isLoginShell) {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        String interpreter = null;
        try (FileInputStream in = new FileInputStream(executable)) {
            byte[] buffer = new byte[256];
            int bytesRead = in.read(buffer);
            if (bytesRead > 4) {
                if (buffer[0] == 0x7F && buffer[1] == 'E' && buffer[2] == 'L' && buffer[3] == 'F') {
                    // Elf file, do nothing.
                } else if (buffer[0] == '#' && buffer[1] == '!') {
                    // Try to parse shebang.
                    StringBuilder builder = new StringBuilder();
                    for (int i = 2; i < bytesRead; i++) {
                        char c = (char) buffer[i];
                        if (c == ' ' || c == '\n') {
                            if (builder.length() == 0) {
                                // Skip whitespace after shebang.
                            } else {
                                // End of shebang.
                                String shebangExecutable = builder.toString();
                                if (shebangExecutable.startsWith("/usr") || shebangExecutable.startsWith("/bin")) {
                                    String[] parts = shebangExecutable.split("/");
                                    String binary = parts[parts.length - 1];
                                    interpreter = TermuxConstants.BIN_PATH + "/" + binary;
                                } else if (shebangExecutable.startsWith(TermuxConstants.FILES_PATH)) {
                                    interpreter = shebangExecutable;
                                }
                                break;
                            }
                        } else {
                            builder.append(c);
                        }
                    }
                } else {
                    // No shebang and no ELF, use standard shell.
                    interpreter = TermuxConstants.BIN_PATH + "/sh";
                }
            }
        } catch (IOException e) {
            Log.e(TermuxConstants.LOG_TAG, "IO exception", e);
        }

        var elfFileToExecute = interpreter == null ? executable.getAbsolutePath() : interpreter;

        var actualArguments = new ArrayList<>();
        var processName = (isLoginShell ? "-" : "") + executable.getName();
        actualArguments.add(processName);

        String actualFileToExecute;
        if (elfFileToExecute.startsWith(TermuxConstants.FILES_PATH)) {
            actualFileToExecute = "/system/bin/linker" + (android.os.Process.is64Bit() ? "64" : "");
            actualArguments.add(elfFileToExecute);
        } else {
            actualFileToExecute = elfFileToExecute;
        }

        if (interpreter != null) {
            actualArguments.add(executable.getAbsolutePath());
        }
        Collections.addAll(actualArguments, arguments);
        return new ExecuteCommand(actualFileToExecute, actualArguments.toArray(new String[0]));
    }


    public static String[] setupEnvironment(boolean failsafe) {
        String tmpDir = TermuxConstants.PREFIX_PATH + "/tmp";

        Map<String, String> environment = new HashMap<>();
        environment.put("COLORTERM", "truecolor");
        environment.put("PREFIX", TermuxConstants.PREFIX_PATH);
        environment.put("TERM", "xterm-256color");
        environment.put("TERMUX_VERSION", BuildConfig.VERSION_NAME);
        putToEnvIfInSystemEnv(environment, "ANDROID_ART_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_ASSETS");
        putToEnvIfInSystemEnv(environment, "ANDROID_DATA");
        putToEnvIfInSystemEnv(environment, "ANDROID_I18N_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_RUNTIME_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_STORAGE");
        putToEnvIfInSystemEnv(environment, "ANDROID_TZDATA_ROOT");
        putToEnvIfInSystemEnv(environment, "ASEC_MOUNTPOINT");
        putToEnvIfInSystemEnv(environment, "BOOTCLASSPATH");
        putToEnvIfInSystemEnv(environment, "DEX2OATBOOTCLASSPATH");
        putToEnvIfInSystemEnv(environment, "EXTERNAL_STORAGE");
        putToEnvIfInSystemEnv(environment, "LOOP_MOUNTPOINT");
        putToEnvIfInSystemEnv(environment, "SYSTEMSERVERCLASSPATH");

        if (!failsafe) {
            environment.put("HOME", TermuxConstants.HOME_PATH);
            environment.put("LANG", "en_US.UTF-8");
            environment.put("TMP", tmpDir);
            environment.put("TMPDIR", tmpDir);
            environment.put("LD_PRELOAD", TermuxConstants.PREFIX_PATH + "/lib/libtermux-exec.so");
            environment.put("PATH", TermuxConstants.PREFIX_PATH + "/bin:" + System.getenv("PATH"));
        }

        List<String> environmentList = new ArrayList<>(environment.size());
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            environmentList.add(entry.getKey() + "=" + entry.getValue());
        }
        Collections.sort(environmentList);
        return environmentList.toArray(new String[0]);
    }

    private static void putToEnvIfInSystemEnv(@NonNull Map<String, String> environment, @NonNull String name) {
        String value = System.getenv(name);
        if (value != null) {
            environment.put(name, value);
        }
    }

    public static int getPid(Process p) {
        try {
            Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            try {
                return f.getInt(p);
            } finally {
                f.setAccessible(false);
            }
        } catch (Throwable e) {
            return -1;
        }
    }

    public static @NonNull TerminalSession executeTerminalSession(@NonNull TerminalSessionClient terminalSessionClient,
                                                                  @Nullable File executable,
                                                                  @Nullable String workingDirectory,
                                                                  @Nullable String[] arguments,
                                                                  boolean failSafe) {
        boolean isLoginShell = executable == null;

        if (!failSafe && executable == null) {
            var shellFile = new File(com.termux.app.TermuxConstants.BIN_PATH, "login");
            if (shellFile.isFile()) {
                if (!shellFile.canExecute()) {
                    if (!shellFile.setExecutable(true)) {
                        Log.e(TermuxConstants.LOG_TAG, "Cannot set executable: " + shellFile.getAbsolutePath());
                    }
                }
                executable = shellFile;
            } else {
                Log.e(TermuxConstants.LOG_TAG, "bin/login not found");
            }
        }

        if (executable == null) {
            executable = new File("/system/bin/sh");
        }

        if (arguments == null) {
            arguments = new String[0];
        }
        TermuxShellUtils.ExecuteCommand command = TermuxShellUtils.setupShellCommandArguments(executable, arguments, isLoginShell);

        var environmentArray = TermuxShellUtils.setupEnvironment(failSafe);

        if (workingDirectory == null) {
            workingDirectory = TermuxConstants.HOME_PATH;
        }

        return new TerminalSession(
            command.executablePath,
            workingDirectory,
            command.arguments,
            environmentArray,
            4000,
            terminalSessionClient
        );
    }


}

