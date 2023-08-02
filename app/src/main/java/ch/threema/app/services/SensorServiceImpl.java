/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.services;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import ch.threema.app.BuildConfig;
import ch.threema.app.listeners.SensorListener;
import ch.threema.base.utils.LoggingUtil;

public class SensorServiceImpl implements SensorService, SensorEventListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SensorServiceImpl");
	private static final String WAKELOCK_TAG = BuildConfig.APPLICATION_ID + ":SensorService";

	private PowerManager.WakeLock proximityWakelock;
	private final SensorManager sensorManager;
	private Sensor proximitySensor, accelerometerSensor;
	private static boolean isFlatOnTable = true;
	private final Map<String, Object> instanceMap = new HashMap<>();

	public SensorServiceImpl(Context context) {
		PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
		this.sensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);

		try {
			this.proximitySensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
			this.accelerometerSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			if (hasSensors()) {
				if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
					this.proximityWakelock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKELOCK_TAG);
				} else {
					logger.debug("Proximity wakelock not supported");
				}
			}
		} catch (Exception e) {
			logger.debug("unable to register sensors.");
		}
	}

	private boolean hasSensors() {
		return this.proximitySensor != null && this.accelerometerSensor != null;
	}

	private void acquireWakelock() {
		if (this.proximityWakelock != null && !this.proximityWakelock.isHeld()) {
			this.proximityWakelock.acquire(DateUtils.HOUR_IN_MILLIS * 3); // assume calls are no longer than 3 hours
		} else if (this.proximityWakelock == null) {
			logger.warn("Failed to acquire proximity wakelock because it is null");
		}
	}

	private void releaseWakelock() {
		if (this.proximityWakelock != null && this.proximityWakelock.isHeld()) {
			this.proximityWakelock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
		} else if (this.proximityWakelock == null) {
			logger.warn("Failed to release proximity wakelock because it is null");
		}
	}

	@Override
	public void registerSensors(String tag, SensorListener sensorListener) {
		registerSensors(tag, sensorListener, true);
	}

	@Override
	public void registerSensors(String tag, SensorListener sensorListener, boolean useAccelerometer) {
		if (hasSensors()) {
			synchronized (instanceMap) {
				if (!instanceMap.containsKey(tag)) {
					instanceMap.put(tag, sensorListener);
					sensorManager.registerListener(this, this.proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
					if (useAccelerometer) {
						sensorManager.registerListener(this, this.accelerometerSensor, 30000);
					} else {
						isFlatOnTable = false;
					}
				} else {
					logger.debug("Sensor {} already registered.", tag);
				}
			}
		}
	}

	@Override
	public void unregisterSensors(String tag) {
		synchronized (instanceMap) {
			if (instanceMap.size() > 0) {
				instanceMap.remove(tag);
				if (instanceMap.size() < 1) {
					releaseWakelock();
					if (hasSensors()) {
						sensorManager.unregisterListener(this);
					}
				}
			}
		}
	}

	@Override
	public void unregisterAllSensors() {
		synchronized (instanceMap) {
			instanceMap.clear();
			releaseWakelock();
			if (hasSensors()) {
				sensorManager.unregisterListener(this);
			}
		}
	}

	@Override
	public boolean isSensorRegistered(String tag) {
		return instanceMap.size() > 0 && instanceMap.containsKey(tag);
	}

	private boolean isNear(float value) {
		return value < 5.0f && value != this.proximitySensor.getMaximumRange();
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor == this.proximitySensor) {
			boolean onEar = isNear(event.values[0]) && !isFlatOnTable;

			logger.info("Proximity Sensor changed. onEar: {}", onEar);

			if (onEar) {
				acquireWakelock();
			} else {
				releaseWakelock();
			}

			for (Map.Entry<String, Object> instance : instanceMap.entrySet()) {
				((SensorListener) (instance.getValue())).onSensorChanged(SensorListener.keyIsNear, onEar);
			}

		} else if (event.sensor == this.accelerometerSensor) {
			float[] values = event.values;

			float x = values[0];
			float y = values[1];
			float z = values[2];
			float norm_Of_g = (float) Math.sqrt(x * x + y * y + z * z);

			z = (z / norm_Of_g);
			int inclination = (int) Math.round(Math.toDegrees(Math.acos(z)));

			isFlatOnTable = (inclination < 45 || inclination > 135);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
