/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.voip.util;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

/**
 * Enumerate and initialize device cameras.
 */
public class VideoCapturerUtil {
	private static final Logger logger = LoggerFactory.getLogger(VideoCapturerUtil.class);

	/**
	 * Return a flag indicating whether the Camera2 API should be used or not.
	 */
	private static boolean useCamera2(Context context) {
		return Camera2Enumerator.isSupported(context);
	}

	/**
	 * Create a video capturer.
	 *
	 * Return null if no cameras were found or if initialization failed.
	 */
	@Nullable
	public static CameraVideoCapturer createVideoCapturer(
		@NonNull Context context,
		@Nullable CameraVideoCapturer.CameraEventsHandler eventsHandler
	) {
		final CameraVideoCapturer capturer;
		if (VideoCapturerUtil.useCamera2(context)) {
			logger.debug("Creating capturer using camera2 API");
			capturer = VideoCapturerUtil.createCameraCapturer(new Camera2Enumerator(context), eventsHandler);
		} else {
			logger.debug("Creating capturer using camera1 API");
			capturer = VideoCapturerUtil.createCameraCapturer(new Camera1Enumerator(), eventsHandler);
		}
		if (capturer == null) {
			logger.error("Failed to initialize camera");
		}
		return capturer;
	}

	/**
	 * Enumerate cameras, return a VideoCapturer instance.
	 *
	 * Return null if no cameras were found or if initialization failed.
	 */
	@Nullable
	private static CameraVideoCapturer createCameraCapturer(
		@NonNull CameraEnumerator enumerator,
		@Nullable CameraVideoCapturer.CameraEventsHandler eventsHandler
	) {
		final String[] deviceNames = enumerator.getDeviceNames();

		// Try to find front camera
		logger.debug("Looking for front cameras");
		for (String deviceName : deviceNames) {
			if (enumerator.isFrontFacing(deviceName)) {
				logger.debug("Found front camera, creating camera capturer");
				final CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, eventsHandler);
				if (videoCapturer != null) {
					return videoCapturer;
				}
			}
		}

		// No front camera found, search for other cams
		logger.debug("No front camera found, looking for other cameras");
		for (String deviceName : deviceNames) {
			if (!enumerator.isFrontFacing(deviceName)) {
				logger.debug("Non-front facing camera found, creating camera capturer");
				final CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
				if (videoCapturer != null) {
					return videoCapturer;
				}
			}
		}
		return null;
	}

	/**
	 *
	 * Returns the primary camera names as Pair of {frontcamera, backcamera}.
	 * Currently, the first available front/backcamera is used as primary.
	 *
	 * @param context
	 * @return Pair of nullable camera name strings.
	 */
	public static Pair<String, String> getPrimaryCameraNames(Context context) {
		CameraEnumerator enumerator;
		String frontCamera = null, backCamera = null;

		if (VideoCapturerUtil.useCamera2(context)) {
			enumerator = new Camera2Enumerator(context);
		} else {
			enumerator = new Camera1Enumerator();
		}

		final String[] deviceNames = enumerator.getDeviceNames();
		for (String deviceName : deviceNames) {
			if (enumerator.isFrontFacing(deviceName)) {
				if (frontCamera == null) {
					frontCamera = deviceName;
				}
			} else if (enumerator.isBackFacing(deviceName)) {
				if (backCamera == null) {
					backCamera = deviceName;
				}
			}

			if (frontCamera != null && backCamera != null) {
				break;
			}
		}
		return new Pair<>(frontCamera, backCamera);
	}
}
