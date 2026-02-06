package com.termux.app.api;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;

public class SpeechToTextAPI {

    private static final String LOG_TAG = "SpeechToTextAPI";

    public static class SpeechToTextService extends Service {

        private static final String STOP_ELEMENT = "";

        protected SpeechRecognizer mSpeechRecognizer;
        final LinkedBlockingQueue<String> queueu = new LinkedBlockingQueue<>();

        private final IBinder binder = new LocalBinder();

        public class LocalBinder extends Binder {
            SpeechToTextService getService() {
                return SpeechToTextService.this;
            }
        }


        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return binder;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            final Context context = this;

            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

            mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onRmsChanged(float rmsdB) {
                    // Do nothing.
                }

                @Override
                public void onResults(Bundle results) {
                    var recognitions = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (recognitions != null && !recognitions.isEmpty()) {
                        // The first element is the most likely candidate:
                        queueu.add(recognitions.get(0));
                    }
                    queueu.add(STOP_ELEMENT);
                }

                @Override
                public void onReadyForSpeech(Bundle params) {
                    // Do nothing.
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    // Do nothing.
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Do nothing.
                }

                @Override
                public void onError(int error) {
                    var description = SpeechToTextAPI.errorToString(error);
                    Log.e(LOG_TAG, "RecognitionListener#onError: " + description);
                    queueu.add("ERROR: " + description);
                    queueu.add(STOP_ELEMENT);
                }

                @Override
                public void onEndOfSpeech() {
                    // Do nothing.
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Do nothing.
                }

                @Override
                public void onBeginningOfSpeech() {
                    // Do nothing.
                }
            });

            var pm = context.getPackageManager();
            var installedList = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);

            if (installedList.isEmpty()) {
                queueu.add("ERROR: No speech to text app found");
                queueu.add(SpeechToTextService.STOP_ELEMENT);
            } else {
                var recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Enter shell command")
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                mSpeechRecognizer.startListening(recognizerIntent);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mSpeechRecognizer.destroy();
            Log.d(LOG_TAG, "SpeechToTextService#onDestroy");
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            ResultReturner.returnData(intent, new ResultReturner.WithInput() {
                @Override
                public void writeResult(PrintWriter out) throws Exception {
                    while (true) {
                        Object s = queueu.take();
                        if (s == STOP_ELEMENT) {
                            SpeechToTextService.this.stopSelf();
                            return;
                        } else {
                            out.println(s);
                        }
                    }
                }
            });
            return Service.START_NOT_STICKY;
        }
    }

    @NonNull
    static String errorToString(int error) {
        return switch (error) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT";
            case SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK";
            case SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO";
            case SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER";
            case SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE";
            case SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH";
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED";
            case SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT";
            case SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED";
            default -> "ERROR_UNKNOWN_" + error;
        };
    }

    public static void onReceive(final Context context, Intent intent) {
        var extras = intent.getExtras();
        if (extras == null) {
            Log.e(LOG_TAG, "No input extras");
        } else {
            var startIntent = new Intent(context, SpeechToTextService.class)
                .putExtras(extras);
            context.startService(startIntent);
        }
    }

}
