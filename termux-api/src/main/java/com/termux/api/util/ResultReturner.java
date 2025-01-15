package com.termux.api.util;

import android.app.Activity;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.JsonWriter;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public abstract class ResultReturner {

    private static final String LOG_TAG = "ResultReturner";

    /**
     * An extra intent parameter which specifies a file descriptor where output from the API
     * call should be written.
     */
    private static final String SOCKET_OUTPUT_EXTRA = "com.termux.api.output_socket_fd";

    /**
     * An extra intent parameter which specifies a file descriptor input to the API call
     * can be read from.
     */
    private static final String SOCKET_INPUT_EXTRA = "com.termux.api.input_socket_fd";

    public interface ResultWriter {
        void writeResult(PrintWriter out) throws Exception;
    }

    /**
     * Possible subclass of {@link ResultWriter} when input is to be read from stdin.
     */
    public static abstract class WithInput implements ResultWriter {
        protected InputStream in;

        public void setInput(InputStream inputStream) throws Exception {
            this.in = inputStream;
        }
    }
    
    /**
     * Possible marker interface for a {@link ResultWriter} when input is to be read from stdin.
     */
    public static abstract class WithStringInput extends WithInput {
        protected String inputString;

        protected boolean trimInput() {
            return true;
        }

        @Override
        public final void setInput(InputStream inputStream) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int l;
            while ((l = inputStream.read(buffer)) > 0) {
                baos.write(buffer, 0, l);
            }
            inputString = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            if (trimInput()) inputString = inputString.trim();
        }
    }

    public static abstract class ResultJsonWriter implements ResultWriter {
        @Override
        public final void writeResult(PrintWriter out) throws Exception {
            JsonWriter writer = new JsonWriter(out);
            writer.setIndent("  ");
            writeJson(writer);
            out.println(); // To add trailing newline.
        }

        public abstract void writeJson(JsonWriter out) throws Exception;
    }

    /**
     * Just tell termux-api.c that we are done.
     */
    public static void noteDone(Context context, final Intent intent) {
        returnData(context, intent, null);
    }

    public static void copyIntentExtras(Intent origIntent, Intent newIntent) {
        newIntent.putExtra("api_method", origIntent.getStringExtra("api_method"));
        newIntent.putExtra(SOCKET_OUTPUT_EXTRA, origIntent.getStringExtra(SOCKET_OUTPUT_EXTRA));
        newIntent.putExtra(SOCKET_INPUT_EXTRA, origIntent.getStringExtra(SOCKET_INPUT_EXTRA));

    }

    /**
     * Run in a separate thread, unless the context is an IntentService.
     */
    public static void returnData(Object context, final Intent intent, final ResultWriter resultWriter) {
        final PendingResult asyncResult = (context instanceof BroadcastReceiver) ? ((BroadcastReceiver) context)
                .goAsync() : null;
        final Activity activity = (Activity) ((context instanceof Activity) ? context : null);

        final Runnable runnable = () -> {
            PrintWriter writer = null;
            ParcelFileDescriptor outFd = null;
            ParcelFileDescriptor inFd = null;
            try {
                outFd = intent.getParcelableExtra(SOCKET_OUTPUT_EXTRA);
                if (outFd == null) {
                    throw new IOException("Missing '" + SOCKET_OUTPUT_EXTRA + "' extra");
                }
                var outStream = new FileOutputStream(outFd.getFileDescriptor());
                writer = new PrintWriter(outStream);

                if (resultWriter != null) {
                    inFd = intent.getParcelableExtra(SOCKET_INPUT_EXTRA);
                    if (inFd == null) {
                        throw new IOException("Missing '" + SOCKET_INPUT_EXTRA + "' extra");
                    }
                    if (resultWriter instanceof WithInput) {
                        var inStream = new FileInputStream(inFd.getFileDescriptor());
                        ((WithInput) resultWriter).setInput(inStream);
                    } else {
                        inFd.close();
                    }

                    resultWriter.writeResult(writer);
                }

                if (asyncResult != null) {
                    asyncResult.setResultCode(0);
                } else if (activity != null) {
                    activity.setResult(0);
                }
            } catch (Throwable t) {
                String message = "Error in " + LOG_TAG;
                Log.e(LOG_TAG, message, t);

                if (asyncResult != null) {
                    asyncResult.setResultCode(1);
                } else if (activity != null) {
                    activity.setResult(1);
                }
            } finally {
                try {
                    if (writer != null) {
                        writer.close();
                    }
                    if (outFd != null) {
                        outFd.close();
                    }
                    if (inFd != null) {
                        inFd.close();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Failed to close", e);
                }

                try {
                    if (asyncResult != null) {
                        asyncResult.finish();
                    } else if (activity != null) {
                        activity.finish();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Failed to finish", e);
                }
            }
        };

        if (context instanceof IntentService) {
            runnable.run();
        } else {
            new Thread(runnable).start();
        }
    }

}
