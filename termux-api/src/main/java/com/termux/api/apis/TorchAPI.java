package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.widget.Toast;

import com.termux.api.util.ResultReturner;

public class TorchAPI {

    private static final String LOG_TAG = "TorchAPI";

    public static void onReceive(final Context context, final Intent intent) {
        boolean enabled = intent.getBooleanExtra("enabled", false);
        toggleTorch(context, enabled);
        ResultReturner.noteDone(context, intent);
    }

    private static void toggleTorch(Context context, boolean enabled) {
        try {
            final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String torchCameraId = getTorchCameraId(cameraManager);

            if (torchCameraId != null) {
                cameraManager.setTorchMode(torchCameraId, enabled);
            } else {
                Toast.makeText(context, "Torch unavailable on your device", Toast.LENGTH_LONG).show();
            }
        } catch (CameraAccessException e) {
            Log.e(LOG_TAG, "Error toggling torch", e);
        }
    }

    private static String getTorchCameraId(CameraManager cameraManager) throws CameraAccessException {
        String[] cameraIdList =  cameraManager.getCameraIdList();
        String result = null;

        for (String id : cameraIdList) {
            if (cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == Boolean.TRUE) {
                result = id;
                break;
            }
        }
        return result;
    }
}
