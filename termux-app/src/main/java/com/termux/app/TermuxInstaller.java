package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;

import com.termux.R;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX directory below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging directory, $STAGING_PREFIX, is cleared if left over from broken installation below.
 * <p/>
 * (4) The zip file is loaded from a shared library.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
final class TermuxInstaller {

    private static final String TERMUX_STAGING_PREFIX_DIR_PATH = TermuxConstants.FILES_PATH + "/usr-staging"; // Default: "/data/data/com.termux/files/usr-staging"

    /**
     * Performs bootstrap setup if necessary.
     */
    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        // Ensure that termux files and home directory is created if it does not already exist:
        new File(activity.getFilesDir(), "home").mkdir();

        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        UserManager userManager = (UserManager) activity.getSystemService(Context.USER_SERVICE);
        boolean isCurrentUserPrimary = userManager.getSerialNumberForUser(UserHandle.getUserHandleForUid(activity.getApplicationInfo().uid)) == 0;
        if (!isCurrentUserPrimary) {
            String bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_not_primary_user_message);
            TermuxMessageDialogUtils.exitAppWithErrorMessage(activity, activity.getString(R.string.bootstrap_error_title), bootstrapErrorMessage);
            return;
        }

        boolean isInstalledOnExternalStorage = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
        if (isInstalledOnExternalStorage) {
            new AlertDialog.Builder(activity)
                .setTitle(R.string.bootstrap_error_installed_on_portable_sd)
                .show();
            return;
        }

        // If prefix directory exists, even if its a symlink to a valid directory and symlink is not broken/dangling
        if (new File(TermuxConstants.PREFIX_PATH).exists()) {
            whenDone.run();
            return;
        }

        final ProgressDialog progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread(() -> {
            try {
                // Delete prefix staging directory or any file at its destination
                File stagingPrefixFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH);
                if (stagingPrefixFile.exists() && !deleteDir(stagingPrefixFile)) {
                    showBootstrapErrorDialog(activity, whenDone, "Unable to delete old staging area.");
                    return;
                }

                File prefixFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH);
                if (prefixFile.exists() && !deleteDir(prefixFile)) {
                    showBootstrapErrorDialog(activity, whenDone, "Unable to delete old PREFIX.");
                    return;
                }

                final byte[] buffer = new byte[8096];
                final List<Pair<String, String>> symlinks = new ArrayList<>(50);

                final byte[] zipBytes = loadZipBytes();
                try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                    ZipEntry zipEntry;
                    while ((zipEntry = zipInput.getNextEntry()) != null) {
                        if (zipEntry.getName().equals("SYMLINKS.txt")) {
                            BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                            String line;
                            while ((line = symlinksReader.readLine()) != null) {
                                String[] parts = line.split("←");
                                if (parts.length != 2)
                                    throw new RuntimeException("Malformed symlink line: " + line);
                                String oldPath = parts[0];
                                String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
                                symlinks.add(Pair.create(oldPath, newPath));
                            }
                        } else {
                            String zipEntryName = zipEntry.getName();
                            File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);

                            // Silence google play scanning flagging about this: https://support.google.com/faqs/answer/9294009
                            var canonicalPath = targetFile.getCanonicalPath();
                            if (!canonicalPath.startsWith(TERMUX_STAGING_PREFIX_DIR_PATH)) {
                                throw new RuntimeException("Invalid zip entry: " + zipEntryName);
                            }

                            boolean isDirectory = zipEntry.isDirectory();

                            if (isDirectory) {
                                targetFile.mkdirs();
                            } else {
                                File parentDir = targetFile.getParentFile();
                                if (!parentDir.exists() && !parentDir.mkdirs()) {
                                    throw new RuntimeException("Cannot create parent dir for: " + targetFile.getAbsolutePath());
                                }
                                try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                    int readBytes;
                                    while ((readBytes = zipInput.read(buffer)) != -1)
                                        outStream.write(buffer, 0, readBytes);
                                }
                                if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") ||
                                    zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods")) {
                                    //noinspection OctalInteger
                                    Os.chmod(targetFile.getAbsolutePath(), 0700);
                                }
                            }
                        }
                    }
                }

                for (Pair<String, String> symlink : symlinks) {
                    var linkFile = new File(symlink.second);
                    if (!linkFile.getParentFile().exists() && !linkFile.getParentFile().mkdirs()) {
                        throw new RuntimeException("Cannot create dir: " + linkFile.getParentFile());
                    }
                    Os.symlink(symlink.first, symlink.second);
                }

                Os.rename(TERMUX_STAGING_PREFIX_DIR_PATH, TermuxConstants.PREFIX_PATH);

                activity.runOnUiThread(whenDone);
            } catch (final Exception e) {
                Log.e(TermuxConstants.LOG_TAG, "Error in installation", e);
                showBootstrapErrorDialog(activity, whenDone, "Error in installation: " + e.getMessage());
            } finally {
                activity.runOnUiThread(() -> {
                    try {
                        progress.dismiss();
                    } catch (RuntimeException e) {
                        // Activity already dismissed - ignore.
                    }
                });
            }
        }).start();
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Log.e(TermuxConstants.LOG_TAG, "Bootstrap Error: " + message);

        activity.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_body)
                    .setNegativeButton(R.string.bootstrap_error_abort, (dialog, which) -> {
                        dialog.dismiss();
                        activity.finish();
                    })
                    .setPositiveButton(R.string.bootstrap_error_try_again, (dialog, which) -> {
                        dialog.dismiss();
                        deleteDir(new File(TermuxConstants.PREFIX_PATH));
                        TermuxInstaller.setupBootstrapIfNeeded(activity, whenDone);
                    }).show();
            } catch (WindowManager.BadTokenException e1) {
                // Activity already dismissed - ignore.
            }
        });
    }

    static void setupStorageSymlinks(final Context context) {
        Log.i(TermuxConstants.LOG_TAG, "Setting up storage symlinks.");

        if (!Environment.isExternalStorageManager()) {
            context.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }

        new Thread(() -> {
            try {
                Log.i(TermuxConstants.LOG_TAG, "Setting up storage symlinks for directories in: " + Environment.getExternalStorageDirectory().getAbsolutePath());

                File storageDir = new File(TermuxConstants.HOME_PATH + "/storage");

                var storageDirEntries = storageDir.listFiles();
                if (storageDirEntries != null) {
                    for (File child : storageDirEntries) {
                        if (!child.delete()) {
                            Log.e(TermuxConstants.LOG_TAG, "Cannot delete: " + child.getAbsolutePath());
                        }
                    }
                }

                if (!storageDir.exists() && !storageDir.mkdirs()) {
                    Log.e(TermuxConstants.LOG_TAG, "Cannot create: " + storageDir.getAbsolutePath());
                    return;
                }

                // Get primary storage root "/storage/emulated/0" symlink
                File sharedDir = Environment.getExternalStorageDirectory();
                Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                Os.symlink(documentsDir.getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());

                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

                File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

                File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

                File podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
                Os.symlink(podcastsDir.getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());

                File audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
                Os.symlink(audiobooksDir.getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());

                // Dir 0 should ideally be for primary storage
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053

                // Create "Android/data/com.termux" symlinks
                File[] dirs = context.getExternalFilesDirs(null);
                if (dirs != null && dirs.length > 0) {
                    for (int i = 0; i < dirs.length; i++) {
                        File dir = dirs[i];
                        if (dir == null) continue;
                        String symlinkName = "external-" + i;
                        Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                    }
                }

                // Create "Android/media/com.termux" symlinks
                dirs = context.getExternalMediaDirs();
                if (dirs != null && dirs.length > 0) {
                    for (int i = 0; i < dirs.length; i++) {
                        File dir = dirs[i];
                        if (dir == null) continue;
                        String symlinkName = "media-" + i;
                        Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                    }
                }
            } catch (ErrnoException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir.delete();
    }

    public static byte[] loadZipBytes() {
        // Only load the shared library when necessary to save memory usage.
        System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    public static native byte[] getZip();

    public static void setupAppLibSymlink(Context context) {
        var nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        var targetFile = new File(TermuxConstants.APP_LIB_PATH);
        new Thread(() -> {
            try {
                if (targetFile.exists()) {
                    if (targetFile.getCanonicalPath().equals(nativeLibraryDir)) {
                        return;
                    } else {
                        Log.w(TermuxConstants.LOG_TAG, "Existing incorrect symlink: " + targetFile.getAbsolutePath());
                        if (!targetFile.delete()) {
                            Log.e(TermuxConstants.LOG_TAG, "Cannot delete: " + targetFile.getAbsolutePath());
                            return;
                        }
                    }
                } else {
                    if (Files.isSymbolicLink(targetFile.toPath())) {
                        Log.w(TermuxConstants.LOG_TAG, "Broken symlink - deleting: " + targetFile.getAbsolutePath());
                        if (!targetFile.delete()) {
                            Log.e(TermuxConstants.LOG_TAG, "Could not delete broken symlink: " + targetFile.getAbsolutePath());
                            return;
                        }
                    }
                }
                // Make sures the files dir exists.
                context.getFilesDir();
                Os.symlink(nativeLibraryDir, targetFile.getAbsolutePath());
            } catch (ErrnoException | IOException e) {
                Log.e(TermuxConstants.LOG_TAG, "Error symlinking " + nativeLibraryDir + " <- " + targetFile.getAbsolutePath(), e);
            }
        }).start();
    }

}
