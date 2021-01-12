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

/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.threema.app.qrscanner.camera;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.qrscanner.camera.open.CameraFacing;
import ch.threema.app.qrscanner.camera.open.OpenCamera;

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 */

/**
 * @date 2016-11-23 15:38
 * @auther GuoJinyu
 * @description modified
 */
public final class CameraConfigurationManager {

	private static final Logger logger = LoggerFactory.getLogger(CameraConfigurationManager.class);

	private final Context context;
	private int cwRotationFromDisplayToCamera;
	private Point screenResolution;
	private Point cameraResolution;
	private Point bestPreviewSize;
	private boolean needExposure;

	public CameraConfigurationManager(Context context, boolean needExposure) {
		this.context = context;
		this.needExposure = needExposure;
	}

	/**
	 * Reads, one time, values from the camera that are needed by the app.
	 */
	void initFromCameraParameters(OpenCamera camera) {
		Camera.Parameters parameters = camera.getCamera().getParameters();
		WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();

		int displayRotation = display.getRotation();
		int cwRotationFromNaturalToDisplay;
		switch (displayRotation) {
			case Surface.ROTATION_0:
				cwRotationFromNaturalToDisplay = 0;
				break;
			case Surface.ROTATION_90:
				cwRotationFromNaturalToDisplay = 90;
				break;
			case Surface.ROTATION_180:
				cwRotationFromNaturalToDisplay = 180;
				break;
			case Surface.ROTATION_270:
				cwRotationFromNaturalToDisplay = 270;
				break;
			default:
				// Have seen this return incorrect values like -90
				if (displayRotation % 90 == 0) {
					cwRotationFromNaturalToDisplay = (360 + displayRotation) % 360;
				} else {
					throw new IllegalArgumentException("Bad rotation: " + displayRotation);
				}
		}
		//Log.i(TAG, "Display at: " + cwRotationFromNaturalToDisplay);

		int cwRotationFromNaturalToCamera = camera.getOrientation();
		//Log.i(TAG, "Camera at: " + cwRotationFromNaturalToCamera);

		// Still not 100% sure about this. But acts like we need to flip this:
		if (camera.getFacing() == CameraFacing.FRONT) {
			cwRotationFromNaturalToCamera = (360 - cwRotationFromNaturalToCamera) % 360;
			//Log.i(TAG, "Front camera overriden to: " + cwRotationFromNaturalToCamera);
		}

		cwRotationFromDisplayToCamera = (360 + cwRotationFromNaturalToCamera - cwRotationFromNaturalToDisplay) % 360;
		//Log.i(TAG, "Final display orientation: " + cwRotationFromDisplayToCamera);
		int cwNeededRotation;
		if (camera.getFacing() == CameraFacing.FRONT) {
			//Log.i(TAG, "Compensating rotation for front camera");
			cwNeededRotation = (360 - cwRotationFromDisplayToCamera) % 360;
		} else {
			cwNeededRotation = cwRotationFromDisplayToCamera;
		}
		//Log.i(TAG, "Clockwise rotation from display to camera: " + cwNeededRotation);

		Point theScreenResolution = new Point();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			DisplayMetrics displayMetrics = new DisplayMetrics();
			display.getRealMetrics(displayMetrics);
			theScreenResolution.x = displayMetrics.widthPixels;
			theScreenResolution.y = displayMetrics.heightPixels;
		} else {
			display.getSize(theScreenResolution);
		}
		screenResolution = theScreenResolution;
		logger.info("Screen resolution in current orientation: " + screenResolution);
		cameraResolution = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolution);
		logger.info("Camera resolution: " + cameraResolution);
		bestPreviewSize = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolution);
		logger.info("Best available preview size: " + bestPreviewSize);
	}

	void setDesiredCameraParameters(OpenCamera camera, boolean safeMode) {
		Camera theCamera = camera.getCamera();
		Camera.Parameters parameters = theCamera.getParameters();
		if (parameters == null) {
			//Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.");
			return;
		}
		//Log.i(TAG, "Initial camera parameters: " + parameters.flatten());

		if (safeMode) {
			//Log.w(TAG, "In camera config safe mode -- most settings will not be honored");
		}
		initializeTorch(parameters, safeMode);
		CameraConfigurationUtils.setFocus(
				parameters,
				true,
				true,
				safeMode);
		if (!safeMode) {
			CameraConfigurationUtils.setBarcodeSceneMode(parameters);
			CameraConfigurationUtils.setVideoStabilization(parameters);
			CameraConfigurationUtils.setFocusArea(parameters);
			CameraConfigurationUtils.setMetering(parameters);
		}
		parameters.setPreviewSize(bestPreviewSize.x, bestPreviewSize.y);
		//设置放大倍数
/*		if (parameters.isZoomSupported()) {
			parameters.setZoom(parameters.getMaxZoom() / 10);
		}
*/		theCamera.setParameters(parameters);
		theCamera.setDisplayOrientation(cwRotationFromDisplayToCamera);
		Camera.Parameters afterParameters = theCamera.getParameters();
		Camera.Size afterSize = afterParameters.getPreviewSize();
		if (afterSize != null && (bestPreviewSize.x != afterSize.width || bestPreviewSize.y != afterSize.height)) {
			logger.info("Camera said it supported preview size " + bestPreviewSize.x + 'x' + bestPreviewSize.y +
			        ", but after setting it, preview size is " + afterSize.width + 'x' + afterSize.height);
			bestPreviewSize.x = afterSize.width;
			bestPreviewSize.y = afterSize.height;
		}
	}

	public Point getCameraResolution() {
		return cameraResolution;
	}

	public Point getScreenResolution() {
		return screenResolution;
	}

	boolean getTorchState(Camera camera) {
		if (camera != null) {
			Camera.Parameters parameters = camera.getParameters();
			if (parameters != null) {
				String flashMode = parameters.getFlashMode();
				return flashMode != null &&
						(Camera.Parameters.FLASH_MODE_ON.equals(flashMode) ||
								Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
			}
		}
		return false;
	}

	void setTorch(Camera camera, boolean newSetting) {
		Camera.Parameters parameters = camera.getParameters();
		doSetTorch(parameters, newSetting, false);
		camera.setParameters(parameters);
	}

	private void initializeTorch(Camera.Parameters parameters, boolean safeMode) {
		doSetTorch(parameters, false, safeMode);
	}

	private void doSetTorch(Camera.Parameters parameters, boolean newSetting, boolean safeMode) {
		CameraConfigurationUtils.setTorch(parameters, newSetting);
		if (!safeMode && needExposure) {
			CameraConfigurationUtils.setBestExposure(parameters, newSetting);
		}
	}

}
