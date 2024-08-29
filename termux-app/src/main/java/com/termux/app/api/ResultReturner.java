package com.termux.app.api;

import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.ParcelFileDescriptor;
import android.util.JsonWriter;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public abstract class ResultReturner {

    private static final String LOG_TAG = "ResultReturner";

    /**
     * An extra intent parameter which specifies a linux abstract namespace socket address where output from the API
     * call should be written.
     */
    static final String SOCKET_OUTPUT_EXTRA = "socket_output";

    /**
     * An extra intent parameter which specifies a linux abstract namespace socket address where input to the API call
     * can be read from.
     */
    static final String SOCKET_INPUT_EXTRA = "socket_input";

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
     * Possible subclass of {@link ResultWriter} when the output is binary data instead of text.
     */
    public static abstract class BinaryOutput implements ResultWriter {
        private OutputStream out;
        
        public void setOutput(OutputStream outputStream) {
            this.out = outputStream;
        }
        
        public abstract void writeResult(OutputStream out) throws Exception;
    
        /**
         * writeResult with a PrintWriter is marked as final and overwritten, so you don't accidentally use it
         */
        public final void writeResult(PrintWriter unused) throws Exception {
            writeResult(out);
            out.flush();
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

    public static abstract class WithAncillaryFd implements ResultWriter {
        private LocalSocket outputSocket = null;
        private final ParcelFileDescriptor[] pfds = { null };

        public final void setOutputSocketForFds(LocalSocket outputSocket) {
            this.outputSocket = outputSocket;
        }

        public final void sendFd(PrintWriter out, int fd) {
            // If fd already sent, then error out as we only support sending one currently.
            if (this.pfds[0] != null) {
                Log.e(LOG_TAG, "File descriptor already sent", new Exception());
                return;
            }

            this.pfds[0] = ParcelFileDescriptor.adoptFd(fd);
            FileDescriptor[] fds = { pfds[0].getFileDescriptor() };

            // Set fd to be sent
            outputSocket.setFileDescriptorsForSend(fds);

            // As per the docs:
            // > The file descriptors will be sent with the next write of normal data, and will be
            //   delivered in a single ancillary message.
            // - https://developer.android.com/reference/android/net/LocalSocket#setFileDescriptorsForSend(java.io.FileDescriptor[])
            // So we write the `@` character. It is not special, it is just the chosen character
            // expected as the message by the native `termux-api` command when a fd is sent.
            // - https://github.com/termux/termux-api-package/blob/e62bdadea3f26b60430bb85248f300fee68ecdcc/termux-api.c#L358
            out.print("@");

            // Actually send the by fd by flushing the data previously written (`@`) as PrintWriter is buffered.
            out.flush();

            // Clear existing fd after it has been sent, otherwise it will get sent for every data write,
            // even though we are currently not writing anything else. Android will not clear it automatically.
            // - https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/net/LocalSocketImpl.java;l=523?q=setFileDescriptorsForSend
            // - https://cs.android.com/android/_/android/platform/frameworks/base/+/refs/tags/android-14.0.0_r1:core/jni/android_net_LocalSocketImpl.cpp;l=194
            outputSocket.setFileDescriptorsForSend(null);
        }

        public final void cleanupFds() {
            if (this.pfds[0] != null) {
                try {
                    this.pfds[0].close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to close file descriptor", e);
                }
            }
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
    public static void noteDone(final Intent intent) {
        returnData(intent, null);
    }

    public static void copyIntentExtras(Intent origIntent, Intent newIntent) {
        newIntent.putExtra("api_method", origIntent.getStringExtra("api_method"));
        newIntent.putExtra(SOCKET_OUTPUT_EXTRA, origIntent.getStringExtra(SOCKET_OUTPUT_EXTRA));
        newIntent.putExtra(SOCKET_INPUT_EXTRA, origIntent.getStringExtra(SOCKET_INPUT_EXTRA));
    }

    /**
     * Run in a separate thread, unless the context is an IntentService.
     */
    public static void returnData(final Intent intent, final ResultWriter resultWriter) {
        final Runnable runnable = () -> {
            PrintWriter writer = null;
            LocalSocket outputSocket = null;
            try {
                outputSocket = new LocalSocket();
                String outputSocketAddress = intent.getStringExtra(SOCKET_OUTPUT_EXTRA);
                if (outputSocketAddress == null || outputSocketAddress.isEmpty()) {
                    throw new IOException("Missing '" + SOCKET_OUTPUT_EXTRA + "' extra");
                }
                outputSocket.connect(new LocalSocketAddress(outputSocketAddress));
                writer = new PrintWriter(outputSocket.getOutputStream());

                if (resultWriter != null) {
                    if (resultWriter instanceof WithAncillaryFd) {
                        ((WithAncillaryFd) resultWriter).setOutputSocketForFds(outputSocket);
                    }
                    if (resultWriter instanceof BinaryOutput) {
                        BinaryOutput bout = (BinaryOutput) resultWriter;
                        bout.setOutput(outputSocket.getOutputStream());
                    }
                    if (resultWriter instanceof WithInput) {
                        try (LocalSocket inputSocket = new LocalSocket()) {
                            String inputSocketAdress = intent.getStringExtra(SOCKET_INPUT_EXTRA);
                            if (inputSocketAdress == null || inputSocketAdress.isEmpty())
                                throw new IOException("Missing '" + SOCKET_INPUT_EXTRA + "' extra");
                            inputSocket.connect(new LocalSocketAddress(inputSocketAdress));
                            ((WithInput) resultWriter).setInput(inputSocket.getInputStream());
                            resultWriter.writeResult(writer);
                        }
                    } else {
                        resultWriter.writeResult(writer);
                    }


                    if (resultWriter instanceof WithAncillaryFd) {
                        ((WithAncillaryFd) resultWriter).cleanupFds();
                    }
                }
            } catch (Throwable t) {
                String message = "Error in " + LOG_TAG;
                Log.e(LOG_TAG, message, t);
            } finally {
                try {
                    if (writer != null) {
                        writer.close();
                    }
                    if (outputSocket != null) {
                        outputSocket.close();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Failed to close", e);
                }
            }
        };

        new Thread(runnable).start();
    }

}
