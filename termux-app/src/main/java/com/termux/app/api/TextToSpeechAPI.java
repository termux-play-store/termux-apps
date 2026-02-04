package com.termux.app.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.speech.tts.UtteranceProgressListener;
import android.util.JsonWriter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TextToSpeechAPI {

    private static final String LOG_TAG = "TextToSpeechAPI";

    public static void onReceive(final Context context, Intent intent) {
        var speechLanguage = intent.getStringExtra("language");
        var speechRegion = intent.getStringExtra("region");
        var speechVariant = intent.getStringExtra("variant");
        var speechEngine = intent.getStringExtra("engine");
        var speechPitch = intent.getFloatExtra("pitch", 1.0f);

        String streamToUseString = intent.getStringExtra("stream");
        int streamToUse = switch (streamToUseString) {
            case "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION;
            case "ALARM" -> AudioManager.STREAM_ALARM;
            case "MUSIC" -> AudioManager.STREAM_MUSIC;
            case "RING" -> AudioManager.STREAM_RING;
            case "SYSTEM" -> AudioManager.STREAM_SYSTEM;
            case "VOICE_CALL" -> AudioManager.STREAM_VOICE_CALL;
            // STREAM_MUSIC is the default audio stream for TTS, see:
            // http://stackoverflow.com/questions/6877272/what-is-the-default-audio-stream-of-tts/6979025#6979025
            case null, default -> AudioManager.STREAM_MUSIC;
        };

        var mTtsLatch = new CountDownLatch(1);

        var mTts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                mTtsLatch.countDown();
            } else {
                Log.e(LOG_TAG, "Failed tts initialization: status=" + status);
                //stopmelf();
            }
        }, speechEngine);

        ResultReturner.returnData(intent, new ResultReturner.WithInput() {
            @Override
            public void writeResult(PrintWriter out) {
                try {
                    try {
                        if (!mTtsLatch.await(10, TimeUnit.SECONDS)) {
                            Log.e(LOG_TAG, "Timeout waiting for TTS initialization");
                            return;
                        }
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "Interrupted awaiting TTS initialization");
                        return;
                    }

                    if ("LIST_AVAILABLE".equals(speechEngine)) {
                        try (JsonWriter writer = new JsonWriter(out)) {
                            writer.setIndent("  ");
                            String defaultEngineName = mTts.getDefaultEngine();
                            writer.beginArray();
                            for (EngineInfo info : mTts.getEngines()) {
                                writer.beginObject();
                                writer.name("name").value(info.name);
                                writer.name("label").value(info.label);
                                writer.name("default").value(defaultEngineName.equals(info.name));
                                writer.endObject();
                            }
                            writer.endArray();
                        }
                        out.println();
                        return;
                    }

                    final AtomicInteger ttsDoneUtterancesCount = new AtomicInteger();

                    mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            // Ignore.
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.e(LOG_TAG, "UtteranceProgressListener.onError() called");
                            synchronized (ttsDoneUtterancesCount) {
                                ttsDoneUtterancesCount.incrementAndGet();
                                ttsDoneUtterancesCount.notify();
                            }
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            synchronized (ttsDoneUtterancesCount) {
                                ttsDoneUtterancesCount.incrementAndGet();
                                ttsDoneUtterancesCount.notify();
                            }
                        }
                    });

                    if (speechLanguage != null) {
                        int setLanguageResult = mTts.setLanguage(getLocale(speechLanguage, speechRegion, speechVariant));
                        if (setLanguageResult != TextToSpeech.LANG_AVAILABLE) {
                            Log.e(LOG_TAG, "tts.setLanguage('" + speechLanguage + "') returned " + setLanguageResult);
                        }
                    }

                    mTts.setPitch(speechPitch);
                    mTts.setSpeechRate(intent.getFloatExtra("rate", 1.0f));

                    String utteranceId = "utterance_id";
                    Bundle params = new Bundle();
                    params.putInt(Engine.KEY_PARAM_STREAM, streamToUse);
                    params.putString(Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

                    int submittedUtterances = 0;

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isEmpty()) {
                                submittedUtterances++;
                                mTts.speak(line, TextToSpeech.QUEUE_ADD, params, utteranceId);
                            }
                        }
                    }

                    synchronized (ttsDoneUtterancesCount) {
                        while (ttsDoneUtterancesCount.get() != submittedUtterances) {
                            ttsDoneUtterancesCount.wait();
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "TTS error", e);
                }
            }
        });
    }

        /*
        public void onDestroy() {
            if (mTts != null) {
                mTts.shutdown();
            }
            super.onDestroy();
        }
         */

    private static Locale getLocale(String language, String region, String variant) {
        Locale result;
        if (region != null) {
            if (variant != null) {
                result = new Locale(language, region, variant);
            } else {
                result = new Locale(language, region);
            }
        } else {
            result = new Locale(language);
        }
        return result;
    }
}
