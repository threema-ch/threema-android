package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface SensorListener {
    String keyIsNear = "IS_NEAR";

    @AnyThread
    void onSensorChanged(String key, boolean value);
}
