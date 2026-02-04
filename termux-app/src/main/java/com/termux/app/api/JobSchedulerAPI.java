package com.termux.app.api;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.Log;

import com.termux.app.TermuxService;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class JobSchedulerAPI {

    private static final String LOG_TAG = "JobSchedulerAPI";

    private static String formatJobInfo(JobInfo jobInfo) {
        var path = jobInfo.getExtras().getString(JobSchedulerService.SCRIPT_FILE_PATH);

        var descriptions = new ArrayList<>();
        if (jobInfo.isPeriodic()) {
            descriptions.add(String.format(Locale.ENGLISH, "(periodic: %dms)", jobInfo.getIntervalMillis()));
        }
        if (jobInfo.isRequireCharging()) {
            descriptions.add("(while charging)");
        }
        if (jobInfo.isRequireDeviceIdle()) {
            descriptions.add("(while idle)");
        }
        if (jobInfo.isPersisted()) {
            descriptions.add("(persisted)");
        }
        if (jobInfo.isRequireBatteryNotLow()) {
            descriptions.add("(battery not low)");
        }
        if (jobInfo.isRequireStorageNotLow()) {
            descriptions.add("(storage not low)");
        }
        var networkRequest = jobInfo.getRequiredNetwork();
        if (networkRequest != null) {
            descriptions.add(String.format(Locale.ENGLISH, "(network: %s)", networkRequest.toString()));
        }

        var description = TextUtils.join(" ", descriptions);

        return String.format(Locale.ENGLISH, "Job %d: %s\t%s", jobInfo.getId(), path, description);
    }

    public static void onReceive(Context context, Intent intent) {
        final String scriptPath = intent.getStringExtra("script");

        final int jobId = intent.getIntExtra("job_id", 0);
        final boolean pending = intent.getBooleanExtra("pending", false);
        final boolean cancel = intent.getBooleanExtra("cancel", false);
        final boolean cancelAll = intent.getBooleanExtra("cancel_all", false);
        final int periodicMillis = intent.getIntExtra("period_ms", 0);
        // String networkType = intent.getStringExtra("network");
        final boolean batteryNotLow = intent.getBooleanExtra("battery_not_low", false);
        final boolean charging = intent.getBooleanExtra("charging", false);
        final boolean persisted = intent.getBooleanExtra("persisted", false);
        final boolean idle = intent.getBooleanExtra("idle", false);
        final boolean storageNotLow = intent.getBooleanExtra("storage_not_low", false);

        /*
        int networkTypeCode = switch (networkType) {
            case "any" -> JobInfo.NETWORK_TYPE_ANY;
            case "unmetered" -> JobInfo.NETWORK_TYPE_UNMETERED;
            case "cellular" -> JobInfo.NETWORK_TYPE_CELLULAR;
            case "not_roaming" -> JobInfo.NETWORK_TYPE_NOT_ROAMING;
            case null -> JobInfo.NETWORK_TYPE_ANY;
            default -> JobInfo.NETWORK_TYPE_NONE;
        };
         */

        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        if (pending) {
            displayPendingJobs(intent, jobScheduler);
            return;
        } else if (cancelAll) {
            ResultReturner.returnData(intent, out -> out.println("Cancelling all jobs"));
            jobScheduler.cancelAll();
            return;
        } else if (cancel) {
            cancelJob(intent, jobScheduler, jobId);
            return;
        } else if (scriptPath == null) {
            ResultReturner.returnData(intent, out -> out.println("No script path given"));
            return;
        }

        // Schedule new job.
        var file = new File(scriptPath);
        final String fileCheckMsg;
        if (!file.isFile()) {
            fileCheckMsg = "No such file: %s";
        } else if (!file.canRead()) {
            fileCheckMsg = "Cannot read file: %s";
        } else if (!file.canExecute()) {
            fileCheckMsg = "Cannot execute file: %s";
        } else {
            fileCheckMsg = null;
        }
        if (fileCheckMsg != null) {
            ResultReturner.returnData(intent, out -> out.println(String.format(fileCheckMsg, scriptPath)));
            return;
        }

        var extras = new PersistableBundle();
        extras.putString(JobSchedulerService.SCRIPT_FILE_PATH, file.getAbsolutePath());

        var serviceComponent = new ComponentName(context, JobSchedulerService.class);
        var builder = new JobInfo.Builder(jobId, serviceComponent)
                .setExtras(extras)
                // Requires ACCESS_NETWORK_STATE permission:
                // .setRequiredNetworkType(networkTypeCode)
                .setRequiresCharging(charging)
                .setPersisted(persisted)
                .setRequiresDeviceIdle(idle)
                .setRequiresBatteryNotLow(batteryNotLow)
                .setRequiresStorageNotLow(storageNotLow);
        if (periodicMillis > 0) {
            builder = builder.setPeriodic(periodicMillis);
        }

        var job = builder.build();
        var scheduleResponse = jobScheduler.schedule(job);
        var message = String.format(Locale.ENGLISH, "Scheduling %s - response %d", formatJobInfo(job), scheduleResponse);
        ResultReturner.returnData(intent, out -> out.println(message));
    }

    private static void displayPendingJobs(Intent intent, JobScheduler jobScheduler) {
        var jobs = jobScheduler.getAllPendingJobs();
        if (jobs.isEmpty()) {
            ResultReturner.returnData(intent, out -> out.println("No pending jobs"));
            return;
        }
        var stringBuilder = new StringBuilder();
        for (JobInfo job : jobs) {
            stringBuilder.append(String.format(Locale.ENGLISH, "Pending %s\n", formatJobInfo(job)));
        }
        ResultReturner.returnData(intent, out -> out.println(stringBuilder));
    }

    private static void cancelJob(Intent intent, JobScheduler jobScheduler, int jobId) {
        var jobInfo = jobScheduler.getPendingJob(jobId);
        if (jobInfo == null) {
            ResultReturner.returnData(intent, out -> out.println(String.format(Locale.ENGLISH, "No job %d found", jobId)));
        } else {
            jobScheduler.cancel(jobId);
            ResultReturner.returnData(intent, out -> out.println(String.format(Locale.ENGLISH, "Cancelling %s", formatJobInfo(jobInfo))));
        }
    }

    public static class JobSchedulerService extends JobService {

        public static final String SCRIPT_FILE_PATH = "com.termux.api.jobscheduler_script_path";

        @Override
        public boolean onStartJob(JobParameters params) {
            PersistableBundle extras = params.getExtras();
            String filePath = extras.getString(SCRIPT_FILE_PATH);
            if (filePath == null) {
                Log.e(LOG_TAG, "onStartJob(): filePath is null");
                return false;
            }

            var context = getApplicationContext();
            var executeIntent = new Intent(TermuxService.ACTION_SERVICE_EXECUTE)
                .setData(Uri.fromFile(new File(filePath)))
                .setClass(context, TermuxService.class)
                .putExtra(TermuxService.TERMUX_EXECUTE_EXTRA_BACKGROUND, true);

            context.startForegroundService(executeIntent);
            return false;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return false;
        }
    }

}
