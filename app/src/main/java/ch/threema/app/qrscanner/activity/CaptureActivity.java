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
 * Copyright (C) 2008 ZXing authors
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
package ch.threema.app.qrscanner.activity;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.R;
import ch.threema.app.qrscanner.assit.AmbientLightManager;
import ch.threema.app.qrscanner.assit.BeepManager;
import ch.threema.app.qrscanner.camera.CameraManager;
import ch.threema.app.qrscanner.view.ViewfinderView;
import ch.threema.app.utils.ConfigUtils;

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as the image processing
 * is happening, and then return the results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */

/**
 * @date 2016-11-18 9:07
 * @auther GuoJinyu
 * @description modified
 */

public final class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback {
	private static final Logger logger = LoggerFactory.getLogger(CaptureActivity.class);

	public static final String INTENT_DATA_QRCODE = "qrcodestring";
	public static final String INTENT_DATA_QRCODE_TYPE_OK = "qrcodetypeok";

	public static final int REQ_CODE = 0xF0F0;
	public static final String KEY_NEED_BEEP = "NEED_BEEP";
	public static final boolean VALUE_BEEP = true; //default
	public static final boolean VALUE_NO_BEEP = false;
	public static final String KEY_NEED_EXPOSURE = "NEED_EXPOSURE";
	public static final boolean VALUE_EXPOSURE = true;
	public static final boolean VALUE_NO_EXPOSURE = false; //default
	public static final String KEY_FLASHLIGHT_MODE = "FLASHLIGHT_MODE";
	public static final byte VALUE_FLASHLIGHT_AUTO = 2;
	public static final byte VALUE_FLASHLIGHT_ON = 1;
	public static final byte VALUE_FLASHLIGHT_OFF = 0;  //default
	public static final String KEY_ORIENTATION_MODE = "ORIENTATION_MODE";
	public static final byte VALUE_ORIENTATION_AUTO = 2;
	public static final byte VALUE_ORIENTATION_LANDSCAPE = 1;
	public static final byte VALUE_ORIENTATION_PORTRAIT = 0; //default
	public static final String KEY_SCAN_AREA_FULL_SCREEN = "SCAN_AREA_FULL_SCREEN";
	public static final boolean VALUE_SCAN_AREA_FULL_SCREEN = true;
	public static final boolean VALUE_SCAN_AREA_VIEW_FINDER = false;
	public static final String EXTRA_SETTING_BUNDLE = "SETTING_BUNDLE";
	public static final String EXTRA_SCAN_RESULT = "SCAN_RESULT";
	public static final String KEY_NEED_SCAN_HINT_TEXT = "KEY_NEED_SCAN_HINT_TEXT";
	public static final boolean VALUE_SCAN_HINT_TEXT = true;
	public static final boolean VALUE_NO_SCAN_HINT_TEXT = false;
	private static final String TAG = CaptureActivity.class.getSimpleName();
	byte flashlightMode;
	byte orientationMode;
	boolean needBeep;
	boolean needExposure;
	boolean needFullScreen;
	String scanHintText;
	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	private SurfaceView surfaceView;
	private boolean hasSurface;
	private BeepManager beepManager;
	private AmbientLightManager ambientLightManager;

	ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		windowSetting();
		setContentView(R.layout.activity_capture);

		Bundle bundle = new Bundle();
		bundle.putString(KEY_NEED_SCAN_HINT_TEXT, getIntent().getStringExtra("PROMPT_MESSAGE"));

		bundleSetting(bundle);
	}

	private void windowSetting() {
		Window window = getWindow();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
					| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			window.setStatusBarColor(Color.TRANSPARENT);
			window.setNavigationBarColor(Color.TRANSPARENT);
		} else {
			window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private void bundleSetting(Bundle bundle) {
		flashlightMode = bundle.getByte(KEY_FLASHLIGHT_MODE, VALUE_FLASHLIGHT_OFF);
		orientationMode = bundle.getByte(KEY_ORIENTATION_MODE, VALUE_ORIENTATION_PORTRAIT);
		needBeep = bundle.getBoolean(KEY_NEED_BEEP, VALUE_BEEP);
		needExposure = bundle.getBoolean(KEY_NEED_EXPOSURE, VALUE_NO_EXPOSURE);
		needFullScreen = bundle.getBoolean(KEY_SCAN_AREA_FULL_SCREEN, VALUE_SCAN_AREA_VIEW_FINDER);
		scanHintText = bundle.getString(KEY_NEED_SCAN_HINT_TEXT, getString(R.string.msg_default_status));
		switch (orientationMode) {
			case VALUE_ORIENTATION_LANDSCAPE:
				ConfigUtils.setRequestedOrientation(this, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			case VALUE_ORIENTATION_PORTRAIT:
				ConfigUtils.setRequestedOrientation(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			default:
				ConfigUtils.setRequestedOrientation(this, ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				break;
		}
		switch (flashlightMode) {
			case VALUE_FLASHLIGHT_AUTO:
				ambientLightManager = new AmbientLightManager(this);
				break;
		}
		beepManager = new BeepManager(this, needBeep);
	}

	@Override
	protected void onResume() {
		super.onResume();
		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		cameraManager = new CameraManager(getApplication(), displayMetrics, needExposure, needFullScreen);
		viewfinderView = findViewById(R.id.viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);
		viewfinderView.setHintText(scanHintText);
		viewfinderView.setScanAreaFullScreen(needFullScreen);
		handler = null;
		beepManager.updatePrefs();
		if (ambientLightManager != null) {
			ambientLightManager.start(cameraManager);
		}
		surfaceView = findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder, surfaceView);
		} else {
			// Install the callback and wait for surfaceCreated() to init the camera.
			surfaceHolder.addCallback(this);
		}

	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		if (ambientLightManager != null) {
			ambientLightManager.stop();
		}
		beepManager.close();
		cameraManager.closeDriver();
		if (!hasSurface) {
			surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}


	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			//Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder, surfaceView);
		}
		if (flashlightMode == VALUE_FLASHLIGHT_ON) {
			if (cameraManager != null) {
				cameraManager.setTorch(true);
			}
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show the results.
	 *
	 * @param rawResult The contents of the barcode.
	 */
	public void handleDecode(Result rawResult) {
		logger.info("Barcode / QR Code detected");
		beepManager.playBeepSoundAndVibrate();
		try {
			Thread.sleep(300);
		} catch (InterruptedException e) {
			logger.error("Exception", e);
			Thread.currentThread().interrupt();
		}
		returnResult(rawResult);
	}

	private void initCamera(SurfaceHolder surfaceHolder, SurfaceView surfaceView) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			//Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder, surfaceView);
			// Creating the handler starts the preview, which can also throw a RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, cameraManager);
			}
		} catch (Exception e) {
			Toast.makeText(this, R.string.msg_camera_framework_bug, Toast.LENGTH_LONG).show();

			returnResult(null);
		}
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	private void returnResult(Result rawResult) {
		Intent intent = new Intent();

		if (rawResult != null && rawResult.getText() != null) {
			intent.putExtra(INTENT_DATA_QRCODE_TYPE_OK, (rawResult.getBarcodeFormat() == BarcodeFormat.QR_CODE));
			intent.putExtra(INTENT_DATA_QRCODE, rawResult.getText());
			setResult(RESULT_OK, intent);
		} else {
			setResult(RESULT_CANCELED);
		}
		finish();
	}

	private void restartActivity() {
		onPause();
		// some device return wrong rotation state when rotate quickly.
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			logger.error("Exception", e);
			Thread.currentThread().interrupt();
		}
		onResume();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		restartActivity();
	}
}
