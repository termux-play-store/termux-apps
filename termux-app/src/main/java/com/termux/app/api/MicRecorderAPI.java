package com.termux.app.api;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED;
import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED;

/**
 * API that enables recording to a file via the built-in microphone
 */
public class MicRecorderAPI {

    public static void onReceive(final Context context, final Intent intent) {
        var recorderService = new Intent(context, MicRecorderService.class)
            .setAction(intent.getAction());
        var extras = intent.getExtras();
        if (extras != null) {
            recorderService.putExtras(extras);
        }
        context.startService(recorderService);
    }

    public static class MicRecorderService extends Service implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
        protected static final int MIN_RECORDING_LIMIT = 1000;

        // default max recording duration in seconds
        protected static final int DEFAULT_RECORDING_LIMIT = (1000 * 60 * 15);

        protected static MediaRecorder mediaRecorder;

        // are we currently recording using the microphone?
        protected static boolean isRecording;

        // file we're recording too
        protected static File file;

        private static final String LOG_TAG = "MicRecorderService";

        public void onCreate() {
            getMediaRecorder(this);
        }

        public int onStartCommand(Intent intent, int flags, int startId) {
            var command = intent.getAction();
            var context = getApplicationContext();
            var handler = getRecorderCommandHandler(command);
            var result = handler.handle(context, intent);
            postRecordCommandResult(intent, result);
            return Service.START_NOT_STICKY;
        }

        private static RecorderCommandHandler getRecorderCommandHandler(final String command) {
            return switch (command) {
                case "info" -> infoHandler;
                case "record" -> recordHandler;
                case "quit" -> quitHandler;
                case null, default -> (context, intent) -> {
                    RecorderCommandResult result = new RecorderCommandResult();
                    result.error = "Unknown command: " + command;
                    if (!isRecording) {
                        context.stopService(intent);
                    }
                    return result;
                };
            };
        }

        private static void postRecordCommandResult(final Intent intent,
                                                    final RecorderCommandResult result) {
            ResultReturner.returnData(intent, out -> {
                out.append(result.message).append("\n");
                if (result.error != null) {
                    out.append(result.error).append("\n");
                }
                out.flush();
                out.close();
            });
        }

