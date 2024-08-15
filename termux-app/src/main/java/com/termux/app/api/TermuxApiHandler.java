package com.termux.app.api;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.Os;
import android.text.TextUtils;
import android.util.JsonWriter;
import android.util.Log;

import androidx.annotation.NonNull;

import com.termux.app.TermuxConstants;
import com.termux.app.TermuxPermissionUtils;
import com.termux.app.TermuxService;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class TermuxApiHandler {

    private final TermuxService mTermuxService;

    /**
     * Messenger for communicating with the service.
     */
    Messenger mMessenger = null;

    boolean mServiceBindingInitiated = false;

    /**
     * Flag indicating whether we have called bind on the service.
     */
    boolean mServiceConnected;

    public void sendToTermuxApi(Intent intent) {
        ParcelFileDescriptor parcelOut;
        ParcelFileDescriptor parcelIn;
        try {
            String outputSocketAdress = intent.getStringExtra(ResultReturner.SOCKET_OUTPUT_EXTRA);
            if (outputSocketAdress == null || outputSocketAdress.isEmpty()) {
                throw new IOException("Missing '" + ResultReturner.SOCKET_OUTPUT_EXTRA + "' extra");
            }
            var outputSocket = new LocalSocket();
            outputSocket.connect(new LocalSocketAddress(outputSocketAdress));
            var outFileDescriptor = outputSocket.getFileDescriptor();
            parcelOut = ParcelFileDescriptor.dup(outFileDescriptor);
            intent.putExtra("com.termux.api.output_socket_fd", parcelOut);
            Os.close(outFileDescriptor);

            String inputSocketAddress = intent.getStringExtra(ResultReturner.SOCKET_INPUT_EXTRA);
            if (inputSocketAddress == null || inputSocketAddress.isEmpty()) {
                throw new IOException("Missing '" + ResultReturner.SOCKET_INPUT_EXTRA + "' extra");
            }
            var inputSocket = new LocalSocket();
            inputSocket.connect(new LocalSocketAddress(inputSocketAddress));
            var inFileDescriptor = inputSocket.getFileDescriptor();
            parcelIn = ParcelFileDescriptor.dup(inFileDescriptor);
            intent.putExtra("com.termux.api.input_socket_fd", parcelIn);
            Os.close(inFileDescriptor);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        if (!mServiceConnected) {
            try (var out = new PrintWriter(new FileOutputStream(parcelOut.getFileDescriptor()))) {
                if (tryBindIfNecessary()) {
                    out.println("Establishing connection to Termux:API - try again in a few seconds");
                } else {
                    out.println("Termux:API is not yet available on Google Play - see https://github.com/termux-play-store/termux-apps/issues/29 for updates");
                }
            }
            Log.e(TermuxConstants.LOG_TAG, "Not bound to Termux:API yet, ignoring intent");
        } else {
            var msg = Message.obtain(null, 1, 0, 0, intent);
            try {
                mMessenger.send(msg);
            } catch (RemoteException e) {
                Log.e(TermuxConstants.LOG_TAG, "Exception sending message to termux-api", e);
            }
        }

        tryClose(parcelOut);
        tryClose(parcelIn);
    }

    private static void tryClose(@NonNull Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            Log.e(TermuxConstants.LOG_TAG, "Unable to close API file descriptor", e);
        }
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.e(TermuxConstants.LOG_TAG, "Connected to Termux:API service");
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mMessenger = new Messenger(service);
            mServiceConnected = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e(TermuxConstants.LOG_TAG, "Disconnected from Termux:API service");
            // This is called when the connection with the service has been
            // unexpectedly disconnected&mdash;that is, its process crashed.
            mMessenger = null;
            mServiceBindingInitiated = false;
            mServiceConnected = false;
        }
    };


    public TermuxApiHandler(TermuxService service) {
        mTermuxService = service;
    }

    public void handleApiIntent(Context context, Intent intent, String apiMethod) {
        try {
            // Handle some useful methods (or methods hard to have in a separate app
            // without sharedUserId) that does not require extra permissions
            // ourselves, and call out to Termux:API in the default case.
            switch (apiMethod) {
                case "AudioInfo":
                    AudioAPI.onReceive(context, intent);
                    break;
                case "BatteryStatus":
                    BatteryStatusAPI.onReceive(context, intent);
                    break;
                case "Clipboard":
                    ClipboardApi.onReceive(context, intent);
                    break;
                case "Dialog":
                    DialogAPI.onReceive(context, intent);
                    break;
                case "Download":
                    DownloadAPI.onReceive(context, intent);
                    break;
                case "Keystore":
                    KeystoreAPI.onReceive(intent);
                    break;
                case "MediaScanner":
                    MediaScannerAPI.onReceive(context, intent);
                    break;
                case "SAF":
                    SAFAPI.onReceive(context, intent);
                    break;
                case "Share":
                    ShareAPI.onReceive(context, intent);
                    break;
                case "StorageGet":
                    StorageGetAPI.onReceive(context, intent);
                    break;
                case "Toast":
                    ToastAPI.onReceive(context, intent);
                    break;
                case "Usb":
                    UsbAPI.onReceive(context, intent);
                    break;
                case "Vibrate":
                    VibrateAPI.onReceive(context, intent);
                    break;
                case "Volume":
                    VolumeAPI.onReceive(context, intent);
                    break;
                default:
                    sendToTermuxApi(intent);
                    break;
            }
        } catch (Exception e) {
            Log.e(TermuxConstants.LOG_TAG, "Exception in handleApiIntent()", e);
        }
    }

    public boolean tryBindIfNecessary() {
        if (mServiceBindingInitiated) {
            Log.e(TermuxConstants.LOG_TAG, "Already started bind, no need to bind again");
            return true;
        }
        Log.e(TermuxConstants.LOG_TAG, "Trying to bind to Termux:API...");
        try {
            mServiceBindingInitiated = mTermuxService.bindService(new Intent().setClassName("com.termux.api", "com.termux.api.TermuxApiService"),
                mConnection,
                Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TermuxConstants.LOG_TAG, "Exception binding to Termux:API service", e);
        }
        if (!mServiceBindingInitiated) {
            Log.w(TermuxConstants.LOG_TAG, "Could not bind to Termux:API");
        }
        return mServiceBindingInitiated;
    }

    public void onDestroy() {
        if (mServiceConnected) {
            mTermuxService.unbindService(mConnection);
            mServiceConnected = false;
        }
    }

    /**
     * Check for and request permissions if necessary.
     *
     * @return if all permissions were already granted
     */
    public static boolean checkAndRequestPermission(Activity activity, Intent intent, String... permissions) {
        var permissionsToRequest = TermuxPermissionUtils.checkNonGrantedPermissions(activity, permissions);

        if (permissionsToRequest.isEmpty()) {
            return true;
        } else {
            ResultReturner.returnData(intent, new ResultReturner.ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    String errorMessage = "Please grant the following permission"
                        + (permissionsToRequest.size() > 1 ? "s" : "")
                        + " (at Settings > Apps > Termux > Permissions) to use this command: "
                        + TextUtils.join(", ", permissionsToRequest);
                    out.beginObject().name("error").value(errorMessage).endObject();
                }
            });

            activity.requestPermissions(permissionsToRequest.toArray(new String[0]), 0);
            return false;
        }
    }

}
