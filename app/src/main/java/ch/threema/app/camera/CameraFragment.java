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

package ch.threema.app.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.video.OnVideoSavedCallback;
import androidx.camera.view.video.OutputFileResults;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.LessObnoxiousMediaActionSound;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.RuntimeUtil;

import static android.view.Surface.ROTATION_180;
import static ch.threema.app.camera.CameraActivity.KEY_EVENT_ACTION;
import static ch.threema.app.camera.CameraActivity.KEY_EVENT_EXTRA;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraFragment extends Fragment {
	private static final Logger logger = LoggerFactory.getLogger(CameraFragment.class);
	private static final int PERMISSION_REQUEST_CODE_AUDIO = 869;

	private ConstraintLayout container;
	private View controlsContainer;
	private CameraView cameraView;
	private LocalBroadcastManager broadcastManager;
	private CameraCallback cameraCallback;
	private CameraConfiguration cameraConfiguration;
	private ProcessCameraProvider cameraProvider;

	private LessObnoxiousMediaActionSound mediaActionSound;

	private int displayId = -1;
	private DisplayManager displayManager;
	private int displayRotation;

	private WindowInsetsCompat windowInsets;

	private ProgressBar progressBar;
	private TimerView timerView;

	private PreferenceService preferenceService;

	/** Blocking camera operations are performed using this executor */
	private ExecutorService cameraExecutor;

	// Volume down button receiver
	private final BroadcastReceiver volumeDownReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int keyCode = intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN);
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				ShutterButtonView shutter = container.findViewById(R.id.camera_capture_button);
				if (shutter != null) {
					shutter.simulateClick();
				}
			}
		}
	};

	/**
	 * We need a display listener for orientation changes that do not trigger a configuration
	 * change, for example if we choose to override config change in manifest or for 180-degree
	 * orientation changes.
	 */
	private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
		@Override
		public void onDisplayAdded(int displayId) {
		}

		@Override
		public void onDisplayRemoved(int displayId) {
		}

		@Override
		public void onDisplayChanged(int displayId) {
			if (getActivity() != null && getActivity().getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
				// ignore rotation event when screen is locked anyway
				return;
			}

			if (displayId == CameraFragment.this.displayId) {
				if (getView() != null && getView().getDisplay() != null) {
					int rotation = getView().getDisplay().getRotation();
					logger.debug("Rotation changed from {} to {}", displayRotation, rotation);
					if (displayRotation != rotation) {
						displayRotation = rotation;
						updateCameraUi();
					}
				}
			}
		}
	};

	/**
	 * Define callback that will be triggered after a photo has been taken
	 */
	private final ImageCapture.OnImageCapturedCallback imageCapturedCallback = new ImageCapture.OnImageCapturedCallback() {
		@SuppressLint("StaticFieldLeak")
		@Override
		public void onCaptureSuccess(@NonNull ImageProxy image) {
			RuntimeUtil.runOnUiThread(() -> {
				if (cameraView != null) {
					cameraView.setVisibility(View.GONE);
				}
				if (container != null) {
					ConstraintLayout constraintLayout = container.findViewById(R.id.camera_ui_container);
					progressBar.setVisibility(View.VISIBLE);
					constraintLayout.setVisibility(View.GONE);
				}
			});

			Lifecycle lifecycle = getLifecycle();

			if (!lifecycle.getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
				return;
			}

			byte[] result;
			try {
				final boolean frontFacing = cameraView.getCameraLensFacing() == CameraSelector.LENS_FACING_FRONT;
				final int rotation = image.getImageInfo().getRotationDegrees();
				result = CameraUtil.getJpegBytes(image, rotation, frontFacing);
			} catch (Exception e) {
				logger.error("Exception", e);
				return;
			} finally {
				image.close();
			}

			if (lifecycle.getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
				if (result != null) {
					cameraCallback.onImageReady(result);
				} else {
					cameraCallback.onError("Exception");
				}
			}
		}

		@Override
		public void onError(@NonNull ImageCaptureException exception) {
			super.onError(exception);

			if (exception.getMessage() != null) {
				logger.debug("Capture error " + exception.getMessage());
				cameraCallback.onError(exception.getMessage());
			} else {
				cameraCallback.onError("Capture error");
			}
		}
	};

	@SuppressLint("UnsafeExperimentalUsageError")
	private final OnVideoSavedCallback onVideoSavedCallback = new OnVideoSavedCallback() {
		@SuppressLint("StaticFieldLeak")
		@Override
		public void onVideoSaved(@NonNull OutputFileResults outputFileResults) {
			if (cameraView != null) {
				cameraView.setVisibility(View.GONE);
			}

			Lifecycle lifecycle = getLifecycle();

			new AsyncTask<Void, Boolean, byte[]>() {
				@Override
				protected void onPreExecute() {
					if (!lifecycle.getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
						cancel(true);
					} else {
						progressBar.setVisibility(View.VISIBLE);
						if (container != null) {
							ConstraintLayout constraintLayout = container.findViewById(R.id.camera_ui_container);
							if (constraintLayout != null) {
								constraintLayout.setVisibility(View.GONE);
							}
						}
					}
				}

				@Override
				protected byte[] doInBackground(Void... voids) {
					// TODO
					return null;
				}

				@Override
				protected void onPostExecute(byte[] result) {
					if (lifecycle.getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
						cameraCallback.onVideoReady();
					} else {
						cameraCallback.onError("Lifecycle");
					}
				}
			}.execute();
		}

		@Override
		public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
			logger.debug("Video capture error " + message);

			cameraCallback.onError(message);
		}
	};

	@Override
	public void onAttach(@NonNull Context context) {
		logger.debug("*** onAttach");

		super.onAttach(context);

		if (!(getActivity() instanceof CameraCallback)) {
			throw new IllegalStateException("Activity does not implement CameraCallback.");
		}
		this.cameraCallback = (CameraCallback) getActivity();
		this.cameraConfiguration = (CameraConfiguration) getActivity();
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		logger.debug("*** onCreate");

		super.onCreate(savedInstanceState);

		preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();

		mediaActionSound = new LessObnoxiousMediaActionSound();
		mediaActionSound.load(LessObnoxiousMediaActionSound.SHUTTER_CLICK);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		logger.debug("*** onCreateView");

		// Inflate the layout for this fragment
		return inflater.inflate(R.layout.camerax_fragment_camera, container, false);
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		container = (ConstraintLayout) view;
		ViewCompat.setOnApplyWindowInsetsListener(container, new OnApplyWindowInsetsListener() {
			@Override
			public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
				windowInsets = insets;
				logger.debug("*** updateCameraUI: top = " + insets.getSystemWindowInsetTop() +
						" bottom = " + insets.getSystemWindowInsetBottom());
				return insets;
			}
		});

		// Initialize our background executor
		cameraExecutor = Executors.newSingleThreadExecutor();

		cameraView = container.findViewById(R.id.camera_view);
		progressBar = container.findViewById(R.id.progress);
		timerView = container.findViewById(R.id.timer_view);

		broadcastManager = LocalBroadcastManager.getInstance(view.getContext());

		try {
			cameraView.bindToLifecycle(getViewLifecycleOwner());
		} catch (IllegalStateException e) {
			if (getActivity() != null) {
				getActivity().finish();
			}
			return;
		}
		cameraView.setCameraLensFacing(CameraSelector.LENS_FACING_BACK);

		// Set up the intent filter that will receive events from our main activity
		IntentFilter filter = new IntentFilter();
		filter.addAction(KEY_EVENT_ACTION);
		broadcastManager.registerReceiver(volumeDownReceiver, filter);

		// Every time the orientation of device changes, recompute layout
		displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
		displayManager = (DisplayManager) cameraView.getContext().getSystemService(Context.DISPLAY_SERVICE);
		displayManager.registerDisplayListener(displayListener, null);

		// Wait for the views to be properly laid out
		cameraView.post(new Runnable() {
			@Override
			public void run() {
				try {
					displayId = cameraView.getDisplay().getDisplayId();
					// Build UI controls and bind all camera use cases
					if (isAdded()) {
						CameraFragment.this.updateCameraUi();
						setupCamera();
					}
				} catch (Exception e) {
					//
				}
			}
		});
	}

	@Override
	public void onDestroy() {
		logger.debug("*** onDestroy");
		super.onDestroy();
	}

	@Override
	@SuppressLint("RestrictedApi")
	public void onDestroyView() {
		logger.debug("*** onDestroyView");
		super.onDestroyView();

		// Shut down our background executor
		cameraExecutor.shutdown();

		// Unregister the broadcast receivers and listeners
		broadcastManager.unregisterReceiver(volumeDownReceiver);
		displayManager.unregisterDisplayListener(displayListener);

		container = null;
		cameraView = null;
		progressBar = null;
		timerView = null;

		if (mediaActionSound != null) {
			mediaActionSound.release();
			mediaActionSound = null;
		}
	}

	@SuppressLint("MissingPermission")
	private void updateCameraUi() {
		// Remove previous UI if any
		ConstraintLayout constraintLayout = container.findViewById(R.id.camera_ui_container);
		container.removeView(constraintLayout);

		// Inflate a new view containing all UI for controlling the camera
		final int curOrientation =  getActivity().getWindowManager().getDefaultDisplay().getRotation();

		if (curOrientation == ROTATION_180 || curOrientation == Surface.ROTATION_270) {
			controlsContainer = View.inflate(requireContext(), R.layout.camerax_ui_container_reverse, container);
		} else {
			controlsContainer = View.inflate(requireContext(), R.layout.camerax_ui_container, container);
		}

		final View controls = controlsContainer.findViewById(R.id.controls);

		controls.setPadding(windowInsets.getSystemWindowInsetLeft(),
				windowInsets.getSystemWindowInsetTop(),
				windowInsets.getSystemWindowInsetRight(),
				windowInsets.getSystemWindowInsetBottom());

		// Listener for button used to capture photo
		ShutterButtonView shutterButton = controlsContainer.findViewById(R.id.camera_capture_button);
		shutterButton.setVideoEnable(cameraConfiguration.getVideoEnable());
		shutterButton.setShutterButtonListener(new ShutterButtonView.ShutterButtonListener() {
			@Override
			public void onRecordStart() {
				if (getActivity() != null) {
					if (ConfigUtils.requestAudioPermissions(getActivity(), CameraFragment.this, PERMISSION_REQUEST_CODE_AUDIO)) {
						getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
						startVideoRecording();
						View explain = container.findViewById(R.id.shutter_explain);
						if (explain != null) {
							explain.setVisibility(View.GONE);
						}
						controlsContainer.findViewById(R.id.camera_switch_button).setVisibility(View.GONE);
						controlsContainer.findViewById(R.id.flash_switch_button).setVisibility(View.GONE);
					} else {
						shutterButton.reset();
					}
				}
			}

			@SuppressLint("UnsafeExperimentalUsageError")
			@Override
			public void onRecordEnd() {
				stopVideoRecording();
			}

			@Override
			public void onZoomChanged(float zoomFactor) {
				float range = cameraView.getMaxZoomRatio() - cameraView.getMinZoomRatio();
				float level = zoomFactor * range + cameraView.getMinZoomRatio();
				cameraView.setZoomRatio(level);

				ZoomView zoomView = controlsContainer.findViewById(R.id.zoom_view);
				if (zoomView != null) {
					zoomView.setZoomFactor(zoomFactor);
				}

				logger.debug("*** new zoom level: " + level + " (factor = " + zoomFactor + ") + range = " + range);
			}

			@Override
			public void onClick() {
				if (cameraView == null) {
					return;
				}

				try {
					cameraView.takePicture(cameraExecutor, imageCapturedCallback);
				} catch (IllegalStateException e) {
					logger.error("Unable to take picture", e);
					return;
				}

				// play shutter sound
				if (mediaActionSound != null) {
					mediaActionSound.play(LessObnoxiousMediaActionSound.SHUTTER_CLICK);
				}

				// We can only change the foreground Drawable using API level 23+ API
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					// Display flash animation to indicate that photo was captured
					cameraView.postDelayed(new Runnable() {
						@Override
						public void run() {
							if (isAdded() && !isDetached() && cameraView != null) {
								cameraView.setForeground(new ColorDrawable(Color.WHITE));
								cameraView.postDelayed(new Runnable() {
									@Override
									public void run() {
										if (isAdded() && !isDetached() && cameraView != null && progressBar != null) {
											cameraView.setForeground(null);
											progressBar.setVisibility(View.VISIBLE);
											cameraView.setVisibility(View.INVISIBLE);
										}
									}
								}, 100);
							}
						}
					}, cameraView.getFlash() == ImageCapture.FLASH_MODE_ON ? 1000 : 100);
				}
			}
		});

		TextView shutterExplainText = controlsContainer.findViewById(R.id.shutter_explain);
		if (shutterExplainText != null) {
			shutterExplainText.setVisibility(cameraConfiguration.getVideoEnable() ? View.VISIBLE : View.GONE);
		}

		controlsContainer.findViewById(R.id.flash_switch_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switchFlash();
			}
		});

		setupCamera();
	}

	@SuppressLint("MissingPermission")
	private void setupCamera() {
		ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
		cameraProviderFuture.addListener(new Runnable() {
			@Override
			public void run() {
				final String errorMessage = "No camera available";

				try {
					cameraProvider = cameraProviderFuture.get();
				} catch (ExecutionException | InterruptedException e) {
					Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
					cameraCallback.onError(errorMessage);
					return;
				}

				if (!hasBackCamera() && !hasFrontCamera()) {
					if (getContext() != null) {
						Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
					}
					if (cameraCallback != null) {
						cameraCallback.onError(errorMessage);
					}
					return;
				}

				updateCameraSwitchButton();
				restoreFlashMode();
				updateFlashButton();
			}
		}, ContextCompat.getMainExecutor(getContext()));
	}

	@SuppressLint("MissingPermission")
	private void updateCameraSwitchButton() {
		if (hasFrontCamera() && hasBackCamera()) {
			// Listener for button used to switch cameras
			controlsContainer.findViewById(R.id.camera_switch_button).setOnClickListener(new View.OnClickListener() {
				@SuppressLint("RestrictedApi")
				@Override
				public void onClick(View v) {
					if (cameraView != null) {
						cameraView.toggleCamera();

						Integer lensFacing = cameraView.getCameraLensFacing();
						if (lensFacing != null) {
							preferenceService.setCameraLensFacing(lensFacing + 1);
						}

						restoreFlashMode();
						updateFlashButton();
					}
				}
			});

			int lensFacingOrdinal = preferenceService.getCameraLensFacing();
			if (lensFacingOrdinal > 0) {
				lensFacingOrdinal--;
				if (cameraView != null && cameraView.hasCameraWithLensFacing(lensFacingOrdinal)) {
					cameraView.setCameraLensFacing(lensFacingOrdinal);
				}
			}

		} else {
			controlsContainer.findViewById(R.id.camera_switch_button).setVisibility(View.GONE);
		}
	}

	private void restoreFlashMode() {
		int flashModeOrdinal = preferenceService.getCameraFlashMode();
		if (flashModeOrdinal > 0) {
			flashModeOrdinal--;
			if (hasFlash()) {
				cameraView.setFlash(flashModeOrdinal);
			}
		}
	}

	@SuppressLint("UnsafeExperimentalUsageError")
	private void startVideoRecording() {
		// play shutter sound
		if (mediaActionSound != null) {
			mediaActionSound.play(LessObnoxiousMediaActionSound.START_VIDEO_RECORDING);
		}

		float bytesPerSecond = ((float) CameraConfig.getDefaultVideoBitrate() / 8F) + ((float) CameraConfig.getDefaultAudioBitrate() / 8F);
		long durationSeconds = (long) Math.floor((float) (ThreemaApplication.MAX_BLOB_SIZE - 1000000) / bytesPerSecond); // we assume a MP4 overhead of 1 MB

		logger.debug("Calculated video duration: " + LocaleUtil.formatTimerText(durationSeconds * DateUtils.SECOND_IN_MILLIS, true));

		timerView.start(durationSeconds * DateUtils.SECOND_IN_MILLIS, time -> stopVideoRecording());

		cameraView.setCaptureMode(CameraView.CaptureMode.VIDEO);
		if (hasFlash() && cameraView.getFlash() == ImageCapture.FLASH_MODE_ON) {
			cameraView.enableTorch(true);
		}
		cameraView.setZoomRatio(0);
		cameraView.startRecording(new File(cameraCallback.getVideoFilePath()), new RuntimeUtil.MainThreadExecutor(), onVideoSavedCallback);

	}

	@SuppressLint("UnsafeExperimentalUsageError")
	private void stopVideoRecording() {
		if (timerView != null) {
			timerView.stop();
		}

		// play shutter sound
		if (mediaActionSound != null) {
			mediaActionSound.play(LessObnoxiousMediaActionSound.STOP_VIDEO_RECORDING);
		}

		if (cameraView != null) {
			// THREEMA
			try {
				// enableTorch() may crash with IllegalStateException
				cameraView.enableTorch(false);
			} catch (Exception e) {
				// ignore this
			}
			// THREEMA
			try {
				cameraView.stopRecording();
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}

	private void switchFlash() {
		@ImageCapture.FlashMode int flashMode = cameraView.getFlash();
		if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
			flashMode = ImageCapture.FLASH_MODE_ON;
		} else if (flashMode == ImageCapture.FLASH_MODE_ON) {
			flashMode = ImageCapture.FLASH_MODE_OFF;
		} else if (flashMode == ImageCapture.FLASH_MODE_OFF) {
			flashMode = ImageCapture.FLASH_MODE_AUTO;
		}
		cameraView.setFlash(flashMode);
		preferenceService.setCameraFlashMode(flashMode + 1);

		updateFlashButton();
	}

	private void updateFlashButton() {
		if (controlsContainer != null) {
			ImageView flashButton = controlsContainer.findViewById(R.id.flash_switch_button);
			try {
				boolean flashSupported = hasFlash();

				if (flashSupported) {
					@ImageCapture.FlashMode int flashMode = cameraView.getFlash();

					flashButton.post(new Runnable() {
						@Override
						public void run() {
							flashButton.setVisibility(View.VISIBLE);
							if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
								flashButton.setImageResource(R.drawable.ic_flash_auto_outline);
							} else if (flashMode == ImageCapture.FLASH_MODE_ON) {
								flashButton.setImageResource(R.drawable.ic_flash_on_outline);
							} else if (flashMode == ImageCapture.FLASH_MODE_OFF) {
								flashButton.setImageResource(R.drawable.ic_flash_off_outline);
							}
						}
					});


					return;
				}
			} catch (IllegalStateException ignored) {
			}

			flashButton.setVisibility(View.GONE);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
			switch (requestCode) {
				case PERMISSION_REQUEST_CODE_AUDIO:
					if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
						ConfigUtils.showPermissionRationale(getContext(), container, R.string.permission_record_video_audio_required);
					}
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Returns true if the CameraView's current camera has a flash unit
	 * Note: The camera will be initialized in CameraView's onMeasure(). If this method is called before tha camera is set up, it will return false.
	 * @return true if current camera has a flash unit, false otherwise or in case of error
	 */
	private boolean hasFlash() {
		if (cameraView != null) {
			Camera camera = cameraView.mCameraModule.getCamera();

			if (camera != null) {
				return camera.getCameraInfo().hasFlashUnit();
			}
		}
		return false;
	}

	/** Returns true if the device has an available back camera. False otherwise */
	private boolean hasBackCamera() {
		try {
			return cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
		} catch (Exception e) {
			return false;
		}
	}

	/** Returns true if the device has an available front camera. False otherwise */
	private boolean hasFrontCamera() {
		try {
			return cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
		} catch (Exception e) {
			return false;
		}
	}

	interface CameraCallback {
		void onImageReady(@NonNull byte[] imageData);
		void onVideoReady();
		void onError(String message);
		String getVideoFilePath();
	}

	interface CameraConfiguration {
		boolean getVideoEnable();
	}
}
