package ch.threema.app.webclient.listeners;

import androidx.annotation.AnyThread;

@AnyThread
public interface BatteryStatusListener {
    void onChange(int percent, boolean isCharging);
}