        /**
         * Returns our MediaPlayer instance and ensures it has all the necessary callbacks
         */
        protected static void getMediaRecorder(MicRecorderService service) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setOnErrorListener(service);
            mediaRecorder.setOnInfoListener(service);
        }

        public void onDestroy() {
            cleanupMediaRecorder();
            Log.e(LOG_TAG, "onDestroy");
        }

        /**
         * Releases MediaRecorder resources
         */
        protected static void cleanupMediaRecorder() {
            if (isRecording) {
                mediaRecorder.stop();
                isRecording = false;
            }
            mediaRecorder.reset();
            mediaRecorder.release();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            isRecording = false;
            this.stopSelf();
        }

        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            switch (what) {
                case MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED: // intentional fallthrough
                case MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    this.stopSelf();
            }
        }

        protected static String getDefaultRecordingFilename() {
            var dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH);
            var date = new Date();
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/TermuxAudioRecording_" + dateFormat.format(date);
        }

        protected static String getRecordingInfoJSONString() {
            String result = "";
            JSONObject info = new JSONObject();
            try {
                info.put("isRecording", isRecording);
                if (isRecording) {
                    info.put("outputFile", file.getAbsolutePath());
                }
                result = info.toString(2);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "infoHandler json error", e);
            }
            return result;
        }

        static final RecorderCommandHandler infoHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();
                result.message = getRecordingInfoJSONString();
                if (!isRecording) {
                    context.stopService(intent);
                }
                return result;
            }
        };

        static final RecorderCommandHandler recordHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();

                int duration = intent.getIntExtra("limit", DEFAULT_RECORDING_LIMIT);
                // allow the duration limit to be disabled with zero or negative
                if (duration > 0 && duration < MIN_RECORDING_LIMIT)
                    duration = MIN_RECORDING_LIMIT;

                var encoderString = intent.getStringExtra("encoder");
                var encoder = switch (encoderString == null ? null : encoderString.toLowerCase(Locale.ROOT)) {
                    case "amr_nb" -> MediaRecorder.AudioEncoder.AMR_NB;
                    case "amr_wb" -> MediaRecorder.AudioEncoder.AMR_WB;
                    case "opus" -> MediaRecorder.AudioEncoder.OPUS;
                    case null, default -> MediaRecorder.AudioEncoder.AAC;
                };

                int format = intent.getIntExtra("format", MediaRecorder.OutputFormat.DEFAULT);
                if (format == MediaRecorder.OutputFormat.DEFAULT) {
                    format = switch (encoder) {
                        case MediaRecorder.AudioEncoder.AMR_NB, MediaRecorder.AudioEncoder.AMR_WB -> MediaRecorder.OutputFormat.THREE_GPP;
                        case MediaRecorder.AudioEncoder.OPUS -> MediaRecorder.OutputFormat.OGG;
                        default -> MediaRecorder.OutputFormat.MPEG_4;
                    };
                }

                var extension = switch (format) {
                    case MediaRecorder.OutputFormat.MPEG_4 -> ".m4a";
                    case MediaRecorder.OutputFormat.THREE_GPP -> ".3gp";
                    case MediaRecorder.OutputFormat.OGG -> ".ogg";
                    default -> "";
                };

                String filename = intent.hasExtra("file") ? intent.getStringExtra("file") : getDefaultRecordingFilename() + extension;
                int source = intent.getIntExtra("source", MediaRecorder.AudioSource.MIC);
                int bitrate = intent.getIntExtra("bitrate", 0);
                int srate = intent.getIntExtra("srate", 0);
                int channels = intent.getIntExtra("channels", 0);

                if (isRecording) {
                    result.error = "Recording already in progress!";
                } else {
                    var newFile = new File(filename);
                    if (newFile.exists()) {
                        result.error = String.format("File: %s already exists! Please specify a different filename", newFile.getName());
                    } else {
                        file = newFile;
                        try {
                            mediaRecorder.setAudioSource(source);
                            mediaRecorder.setOutputFormat(format);
                            mediaRecorder.setAudioEncoder(encoder);
                            mediaRecorder.setOutputFile(filename);
                            mediaRecorder.setMaxDuration(duration);
                            if (bitrate > 0) {
                                mediaRecorder.setAudioEncodingBitRate(bitrate);
                            }
                            if (srate > 0) {
                                mediaRecorder.setAudioSamplingRate(srate);
                            }
                            if (channels > 0) {
                                mediaRecorder.setAudioChannels(channels);
                            }
                            mediaRecorder.prepare();
                            mediaRecorder.start();
                            isRecording = true;
                            result.message = String.format("Recording started: %s \nMax Duration: %s",
                                file.getAbsolutePath(),
                                duration <= 0 ?
                                    "unlimited" :
                                    MediaPlayerAPI.getTimeString(duration /
                                        1000));

                        } catch (IllegalStateException | IOException e) {
                            Log.e(LOG_TAG, "MediaRecorder error", e);
                            result.error = "Recording error: " + e.getMessage();
                        }
                    }
                }
                if (!isRecording) {
                    context.stopService(intent);
                }
                return result;
            }
        };

        static final RecorderCommandHandler quitHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();
                if (isRecording) {
                    result.message = "Recording finished: " + file.getAbsolutePath();
                } else {
                    result.message = "No recording to stop";
                }
                context.stopService(intent);
                return result;
            }
        };
    }

    /**
     * Interface for handling recorder commands
     */
    public interface RecorderCommandHandler {
        RecorderCommandResult handle(final Context context, final Intent intent);
    }

    /**
     * Simple POJO to store result of executing a Recorder command
     */
    static class RecorderCommandResult {
        public String message = "";
        public String error;
    }
}
