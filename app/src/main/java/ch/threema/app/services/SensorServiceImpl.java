/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.PowerManager;
import android.text.format.DateUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import ch.threema.app.BuildConfig;
import ch.threema.app.listeners.SensorListener;

public class SensorServiceImpl implements SensorService, SensorEventListener {
	private static final Logger logger = LoggerFactory.getLogger(SensorServiceImpl.class);
	private static final String WAKELOCK_TAG = BuildConfig.APPLICATION_ID + ":SensorService";

	// On Android API <21, the PROXIMITY_SCREEN_OFF_WAKE_LOCK and
	// RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY are not part of the public API.
	private static int PROXIMITY_SCREEN_OFF_WAKE_LOCK_PRE_21 = 32;
	private static int RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY_PRE_21 = 1;

	private PowerManager.WakeLock proximityWakelock;
	private SensorManager sensorManager;
	private Sensor proximitySensor, accelerometerSensor;
	private static boolean isFlatOnTable = true;
	private Map<String, Object> instanceMap = new HashMap<>();

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public SensorServiceImpl(Context context) {
		PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
		this.sensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);

		try {
			this.proximitySensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
			this.accelerometerSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			if (hasSensors()) {
				// New API (21+)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					if (powerManager.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
						this.proximityWakelock = powerManager.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKELOCK_TAG);
					} else {
						logger.debug("Proximity wakelock not supported");
					}

				// Older Android versions, rely on undocumented code
				} else {
					try {
						this.proximityWakelock = powerManager.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK_PRE_21, WAKELOCK_TAG);
					} catch (Exception e) {
						logger.error("Proximity wakelock not supported", e);
					}
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
		}
	}

	private void releaseWakelock() {
		if (this.proximityWakelock != null && this.proximityWakelock.isHeld()) {
			// Properly releasing the proximity wakelock is a bit tricky. Newer API versions
			// have a version of .release() that accepts flags. We want the
			// RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY flag. On older versions, that method may exist
			// but is not part of the public API. Therefore we try to invoke the method
			// through reflection.
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				this.proximityWakelock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
			} else {
				boolean released = false;
				Method releaseWithFlag = null;
				try {
					releaseWithFlag = PowerManager.WakeLock.class.getDeclaredMethod("release", Integer.TYPE);
				} catch (NoSuchMethodException e) {
					logger.error("Device does not support parametrizable wakelock release", e);
				}
				if (releaseWithFlag != null) {
					//noinspection TryWithIdenticalCatches
					try {
						// Release with flag
						releaseWithFlag.invoke(this.proximityWakelock, RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY_PRE_21);
						released = true;
					} catch (IllegalAccessException e) {
						logger.error("Could not release wakelock with flags", e);
					} catch (InvocationTargetException e) {
						logger.error("Could not release wakelock with flags", e);
					}
				}
				if (!released) {
					// Release without a flag
					this.proximityWakelock.release();
				}
				logger.info("Released proximity wakelock");
			}
		}
	}

	@Override
	public void registerSensors(String tag, SensorListener sensorListener) {
		registerSensors(tag, sensorListener, true);
	}

	@Override
	public void registerSensors(String tag, SensorListener sensorListener, boolean useAccelerometer) {
		if (hasSensors()) {
			instanceMap.put(tag, sensorListener);
			sensorManager.registerListener(this, this.proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
			if (useAccelerometer) {
				sensorManager.registerListener(this, this.accelerometerSensor, 30000);
			} else {
				isFlatOnTable = false;
			}
		}
	}

	@Override
	public void unregisterSensors(String tag) {
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

	@Override
	public void unregisterAllSensors() {
		instanceMap.clear();
		releaseWakelock();
		if (hasSensors()) {
			sensorManager.unregisterListener(this);
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

			logger.debug("Proximity Sensor changed. onEar: " + onEar);

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

			isFlatOnTable = (inclination < 20 || inclination > 160);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
