package com.termux.app.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.JsonWriter;

public class AudioAPI {

    public static void onReceive(final Context context, Intent intent) {
        var am = context.getSystemService(AudioManager.class);
        var sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        var framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        var bluetootha2dp = am.isBluetoothA2dpOn();
        var wiredhs = am.isWiredHeadsetOn();

        final int sr, bs, sr_ll, bs_ll, sr_ps, bs_ps;
        var at = new AudioTrack.Builder()
            .setBufferSizeInBytes(4) // one 16bit 2ch frame
            .build();
        sr = at.getSampleRate();
        bs = at.getBufferSizeInFrames();
        at.release();

        at = new AudioTrack.Builder()
            .setBufferSizeInBytes(4) // one 16bit 2ch frame
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build();
        sr_ll = at.getSampleRate();
        bs_ll = at.getBufferSizeInFrames();
        at.release();

        at = new AudioTrack.Builder()
            .setBufferSizeInBytes(4) // one 16bit 2ch frame
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            .build();
        sr_ps = at.getSampleRate();
        bs_ps = at.getBufferSizeInFrames();
        at.release();

        ResultReturner.returnData(intent, new ResultReturner.ResultJsonWriter() {
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("PROPERTY_OUTPUT_SAMPLE_RATE").value(sampleRate);
                out.name("PROPERTY_OUTPUT_FRAMES_PER_BUFFER").value(framesPerBuffer);
                out.name("AUDIOTRACK_SAMPLE_RATE").value(sr);
                out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES").value(bs);
                if (sr_ll != sr || bs_ll != bs) { // all or nothing
                    out.name("AUDIOTRACK_SAMPLE_RATE_LOW_LATENCY").value(sr_ll);
                    out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES_LOW_LATENCY").value(bs_ll);
                }
                if (sr_ps != sr || bs_ps != bs) { // all or nothing
                    out.name("AUDIOTRACK_SAMPLE_RATE_POWER_SAVING").value(sr_ps);
                    out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES_POWER_SAVING").value(bs_ps);
                }
                out.name("BLUETOOTH_A2DP_IS_ON").value(bluetootha2dp);
                out.name("WIREDHEADSET_IS_CONNECTED").value(wiredhs);
                out.endObject();
            }
        });
    }

}
