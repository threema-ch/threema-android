package ch.threema.app.services;

import ch.threema.app.listeners.SensorListener;
import ch.threema.base.SessionScoped;

@SessionScoped
public interface SensorService {
    void registerSensors(String tag, SensorListener sensorListener, boolean useAccelerometer);

    void registerSensors(String tag, SensorListener sensorListener);

    void unregisterSensors(String tag);

    void unregisterAllSensors();

    boolean isSensorRegistered(String tag);
}
