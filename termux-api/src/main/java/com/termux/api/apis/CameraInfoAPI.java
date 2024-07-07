package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.JsonWriter;
import android.util.Size;
import android.util.SizeF;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;

public class CameraInfoAPI {

    public static void onReceive(final Context context, Intent intent) {
        ResultReturner.returnData(context, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

                out.beginArray();
                for (String cameraId : manager.getCameraIdList()) {
                    out.beginObject();
                    out.name("id").value(cameraId);

                    CameraCharacteristics camera = manager.getCameraCharacteristics(cameraId);

                    out.name("facing");
                    int lensFacing = camera.get(CameraCharacteristics.LENS_FACING);
                    switch (lensFacing) {
                        case CameraMetadata.LENS_FACING_FRONT:
                            out.value("front");
                            break;
                        case CameraMetadata.LENS_FACING_BACK:
                            out.value("back");
                            break;
                        default:
                            out.value(lensFacing);
                    }

                    StreamConfigurationMap map = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    out.name("jpeg_output_sizes").beginArray();
                    for (Size size : map.getOutputSizes(ImageFormat.JPEG)) {
                        out.beginObject().name("width").value(size.getWidth()).name("height").value(size.getHeight()).endObject();
                    }
                    out.endArray();

                    out.name("focal_lengths").beginArray();
                    for (float f : camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))
                        out.value(f);
                    out.endArray();

                    out.name("auto_exposure_modes").beginArray();
                    int[] flashModeValues = camera.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
                    for (int flashMode : flashModeValues) {
                        switch (flashMode) {
                            case CameraMetadata.CONTROL_AE_MODE_OFF:
                                out.value("CONTROL_AE_MODE_OFF");
                                break;
                            case CameraMetadata.CONTROL_AE_MODE_ON:
                                out.value("CONTROL_AE_MODE_ON");
                                break;
                            case CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                                out.value("CONTROL_AE_MODE_ON_ALWAYS_FLASH");
                                break;
                            case CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH:
                                out.value("CONTROL_AE_MODE_ON_AUTO_FLASH");
                                break;
                            case CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
                                out.value("CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE");
                                break;
                            case CameraMetadata.CONTROL_AE_MODE_ON_EXTERNAL_FLASH:
                                out.value("CONTROL_AE_MODE_ON_EXTERNAL_FLASH");
                                break;
                            default:
                                out.value(flashMode);
                        }
                    }
                    out.endArray();

                    SizeF physicalSize = camera.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    out.name("physical_size").beginObject().name("width").value(physicalSize.getWidth()).name("height")
                            .value(physicalSize.getHeight()).endObject();

                    out.name("capabilities").beginArray();
                    for (int capability : camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)) {
                        switch (capability) {
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
                                out.value("backward_compatible");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE:
                                out.value("burst_capture");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO:
                                out.value("constrained_high_speed_video");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT:
                                out.value("depth_output");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA:
                                out.value("logical_multi_camera");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
                                out.value("manual_post_processing");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
                                out.value("manual_sensor");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME:
                                out.value("monochrome");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING:
                                out.value("motion_tracking");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING:
                                out.value("private_reprocessing");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW:
                                out.value("raw");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS:
                                out.value("read_sensor_settings");
                                break;
                            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING:
                                out.value("yuv_reprocessing");
                                break;
                            default:
                                out.value(capability);
                        }
                    }
                    out.endArray();

                    out.endObject();
                }
                out.endArray();
            }
        });
    }
}
