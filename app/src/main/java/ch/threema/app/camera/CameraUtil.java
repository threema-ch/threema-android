package ch.threema.app.camera;

import android.os.Build;

import java.util.HashSet;

import androidx.camera.core.ImageCapture;

public class CameraUtil {
    // list of MAX_QUALITY compatible cameras
    private static final HashSet<String> MAX_QUALITY_CAMERAS = new HashSet<String>() {{
        add("Pixel 2");
        add("Pixel 2 XL");
        add("Pixel 3");
        add("Pixel 3 XL");
        add("Pixel 3a");
        add("Pixel 3a XL");
    }};

    public static @ImageCapture.CaptureMode int getCaptureMode() {
        return MAX_QUALITY_CAMERAS.contains(Build.MODEL) ? ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY : ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;
    }
}
