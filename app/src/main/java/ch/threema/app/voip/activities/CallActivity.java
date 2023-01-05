/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

package ch.threema.app.voip.activities;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.PictureInPictureParams;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityManager;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;

import org.slf4j.Logger;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.transition.ChangeBounds;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.dialogs.BottomSheetAbstractDialog;
import ch.threema.app.dialogs.BottomSheetListDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.SensorListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.UpdateFeatureLevelRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SensorService;
import ch.threema.app.ui.AnimatedEllipsisTextView;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AudioDevice;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.AudioSelectorButton;
import ch.threema.app.voip.CallStateSnapshot;
import ch.threema.app.voip.listeners.VoipAudioManagerListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.voip.services.CallRejectService;
import ch.threema.app.voip.services.VideoContext;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData;
import ch.threema.domain.protocol.csp.messages.voip.features.VideoFeature;
import ch.threema.localcrypto.MasterKey;
import ch.threema.storage.models.ContactModel;
import java8.util.concurrent.CompletableFuture;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
import static ch.threema.app.voip.services.VideoContext.CAMERA_FRONT;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_ACTIVITY_MODE;
import static ch.threema.app.voip.services.VoipStateService.VIDEO_RENDER_FLAG_INCOMING;
import static ch.threema.app.voip.services.VoipStateService.VIDEO_RENDER_FLAG_NONE;
import static ch.threema.app.voip.services.VoipStateService.VIDEO_RENDER_FLAG_OUTGOING;

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
public class CallActivity extends ThreemaActivity implements
		BottomSheetAbstractDialog.BottomSheetDialogCallback,
		SensorListener,
		GenericAlertDialog.DialogClickListener,
		LifecycleOwner {
	private static final Logger logger = LoggingUtil.getThreemaLogger("CallActivity");
	private static final String LIFETIME_SERVICE_TAG = "CallActivity";
	private static final String SENSOR_TAG_CALL = "voipcall";
	public static final String EXTRA_CALL_FROM_SHORTCUT = "shortcut";
	public static final String EXTRA_ACCEPT_INCOMING_CALL = "ACCEPT_INCOMING_CALL";
	private static final String DIALOG_TAG_OK = "ok";

	// saved activity states
	private static final String BUNDLE_ACTIVITY_MODE = "activityMode";
	private static final String BUNDLE_VIDEO_RENDER_MODE = "renderMode";
	private static final String BUNDLE_SWAPPED_FEEDS = "swappedFeeds";

	// Locks
	private final Object navigationLock = new Object();
	private final Object videoToggleLock = new Object();

	// Incoming call, user should decide whether to accept or reject
	public static final byte MODE_INCOMING_CALL = 1;
	// Outgoing call, connection is not yet established but should be started
	public static final byte MODE_OUTGOING_CALL = 2;
	// A call (either incoming or outgoing) is active
	public static final byte MODE_ACTIVE_CALL = 3;
	// A call has been answered
	public static final byte MODE_ANSWERED_CALL = 4;
	// Undefined mode / initial state
	public static final byte MODE_UNDEFINED = 0;

	// PIP position flags
	public static final int PIP_BOTTOM = 0x01;
	public static final int PIP_LEFT = 0x02;
	public static final int PIP_TOP = 0x04;
	public static final int PIP_RIGHT = 0x08;

	public @DrawableRes int[] audioDeviceIcons = {
		R.drawable.ic_volume_up_outline,
		R.drawable.ic_headset_mic_outline,
		R.drawable.ic_phone_in_talk,
		R.drawable.ic_bluetooth_searching_outline,
		R.drawable.ic_mic_off_outline
	};

	public @StringRes int[] audioDeviceLabels = {
		R.string.voip_speakerphone,
		R.string.voip_wired_headset,
		R.string.voip_earpiece,
		R.string.voip_bluetooth,
		R.string.voip_bluetooth,
		R.string.voip_none
	};

	// Permissions
	private final static int PERMISSION_REQUEST_RECORD_AUDIO = 9001;
	private final static int PERMISSION_REQUEST_CAMERA = 9002;
	private final static int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 9003;
	@IntDef({PERMISSION_REQUEST_RECORD_AUDIO, PERMISSION_REQUEST_CAMERA, PERMISSION_REQUEST_BLUETOOTH_CONNECT})
	private @interface PermissionRequest {}
	/**
	 * This future resolves as soon as the microphone permission request has been answered.
	 * It resolves to a boolean that indicates whether the permission was granted or not.
	 */
	private @Nullable CompletableFuture<PermissionRequestResult> micPermissionResponse;
	/**
	 * This future resolves as soon as the camera permission request has been answered.
	 * It resolves to a boolean that indicates whether the permission was granted or not.
	 */
	private @Nullable CompletableFuture<PermissionRequestResult> camPermissionResponse;
	/**
	 * This future resolves as soon as the bluetooth connect permission request has been answered.
	 * It resolves to a boolean that indicates whether the permission was granted or not.
	 */
	private @Nullable CompletableFuture<PermissionRequestResult> bluetoothConnectPermissionResponse;

	private static final String DIALOG_TAG_SELECT_AUDIO_DEVICE = "saud";

	/** Sent before initializing the disconnecting process */
	public static final String ACTION_PRE_DISCONNECT = BuildConfig.APPLICATION_ID + ".PRE_DISCONNECT";
	/** The peer device is ringing */
	public static final String ACTION_PEER_RINGING = BuildConfig.APPLICATION_ID + ".PEER_RINGING";
	/** The peer accepted the call */
	public static final String ACTION_CALL_ACCEPTED = BuildConfig.APPLICATION_ID + ".CALL_ACCEPTED";
	/** Connection has been established */
	public static final String ACTION_CONNECTED = BuildConfig.APPLICATION_ID + ".CONNECTED";
	/** A previously established connection was closed */
	public static final String ACTION_DISCONNECTED = BuildConfig.APPLICATION_ID + ".DISCONNECTED";
	/** A call that was never connected was cancelled */
	public static final String ACTION_CANCELLED = BuildConfig.APPLICATION_ID + ".CANCELLED";
	/** Debug information is being broadcasted */
	public static final String ACTION_DEBUG_INFO = BuildConfig.APPLICATION_ID + ".DEBUG_INFO";
	/** Connecting failed **/
	public static final String ACTION_CONNECTING_FAILED = BuildConfig.APPLICATION_ID + ".ERR_CONN_FAILED";
	/** Connection was temporarily lost, attempting to reconnect */
	public static final String ACTION_RECONNECTING = BuildConfig.APPLICATION_ID + ".RECONNECTING";
	/** Connection could be re-established after a connection loss */
	public static final String ACTION_RECONNECTED = BuildConfig.APPLICATION_ID + ".RECONNECTED";
	public static final String ACTION_INCOMING_VIDEO_STARTED = BuildConfig.APPLICATION_ID + ".INCOMING_VIDEO_STARTED";
	public static final String ACTION_INCOMING_VIDEO_STOPPED = BuildConfig.APPLICATION_ID + ".INCOMING_VIDEO_STOPPED";
	public static final String ACTION_OUTGOING_VIDEO_STARTED = BuildConfig.APPLICATION_ID + ".OUTGOING_VIDEO_STARTED";
	public static final String ACTION_OUTGOING_VIDEO_STOPPED = BuildConfig.APPLICATION_ID + ".OUTGOING_VIDEO_STOPPED";
	public static final String ACTION_CAMERA_CHANGED = BuildConfig.APPLICATION_ID + ".CAMERA_CHANGED";
	public static final String ACTION_DISABLE_VIDEO = BuildConfig.APPLICATION_ID + ".VIDEO_DISABLE";

	private boolean callDebugInfoEnabled = false;
	private boolean sensorEnabled = false;
	private boolean toggleVideoTooltipShown = false, audioSelectorTooltipShown = false;
	private byte activityMode;
	private boolean navigationShown = true;
	private boolean isInPictureInPictureMode = false;
	private int pipPosition;
	private int layoutMargin;
	private AudioDevice currentAudioDevice;
	private TooltipPopup toggleVideoTooltip, audioSelectorTooltip;

	private NotificationManagerCompat notificationManagerCompat;
	private AudioManager audioManager;

	private ContactService contactService;
	private SensorService sensorService;
	private PreferenceService preferenceService;
	private VoipStateService voipStateService;
	private LifetimeService lifetimeService;
	private LockAppService lockAppService;
	private APIConnector apiConnector;

	private ContactModel contact;

	private static final int KEEP_ALIVE_DELAY = 20000;
	private final static Handler keepAliveHandler = new Handler();
	private final Runnable keepAliveTask = new Runnable() {
		@Override
		public void run() {
			ThreemaApplication.activityUserInteract(CallActivity.this);
			keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY);
		}
	};

	/**
	 * The result of a permission request.
	 */
	private static class PermissionRequestResult {
		private final boolean _granted;
		private final boolean _wasAlreadyGranted;

		public PermissionRequestResult(boolean granted, boolean wasAlreadyGranted) {
			this._granted = granted;
			this._wasAlreadyGranted = wasAlreadyGranted;
		}

		/**
		 * True if the permission was granted.
		 */
		public boolean isGranted() {
			return _granted;
		}

		/**
		 * True if the permission was already granted before, and no permission request was shown.
		 */
		public boolean wasAlreadyGranted() {
			return _wasAlreadyGranted;
		}
	}

	/**
	 * Helper: Find a view and ensure it's not null.
	 */
	private <T extends View> T findView(@NonNull String name, @IdRes int viewId) {
		final T view = findViewById(viewId);
		if (view == null) {
			throw new IllegalStateException("Could not find view " + name);
		}
		return view;
	}

	private class VideoViews {
		@NonNull SurfaceViewRenderer fullscreenVideoRenderer;
		@NonNull SurfaceViewRenderer pipVideoRenderer;
		@NonNull View fullscreenVideoRendererGradient;
		@NonNull ImageView switchCamButton;
		@NonNull ImageView pipButton;

		VideoViews() {
			this.fullscreenVideoRenderer = findView("fullscreenVideoRenderer", R.id.fullscreen_video_view);
			this.fullscreenVideoRendererGradient = findView("fullscreenVideoRendererGradient", R.id.fullscreen_video_view_gradient);
			this.pipVideoRenderer = findView("pipVideoRenderer", R.id.pip_video_view);
			this.switchCamButton = findView("switchCamButton", R.id.button_call_switch_cam);
			this.pipButton = findViewById(R.id.button_picture_in_picture);
		}
	}

	private class CommonViews {
		// Layout
		ViewGroup parentLayout, contentLayout;

		// Background
		ImageView backgroundView;

		// Before-call buttons
		ViewGroup incomingCallButtonContainer, incomingCallSliderContainer;
		ImageView incomingCallButton, declineButton, answerButton;
		ObjectAnimator callButtonAnimator;
		FrameLayout accessibilityContainer;

		// In-call buttons
		ViewGroup inCallButtonContainer;
		ImageView disconnectButton, toggleMicButton;
		AudioSelectorButton audioSelectorButton;
		ImageView toggleOutgoingVideoButton;

		// Status
		EmojiTextView contactName;
		ImageView contactDots;
		AnimatedEllipsisTextView callStatus;
		Chronometer callDuration;
		TextView callDebugInfo;

		CommonViews() {
			// Layout
			this.parentLayout = findView("parentLayout", R.id.call_layout);
			this.contentLayout = findView("contentLayout", R.id.content_layout);

			// Background
			this.backgroundView = findView("backgroundView", R.id.background_view);

			// Before-call buttons
			this.incomingCallButtonContainer = findView("incomingCallButtonContainer", R.id.buttons_incoming_call_container);
			this.incomingCallSliderContainer = findView("incomingCallSliderContainer", R.id.buttons_incoming_call_slider_container);
			this.incomingCallButton = findView("incomingCallButton", R.id.button_incoming_call);
			this.declineButton = findView("declineButton", R.id.button_incoming_call_decline);
			this.answerButton = findView("answerButton", R.id.button_incoming_call_answer);
			this.accessibilityContainer = findView("accessibilityContainer", R.id.accessibility_layout);

			// In-call buttons
			this.inCallButtonContainer = findViewById(R.id.incall_buttons_container);
			this.disconnectButton = findView("disconnectButton", R.id.button_call_disconnect);
			this.toggleMicButton = findView("toggleMicButton", R.id.button_call_toggle_mic);
			this.audioSelectorButton = findView("audioSelectorButton", R.id.button_call_toggle_audio_source);
			this.toggleOutgoingVideoButton = findView("toggleVideoButton", R.id.button_call_toggle_video);

			// Status
			this.contactName = findView("contactName", R.id.call_contact_name);
			this.contactDots = findView("contactDots", R.id.call_contact_dots);
			this.callStatus = findView("callStatus", R.id.call_status);
			this.callDuration = findView("callDuration", R.id.call_duration);
			this.callDebugInfo = findView("callDebugInfo", R.id.call_debug_info);
		}
	}

	// UI elements
	private @Nullable VideoViews videoViews;
	private @Nullable CommonViews commonViews;

	// UI state
	private boolean isSwappedFeeds = true; // If true then local stream is in fullscreen renderer
	private boolean accessibilityEnabled = false;

	//region Broadcast receivers

	private final BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (action != null) {
				switch (action) {
					case ACTION_PRE_DISCONNECT:
						if (commonViews != null) {
							commonViews.callStatus.setText(getString(R.string.voip_status_disconnecting));
							commonViews.callDuration.stop();
							commonViews.callDuration.setVisibility(View.GONE);
							commonViews.callStatus.setVisibility(View.VISIBLE);
						}
						break;
					case ACTION_PEER_RINGING:
						commonViews.callStatus.setText(getString(R.string.voip_status_ringing));
						commonViews.callStatus.setVisibility(View.VISIBLE);
						break;
					case ACTION_CALL_ACCEPTED:
						commonViews.callStatus.setText(getString(R.string.voip_status_connecting));
						commonViews.callStatus.setVisibility(View.VISIBLE);
						break;
					case ACTION_CONNECTED:
						startCallDurationCounter(SystemClock.elapsedRealtime());
						break;
					case ACTION_DISCONNECTED:
						disconnect(RESULT_OK);
						break;
					case ACTION_CANCELLED:
						disconnect(RESULT_CANCELED);
						break;
					case ACTION_DEBUG_INFO:
						final String text = intent.getStringExtra("TEXT");
						commonViews.callDebugInfo.setText(text);
						break;
					case ACTION_CONNECTING_FAILED:
						if (!isDestroyed()) {
								GenericAlertDialog.newInstance(R.string.error, R.string.voip_connection_failed, R.string.ok, 0)
									.show(getSupportFragmentManager(), DIALOG_TAG_OK);
						}
						break;
					case ACTION_RECONNECTING:
						if (commonViews != null) {
							commonViews.callStatus.setText(getString(R.string.voip_status_connecting));
							commonViews.callStatus.setVisibility(View.VISIBLE);
							commonViews.callDuration.setVisibility(View.GONE);
						}
						break;
					case ACTION_RECONNECTED:
						commonViews.callStatus.setVisibility(View.GONE);
						commonViews.callDuration.setVisibility(View.VISIBLE);
						break;
					case ACTION_INCOMING_VIDEO_STARTED:
						logger.debug("Incoming video started");
						if ((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_INCOMING) == VIDEO_RENDER_FLAG_INCOMING) {
							// already in incoming mode
							break;
						}
						if (!ConfigUtils.isVideoCallsEnabled()) {
							break;
						}

						voipStateService.setVideoRenderMode(voipStateService.getVideoRenderMode() | VIDEO_RENDER_FLAG_INCOMING);

						// Update the videos. This will also swap the views.
						updateVideoViews();

						// Because we swapped the video views, our own image will remain in both views
						// until the first frame by the peer arrives. To avoid, fake a single black frame.
						final VideoContext videoContext = voipStateService.getVideoContext();
						if (videoContext != null) {
							videoContext.clearRemoteVideoSinkProxy();
						}

						// Vibrate phone quickly to indicate that the remote video stream was enabled
						if (preferenceService.isInAppVibrate()) {
							try {
								final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
								if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
									// VibrationEffect requires API>=26
									final VibrationEffect effect = VibrationEffect.createOneShot(100, 128);
									vibrator.vibrate(effect);
								} else {
									// Legacy method (API<26), use shorter vibration to compensate missing amplitude control
									vibrator.vibrate(60);
								}
							} catch (Exception e) {
								logger.warn("Could not vibrate device on incoming video stream", e);
							}
						}

						if (!audioSelectorTooltipShown && currentAudioDevice == AudioDevice.EARPIECE) {
							// remind user to switch audio device to Speakerphone
							if (commonViews != null && commonViews.audioSelectorButton.getVisibility() == View.VISIBLE) {
								commonViews.audioSelectorButton.postDelayed(() -> {
									if (navigationShown) {
										if (!audioSelectorTooltipShown && currentAudioDevice == AudioDevice.EARPIECE
											&& (voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_INCOMING) == VIDEO_RENDER_FLAG_INCOMING
											&& (voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) != VIDEO_RENDER_FLAG_OUTGOING) {
											int[] location = new int[2];
											commonViews.audioSelectorButton.getLocationInWindow(location);
											location[1] += (commonViews.audioSelectorButton.getHeight() / 5);
											audioSelectorTooltip = new TooltipPopup(CallActivity.this, R.string.preferences__tooltip_audio_selector_hint, R.layout.popup_tooltip_bottom_right, CallActivity.this);
											audioSelectorTooltip.show(CallActivity.this, commonViews.audioSelectorButton, getString(R.string.tooltip_voip_enable_speakerphone), TooltipPopup.ALIGN_ABOVE_ANCHOR_ARROW_RIGHT, location, 5000);
											audioSelectorTooltipShown = true;
										}
									}
								}, 12000);
							}
						}
						if ((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) != VIDEO_RENDER_FLAG_OUTGOING) {
							// no outgoing video. show a tooltip
							if (!toggleVideoTooltipShown) {
								if (commonViews != null && commonViews.toggleOutgoingVideoButton.getVisibility() == View.VISIBLE) {
									commonViews.toggleOutgoingVideoButton.postDelayed(() -> {
										if (navigationShown) {
											// still incoming but no outgoing video after 5 seconds
											if (((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_INCOMING) == VIDEO_RENDER_FLAG_INCOMING) &&
												((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) != VIDEO_RENDER_FLAG_OUTGOING)) {
												int[] location = new int[2];
												commonViews.toggleOutgoingVideoButton.getLocationInWindow(location);
												location[1] -= (commonViews.toggleOutgoingVideoButton.getHeight() / 5);
												toggleVideoTooltip = new TooltipPopup(CallActivity.this, 0, R.layout.popup_tooltip_top_right, CallActivity.this);
												toggleVideoTooltip.show(CallActivity.this, commonViews.toggleOutgoingVideoButton, getString(R.string.tooltip_voip_other_party_video_on), TooltipPopup.ALIGN_BELOW_ANCHOR_ARROW_RIGHT, location, 6000);
												toggleVideoTooltipShown = true;
											}
										}
									}, 5000);
								}
							}
						}
						break;
					case ACTION_INCOMING_VIDEO_STOPPED:
						logger.debug("Incoming video stopped");
						if (!ConfigUtils.isVideoCallsEnabled()) {
							break;
						}
						voipStateService.setVideoRenderMode(voipStateService.getVideoRenderMode() & ~VIDEO_RENDER_FLAG_INCOMING);
						updateVideoViews();
						if (voipStateService.getVideoRenderMode() == VIDEO_RENDER_FLAG_NONE) {
							if (!navigationShown && !isInPictureInPictureMode) {
								toggleNavigation();
							}
						}
						break;
					case ACTION_OUTGOING_VIDEO_STARTED:
						logger.debug("Outgoing video started");
						if ((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) == VIDEO_RENDER_FLAG_OUTGOING) {
							// already in outgoing mode
							break;
						}
						voipStateService.setVideoRenderMode(voipStateService.getVideoRenderMode() | VIDEO_RENDER_FLAG_OUTGOING);
						updateVideoButton(true);
						updateVideoViews();
						setPreferredAudioDevice(AudioDevice.SPEAKER_PHONE);

						// autohide navigation
						commonViews.parentLayout.postDelayed(new Runnable() {
							@Override
							public void run() {
								if (voipStateService != null && (voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) == VIDEO_RENDER_FLAG_OUTGOING) {
									hideNavigation(true);
								}
							}
						}, 5000);

						break;
					case ACTION_OUTGOING_VIDEO_STOPPED:
						logger.debug("Outgoing video stopped");
						voipStateService.setVideoRenderMode(voipStateService.getVideoRenderMode() & ~VIDEO_RENDER_FLAG_OUTGOING);
						updateVideoButton(false);
						updateVideoViews();
						if (voipStateService.getVideoRenderMode() == VIDEO_RENDER_FLAG_NONE) {
							if (!navigationShown && !isInPictureInPictureMode) {
								toggleNavigation();
							}
						}
						break;
					case ACTION_CAMERA_CHANGED:
						logger.debug("Camera changed.");
						updateVideoViewsMirror();
						break;
					case ACTION_DISABLE_VIDEO:
						logger.debug("Video disabled by peer.");
						if ((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) == VIDEO_RENDER_FLAG_OUTGOING) {
							Toast.makeText(CallActivity.this, getString(R.string.voip_peer_video_disabled), Toast.LENGTH_LONG).show();
						}
						voipStateService.setVideoRenderMode(VIDEO_RENDER_FLAG_NONE);
						if (commonViews != null) {
							setEnabled(commonViews.toggleOutgoingVideoButton, false);
						}
						updateVideoViews();
						break;
					default:
						break;
				}
			}
		}
	};

	//endregion

	//region Listeners

	private final ContactListener contactListener = new ContactListener() {
		@Override
		public void onModified(ContactModel modifiedContactModel) {
			RuntimeUtil.runOnUiThread(CallActivity.this::updateContactInfo);
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			RuntimeUtil.runOnUiThread(CallActivity.this::updateContactInfo);
		}

		@Override
		public boolean handle(String identity) {
			return contact != null && TestUtil.compare(contact.getIdentity(), identity);
		}
	};

	//endregion
	private final VoipAudioManagerListener audioManagerListener = new VoipAudioManagerListener() {
		@Override
		public void onAudioDeviceChanged(@Nullable AudioDevice selectedAudioDevice, @NonNull HashSet<AudioDevice> availableAudioDevices) {
			if (selectedAudioDevice != null) {
				currentAudioDevice = selectedAudioDevice;
				logger.debug("Audio device changed. New device = " + selectedAudioDevice.name());
				if (sensorService != null) {
					if (selectedAudioDevice == AudioDevice.EARPIECE) {
						if (!sensorService.isSensorRegistered(SENSOR_TAG_CALL)) {
							sensorService.registerSensors(SENSOR_TAG_CALL, CallActivity.this, false);
						}
						sensorEnabled = true;
					} else {
						sensorService.unregisterSensors(SENSOR_TAG_CALL);
						sensorEnabled = false;
					}
				}
				if (currentAudioDevice == AudioDevice.SPEAKER_PHONE) {
					setVolumeControlStream(AudioManager.STREAM_MUSIC);
				} else {
					setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
				}
			}
		}

		@Override
		public void onAudioFocusLost(boolean temporary) {
			// WARNING: This method is currently not being called,
			// see commit ff68bb215c8e55f03b75128ebb40ae423585c5d9.
			if (!temporary) {
				return;
			}
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					findViewById(R.id.interrupt_layout).setVisibility(View.VISIBLE);
				}
			});
		}

		@Override
		public void onAudioFocusGained() {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					findViewById(R.id.interrupt_layout).setVisibility(View.GONE);
				}
			});
		}

		@Override
		public void onMicEnabledChanged(boolean micEnabled) {
			logger.debug("onMicEnabledChanged: " + micEnabled);
			updateMicButton(micEnabled);
		}
	};

	//region Lifecycle methods

	@Override
	@UiThread
	protected void onResume() {
		logger.info("onResume");

		super.onResume();

		// Request initial audio device information
		VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_QUERY_AUDIO_DEVICES);
	}

	@SuppressLint({"ClickableViewAccessibility", "SourceLockedOrientationActivity"})
	@Override
	@UiThread
	public void onCreate(Bundle savedInstanceState) {
		logger.info("onCreate");

		super.onCreate(savedInstanceState);

		// Threema services
		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			this.contactService = serviceManager.getContactService();
			this.sensorService = serviceManager.getSensorService();
			this.preferenceService = serviceManager.getPreferenceService();
			this.voipStateService = serviceManager.getVoipStateService();
			this.lifetimeService = serviceManager.getLifetimeService();
			this.apiConnector = serviceManager.getAPIConnector();
			this.lockAppService = serviceManager.getLockAppService();
		} catch (Exception e) {
			logger.error("Could not instantiate services", e);
			finish();
			return;
		}

		if (getIntent().getBooleanExtra(EXTRA_ACCEPT_INCOMING_CALL, false)) {
			// Don't reject call automatically after timeout
			voipStateService.disableTimeoutReject();
			// In case of an incoming call we cancel the incoming call notification. Otherwise the
			// notification would stay visible until for example the microphone permission is granted
			voipStateService.cancelCallNotificationsForNewCall();
		}

		// Get audio manager
		this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		// Set window styles for fullscreen-window size. Needs to be done before
		// adding content.
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN
			| LayoutParams.FLAG_KEEP_SCREEN_ON
			| LayoutParams.FLAG_DISMISS_KEYGUARD
			| LayoutParams.FLAG_SHOW_WHEN_LOCKED
			| LayoutParams.FLAG_TURN_SCREEN_ON
			| LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

		// disable screenshots if necessary
		ConfigUtils.setScreenshotsAllowed(this, this.preferenceService, this.lockAppService);

		getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility(getWindow()));

		// Load layout
		setContentView(R.layout.activity_call);

		// Support notch
		adjustWindowOffsets();

		this.layoutMargin = getApplicationContext().getResources().getDimensionPixelSize(R.dimen.call_activity_margin);

		// Establish connection
		this.notificationManagerCompat = NotificationManagerCompat.from(this);
		AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
		if (accessibilityManager != null && accessibilityManager.isTouchExplorationEnabled()) {
			accessibilityEnabled = true;
		}

		// Check master key
		final MasterKey masterKey = ThreemaApplication.getMasterKey();
		if (masterKey != null && masterKey.isLocked()) {
			logger.warn("Cannot start call, master key is locked");
			Toast.makeText(this, R.string.master_key_locked, Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		// Acquire a Threema server connection
		this.lifetimeService.acquireConnection(LIFETIME_SERVICE_TAG);

		// Register broadcast receiver
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_PRE_DISCONNECT);
		filter.addAction(ACTION_PEER_RINGING);
		filter.addAction(ACTION_CALL_ACCEPTED);
		filter.addAction(ACTION_CONNECTED);
		filter.addAction(ACTION_DISCONNECTED);
		filter.addAction(ACTION_CANCELLED);
		filter.addAction(ACTION_DEBUG_INFO);
		filter.addAction(ACTION_CONNECTING_FAILED);
		filter.addAction(ACTION_RECONNECTING);
		filter.addAction(ACTION_RECONNECTED);
		filter.addAction(ACTION_INCOMING_VIDEO_STARTED);
		filter.addAction(ACTION_INCOMING_VIDEO_STOPPED);
		filter.addAction(ACTION_OUTGOING_VIDEO_STARTED);
		filter.addAction(ACTION_OUTGOING_VIDEO_STOPPED);
		filter.addAction(ACTION_CAMERA_CHANGED);
		filter.addAction(ACTION_DISABLE_VIDEO);
		LocalBroadcastManager.getInstance(this).registerReceiver(localBroadcastReceiver, filter);

		// Register listeners
		ListenerManager.contactListeners.add(this.contactListener);
		VoipListenerManager.audioManagerListener.add(this.audioManagerListener);

		// Restore PIP position from preferences
		pipPosition = preferenceService.getPipPosition();
		if (pipPosition == 0x00) {
			pipPosition = PIP_BOTTOM | PIP_LEFT;
		}
		adjustPipLayout();

		if (!restoreState(getIntent(), savedInstanceState)) {
			logger.warn("Unable to restore state. Finishing");
			finish();
			return;
		}

		// Check for mandatory permissions
		logger.info("Checking for audio permission...");
		this.micPermissionResponse = new CompletableFuture<>();
		if (ConfigUtils.requestAudioPermissions(this, null, PERMISSION_REQUEST_RECORD_AUDIO)) {
			this.micPermissionResponse.complete(new PermissionRequestResult(true, true));
		}

		// Initialize activity once all permissions are granted
		this.micPermissionResponse
			.thenAccept((result) -> {
				if (result.isGranted()) {
					logger.info("Audio permission granted");
					checkBluetoothPermission();
				} else {
					logger.warn("Audio permission not granted");
					Toast.makeText(CallActivity.this, R.string.permission_record_audio_required, Toast.LENGTH_LONG).show();
					abortWithError(VoipCallAnswerData.RejectReason.DISABLED);
				}
			})
			.exceptionally((e) -> {
				if (e != null) {
					logger.error("Error in initializeActivity", e);
					abortWithError();
				}
				return null;
			});

		// Check reject preferences and fix them if necessary
		if (this.preferenceService.isRejectMobileCalls()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
				&& this.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
				this.preferenceService.setRejectMobileCalls(false);
			}
		}

		// make sure lock screen is not activated during call
		keepAliveHandler.removeCallbacksAndMessages(null);
		keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY);
	}

	private void checkBluetoothPermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			initializeActivity(getIntent());
			return;
		}

		try {
			// simple check for connected headset - this still works with legacy BT permissions
			BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (bluetoothAdapter == null || BluetoothProfile.STATE_CONNECTED != bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)) {
				initializeActivity(getIntent());
				return;
			}
		} catch (Exception e) {
			logger.error("Unable to get BT connection state. Android >12?", e);
		}

		this.bluetoothConnectPermissionResponse = new CompletableFuture<>();
		if (ConfigUtils.requestBluetoothConnectPermissions(this, null, PERMISSION_REQUEST_BLUETOOTH_CONNECT, true)) {
			this.bluetoothConnectPermissionResponse.complete(new PermissionRequestResult(true, true));
		}

		// Initialize activity once all permissions are granted
		this.bluetoothConnectPermissionResponse
			.thenAccept((result) -> {
				if (result.isGranted()) {
					logger.info("BLUETOOTH_CONNECT permission granted");
				} else {
					Toast.makeText(CallActivity.this, R.string.permission_bluetooth_connect_required, Toast.LENGTH_LONG).show();
					logger.warn("BLUETOOTH_CONNECT permission not granted");
					// simply continue without bluetooth support
				}
				initializeActivity(getIntent());
			})
			.exceptionally((e) -> {
				if (e != null) {
					logger.error("Error in checkBluetoothConnect", e);
					abortWithError();
				}
				return null;
			});
	}


	private boolean restoreState(@NonNull Intent intent, Bundle savedInstanceState) {
		// Every valid intent must either be a call action intent,
		// or specify the contact identity.
		String contactIdentity = intent.getStringExtra(VoipCallService.EXTRA_CONTACT_IDENTITY);
		if (contactIdentity == null) {
			logger.error("Error while initializing call: Missing contact identity in intent!");
			return false;
		}

		final CallStateSnapshot callState = voipStateService.getCallState();

		// restore a previously saved activity state in case the activity was killed by the system
		// note: the activity mode should override conflicting settings of a re-delivered intent (which reflects the state when the activity was first set up)
		this.activityMode = MODE_UNDEFINED;
		if (savedInstanceState != null && VoipCallService.isRunning()) {
			// the activity was killed and restarted by the system - restore previous configuration
			this.activityMode = savedInstanceState.getByte(BUNDLE_ACTIVITY_MODE, this.activityMode);
			this.isSwappedFeeds = savedInstanceState.getBoolean(BUNDLE_SWAPPED_FEEDS, false);
			this.voipStateService.setVideoRenderMode(savedInstanceState.getInt(BUNDLE_VIDEO_RENDER_MODE, VIDEO_RENDER_FLAG_NONE));
		}

		// Determine activity mode
		if (intent.getBooleanExtra(EXTRA_CALL_FROM_SHORTCUT, false)) {
			if (!callState.isIdle()) {
				logger.error("Ongoing call - ignore shortcut");
				return false;
			}
			// a shortcut call is always outgoing
			this.activityMode = MODE_OUTGOING_CALL;
		} else {
			if (this.activityMode == MODE_UNDEFINED) {
				// use the intent only if activity is new
				this.activityMode = intent.getByteExtra(VoipCallService.EXTRA_ACTIVITY_MODE, MODE_UNDEFINED);
			}
		}

		// Activity mode sanity checks
		if (this.activityMode == MODE_INCOMING_CALL && callState.isIdle()) {
			logger.error("Started CallActivity (incoming call) when call state is IDLE");
			return false;
		}

		logger.info("Restored activity mode: {}", activityMode);
		logger.info("Restored call state: " + voipStateService.getCallState());
		logger.info("Restored Video flags: {}", Utils.byteToHex((byte) voipStateService.getVideoRenderMode(), true, true));

		// Fetch contact
		this.contact = contactService.getByIdentity(contactIdentity);
		if (this.contact == null) {
			logger.info("Contact is null");
			return false;
		}

		return true;
	}

	@Override
	protected void onPause() {
		logger.trace("onPause");

		if (this.activityMode == MODE_INCOMING_CALL
				&& !this.notificationManagerCompat.areNotificationsEnabled()) {
			// abort cus we're unable to put up an ongoing notification
			logger.warn("Could not start call, since notifications are disabled");
			rejectOrCancelCall(VoipCallAnswerData.RejectReason.DISABLED);
		}

		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		logger.info("onSaveInstanceState");

		outState.putByte(BUNDLE_ACTIVITY_MODE, activityMode);
		outState.putBoolean(BUNDLE_SWAPPED_FEEDS, isSwappedFeeds);
		outState.putInt(BUNDLE_VIDEO_RENDER_MODE, voipStateService.getVideoRenderMode());

		super.onSaveInstanceState(outState);
	}

	@Override
	@UiThread
	protected void onDestroy() {
		logger.info("onDestroy");

		// Remove call button animation listeners
		if (this.commonViews != null) {
			if (this.commonViews.callButtonAnimator != null && !accessibilityEnabled) {
				this.commonViews.callButtonAnimator.removeAllListeners();
				this.commonViews.callButtonAnimator.cancel();
				this.commonViews.callButtonAnimator = null;
			}
		}

		if (this.voipStateService != null) {
			// stop capturing
			if ((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) == VIDEO_RENDER_FLAG_OUTGOING) {
				// disable outgoing video
				VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_STOP_CAPTURING);

				// make sure outgoing flag is cleared
				voipStateService.setVideoRenderMode(voipStateService.getVideoRenderMode() & ~VIDEO_RENDER_FLAG_OUTGOING);
			}

			// Unset video target
			if (this.voipStateService.getVideoContext() != null) {
				this.voipStateService.getVideoContext().setLocalVideoSinkTarget(null);
				this.voipStateService.getVideoContext().setRemoteVideoSinkTarget(null);
			}
		}

		// Release connection
		if (this.lifetimeService != null) {
			this.lifetimeService.releaseConnection(LIFETIME_SERVICE_TAG);
		}

		// Unregister receivers
		LocalBroadcastManager.getInstance(this).unregisterReceiver(this.localBroadcastReceiver);

		// Unregister sensor listeners
		if (sensorService != null) {
			sensorService.unregisterSensors(SENSOR_TAG_CALL);
			sensorEnabled = false;
		}

		// Unregister other listeners
		ListenerManager.contactListeners.remove(this.contactListener);
		VoipListenerManager.audioManagerListener.remove(this.audioManagerListener);

		// Release renderers
		if (this.videoViews != null) {
			this.videoViews.fullscreenVideoRenderer.release();
			this.videoViews.pipVideoRenderer.release();
			this.videoViews = null;
		}

		if (this.preferenceService != null) {
			this.preferenceService.setPipPosition(pipPosition);
		}

		// remove lockscreen keepalive
		keepAliveHandler.removeCallbacksAndMessages(null);

		// Remove uncaught exception handler
		Thread.setDefaultUncaughtExceptionHandler(null);

		super.onDestroy();
	}

	@Override
	@UiThread
	protected void onNewIntent(Intent intent) {
		logger.info("onNewIntent");
		super.onNewIntent(intent);
		setIntent(intent);
		if (restoreState(intent, null)) {
			try {
				this.initializeActivity(intent);
			} catch (Exception e) {
				logger.error("Error in initializeActivity", e);
				this.abortWithError();
			}
		} else {
			logger.info("Unable to restore state");
			this.abortWithError();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
			// mute notification
			if (voipStateService != null) {
				if (voipStateService.muteRingtone()) {
					return true;
				}
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	//endregion

	//region UI helpers

	/**
	 * Enable or disable collecting and display of debug information.
	 *
	 * @param enabled Collect and show debug info?
	 * @param force If this is set to "true", then the VoipCallService will be notified even if the
	 * value hasn't changed.
	 */
	@SuppressLint("SetTextI18n")
	@UiThread
	private void enableDebugInfo(boolean enabled, boolean force) {
		// Sanity check: Ensure that views are initialized
		if (this.commonViews == null) {
			logger.error("Error: Common views not yet initialized!");
			return;
		}

		final boolean changed = enabled != this.callDebugInfoEnabled;
		logger.debug("enableDebugInfo={},force={},changed={}", enabled, force, changed);
		this.callDebugInfoEnabled = enabled;
		this.commonViews.callDebugInfo.setVisibility(enabled ? View.VISIBLE : View.GONE);
		if (changed || force) {
			final String action = enabled ? VoipCallService.ACTION_ENABLE_DEBUG_INFO : VoipCallService.ACTION_DISABLE_DEBUG_INFO;
			VoipUtil.sendVoipBroadcast(getApplicationContext(), action);
			if (!enabled) {
				this.commonViews.callDebugInfo.setText("Debug:");
			}
		}
	}

	/**
	 * Update all video related views to reflect current video configuration.
	 * Will launch video rendering if necessary and video is enabled
	 */
	private void updateVideoViews() {
		int videoMode = voipStateService.getVideoRenderMode();

		if (videoMode != VIDEO_RENDER_FLAG_NONE) {
			setupVideoRendering();
		}

		boolean incomingVideo = (videoMode & VIDEO_RENDER_FLAG_INCOMING) == VIDEO_RENDER_FLAG_INCOMING;
		boolean outgoingVideo = (videoMode & VIDEO_RENDER_FLAG_OUTGOING) == VIDEO_RENDER_FLAG_OUTGOING;

		if (this.videoViews != null && this.commonViews != null) {
			if (incomingVideo && outgoingVideo) {
				this.videoViews.pipVideoRenderer.setVisibility(View.VISIBLE);
			} else {
				this.videoViews.pipVideoRenderer.setVisibility(View.GONE);
			}

			if (incomingVideo || outgoingVideo) {
				// Make video views visible
				this.videoViews.fullscreenVideoRenderer.setVisibility(View.VISIBLE);
				if (this.commonViews.backgroundView != null) {
					this.commonViews.backgroundView.setVisibility(View.INVISIBLE);
				}

				this.videoViews.switchCamButton.setVisibility(outgoingVideo &&
					(voipStateService.getVideoContext() != null && voipStateService.getVideoContext().hasMultipleCameras()) ?
					View.VISIBLE :
					View.GONE);
				this.videoViews.pipButton.setVisibility(ConfigUtils.supportsPictureInPicture(this) ? View.VISIBLE : View.GONE);

				if (incomingVideo && !outgoingVideo) {
					setSwappedFeeds(false);
				} else if (!incomingVideo) {
					setSwappedFeeds(true);
				} else {
					setSwappedFeeds(false);
				}
			} else {
				// audio only
				this.videoViews.fullscreenVideoRenderer.setVisibility(View.GONE);
				this.videoViews.switchCamButton.setVisibility(View.GONE);
				this.videoViews.pipButton.setVisibility(View.GONE);
				if (this.commonViews.backgroundView != null) {
					this.commonViews.backgroundView.setVisibility(View.VISIBLE);
				}
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ConfigUtils.supportsPictureInPicture(this)) {
					setPictureInPictureParams(new PictureInPictureParams.Builder().setAutoEnterEnabled(false).build());
				}
			}
		}
	}

	/**
	 * Set correct video orientation depending on current video configuration and active camera
	 */
	private void updateVideoViewsMirror() {
		VideoContext videoContext = this.voipStateService.getVideoContext();
		if (videoContext != null) {
			@VideoContext.CameraOrientation int orientation = videoContext.getCameraOrientation();
			if (this.videoViews != null) {
				if (isSwappedFeeds) {
					// outgoing on big view
					if (orientation == CAMERA_FRONT) {
						this.videoViews.fullscreenVideoRenderer.setMirror(true);
					} else {
						this.videoViews.fullscreenVideoRenderer.setMirror(false);
					}
					this.videoViews.pipVideoRenderer.setMirror(false);
				} else {
					// outgoing on small view
					if (orientation == CAMERA_FRONT) {
						this.videoViews.pipVideoRenderer.setMirror(true);
					} else {
						this.videoViews.pipVideoRenderer.setMirror(false);
					}
					this.videoViews.fullscreenVideoRenderer.setMirror(false);
				}
			}
		}
	}

	//endregion

	private void updateMicButton(boolean micEnabled) {
		if (this.commonViews != null) {
			this.commonViews.toggleMicButton.setImageResource(micEnabled ? R.drawable.ic_keyboard_voice_outline : R.drawable.ic_mic_off_outline);
			this.commonViews.toggleMicButton.setContentDescription(micEnabled ? getString(R.string.voip_mic_disable) : getString(R.string.voip_mic_enable));
		}
	}

	private void updateVideoButton(boolean cameraEnabled) {
		if (this.commonViews != null) {
			this.commonViews.toggleOutgoingVideoButton.setImageResource(cameraEnabled ?
				R.drawable.ic_videocam_black_outline :
				R.drawable.ic_videocam_off_black_outline);
			this.commonViews.toggleOutgoingVideoButton.setContentDescription(cameraEnabled ?
				getString(R.string.video_camera_on) :
				getString(R.string.video_camera_off));
		}
	}

	@SuppressLint("StaticFieldLeak")
	@UiThread
	private void updateContactInfo() {
		if (this.commonViews == null) {
			// UI not yet initialized
			return;
		}

		if (contact != null) {
			// Set background to blurred avatar.
			new AsyncTask<Void, Void, Bitmap>() {
				@Override
				protected Bitmap doInBackground(Void... voids) {
					return BitmapUtil.blurBitmap(
						contactService.getAvatar(contact, true),
						CallActivity.this
					);
				}

				@Override
				protected void onPostExecute(Bitmap blurredAvatar) {
					if (!isDestroyed() && ! isFinishing()) {
						commonViews.backgroundView.setImageBitmap(blurredAvatar);
					}
				}
			}.execute();

			this.commonViews.contactName.setText(NameUtil.getDisplayNameOrNickname(contact, true));
			this.commonViews.contactDots.setImageDrawable(ContactUtil.getVerificationDrawable(this, contact));
		}
	}

	//region Activity initialization

	/**
	 * Initialize the activity with the specified intent.
	 */
	@SuppressLint("ClickableViewAccessibility")
	@UiThread
	private void initializeActivity(final Intent intent) {
		logger.info("Initialize activity");

		final long callId = this.voipStateService.getCallState().getCallId();
		final Boolean isInitiator = this.voipStateService.isInitiator();

		// Start service if it is an incoming call
		if (intent.getBooleanExtra(EXTRA_ACCEPT_INCOMING_CALL, false)) {
			Intent voipCallServiceIntent = new Intent(this, VoipCallService.class);
			voipCallServiceIntent.putExtras(intent.getExtras());
			ContextCompat.startForegroundService(this, voipCallServiceIntent);
		}

		// Initialize view groups
		this.commonViews = new CommonViews();

		// Check feature mask and enable video button if peer supports and requests video calls
		setEnabled(this.commonViews.toggleOutgoingVideoButton, false);
		if (ConfigUtils.isVideoCallsEnabled()) {
			final VoipCallOfferData offerData = this.voipStateService.getCallOffer(callId);
			if (offerData != null && isInitiator == Boolean.FALSE) {
				// Incoming call. In this case we don't need to check the feature level, only the
				// call feature list in the offer.
				boolean videoEnabled = offerData.getFeatures().hasFeature(VideoFeature.NAME);
				setEnabled(this.commonViews.toggleOutgoingVideoButton, videoEnabled);
			} else {
				// Outgoing call. Check the feature mask of the remote contact.
				if (ThreemaFeature.canVideocall(contact.getFeatureMask())) {
					setEnabled(this.commonViews.toggleOutgoingVideoButton, true);
				} else {
					try {
						CompletableFuture
							.runAsync(new UpdateFeatureLevelRoutine(
								contactService,
								apiConnector,
								Collections.singletonList(contact)
							))
							.thenRun(() -> RuntimeUtil.runOnUiThread(() -> {
								if (!isDestroyed()) {
									if (commonViews != null) {
										setEnabled(commonViews.toggleOutgoingVideoButton, ThreemaFeature.canVideocall(contact.getFeatureMask()));
									}
								}
							}))
							.get();
					} catch (InterruptedException | ExecutionException e) {
						logger.warn("Unable to fetch feature mask");
					}
				}
			}
		}

		// Attach UI event handlers
		this.commonViews.contactName.setOnLongClickListener(view -> {
			// In DEBUG builds, the call debug info can be shown and hidden by long-pressing
			// on the contact name. (In release builds, most of this information is also
			// being logged into the debug log, so if you need to debug something, use
			// the logfile.)
			if (BuildConfig.DEBUG) {
				enableDebugInfo(!callDebugInfoEnabled, false);
			}
			return true;
		});
		this.commonViews.disconnectButton.setOnClickListener(new DebouncedOnClickListener(1000) {
			@Override
			public void onDebouncedClick(View view) {
				logger.info("Disconnect button pressed. Ending call.");
				VoipUtil.sendVoipCommand(CallActivity.this, VoipCallService.class, VoipCallService.ACTION_HANGUP);
			}
		});
		this.commonViews.toggleMicButton.setOnClickListener(view -> {
			VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_MUTE_TOGGLE);
		});
		this.commonViews.toggleMicButton.post(new Runnable() {
			@Override
			public void run() {
				// we request the initial configuration as soon as the button has been created
				VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_QUERY_MIC_ENABLED);
			}
		});
		this.commonViews.audioSelectorButton.setAudioDeviceMultiSelectListener((audioDevices, selectedDevice) -> {
			int i = 0, currentDeviceIndex = -1;
			ArrayList<BottomSheetItem> items = new ArrayList<>();

			for (AudioDevice device : audioDevices) {
				int index = device.ordinal();
				items.add(new BottomSheetItem(
						audioDeviceIcons[index],
						getString(audioDeviceLabels[index]),
						String.valueOf(index)
					)
				);
				if (device.equals(selectedDevice)) {
					currentDeviceIndex = i;
				}
				i++;
			}

			BottomSheetListDialog dialog = BottomSheetListDialog.newInstance(0, items, currentDeviceIndex);
			dialog.show(getSupportFragmentManager(), DIALOG_TAG_SELECT_AUDIO_DEVICE);
		});
		this.commonViews.audioSelectorButton.post(() -> {
			// We request the initial configuration as soon as the button has been created
			VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_QUERY_AUDIO_DEVICES);
		});
		this.commonViews.incomingCallButton.setOnTouchListener(new View.OnTouchListener() {
			float dX, oX, newX;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						dX = v.getX() - event.getRawX();
						oX = v.getX();
						break;
					case MotionEvent.ACTION_MOVE:
						newX = event.getRawX() + dX;
						if (newX < commonViews.declineButton.getX() + commonViews.incomingCallSliderContainer.getX()) {
							newX = commonViews.declineButton.getX() + commonViews.incomingCallSliderContainer.getX();
						} else if (newX > commonViews.answerButton.getX()) {
							newX = commonViews.answerButton.getX() + commonViews.incomingCallSliderContainer.getX();
						}

						v.animate()
							.x(newX)
							.setDuration(0)
							.start();
						break;
					case MotionEvent.ACTION_UP:
						newX = event.getRawX() + dX;
						if (newX > commonViews.answerButton.getX() + commonViews.incomingCallSliderContainer.getX()) {
							answerCall();
						} else if (newX < commonViews.declineButton.getX() + commonViews.incomingCallSliderContainer.getX()) {
							rejectOrCancelCall(VoipCallAnswerData.RejectReason.REJECTED);
						} else {
							v.animate()
								.x(oX)
								.setDuration(200)
								.start();
						}
						break;
					default:
						return false;
				}
				return true;
			}
		});
		this.commonViews.toggleOutgoingVideoButton.setOnClickListener(v -> {
			synchronized (this.videoToggleLock) {
				logger.info("Toggle outgoing video");

				if (!isEnabled(v)) {
					if (navigationShown) {
						int[] location = new int[2];
						v.getLocationInWindow(location);
						location[1] -= (v.getHeight() / 5);
						TooltipPopup tooltipPopup = new TooltipPopup(CallActivity.this, 0, R.layout.popup_tooltip_top_right, CallActivity.this);
						tooltipPopup.show(CallActivity.this, v, getString(R.string.tooltip_voip_other_party_video_disabled), TooltipPopup.ALIGN_BELOW_ANCHOR_ARROW_RIGHT, location, 3000);
					}
					return;
				}

				if ((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) == VIDEO_RENDER_FLAG_OUTGOING) {
					// disable outgoing video
					VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_STOP_CAPTURING);
				} else {
					// enable outgoing
					if (this.camPermissionResponse != null) {
						// Make sure to cancel old instances of the completablefuture
						this.camPermissionResponse.cancel(true);
					}
					this.camPermissionResponse = new CompletableFuture<>();
					if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
						// If permission was already granted, complete immediately
						this.camPermissionResponse.complete(new PermissionRequestResult(true, true));
					}
					this.camPermissionResponse
						.thenAccept((result) -> {
							synchronized (this.videoToggleLock) {
								if (result.isGranted()) {
									// Permission was granted
									logger.debug("Permission granted, set up video views");

									// Start capturing
									VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_START_CAPTURING);
								} else {
									// Permission was rejected
									Toast.makeText(CallActivity.this, R.string.permission_camera_videocall_required, Toast.LENGTH_LONG).show();
								}
							}
						})
						.exceptionally((e) -> {
							if (e != null) {
								logger.error("Error", e);
							}
							return null;
						});
				}
			}
		});

		// Initialize avatar
		updateContactInfo();

		// Initialize UI controls
		this.commonViews.callStatus.setVisibility(View.VISIBLE);
		this.commonViews.callDuration.setVisibility(View.GONE); // Initially invisible
		this.commonViews.callDuration.stop();
		this.commonViews.callDebugInfo.setText("Debug:");

		// Initialize timer
		final long chronoStartTime = intent.getLongExtra(VoipCallService.EXTRA_START_TIME, SystemClock.elapsedRealtime());

		// Call buttons
		if (accessibilityEnabled) {
			// Register on-click listeners for answer and reject buttons
			findViewById(R.id.accessibility_decline)
				.setOnClickListener(v -> rejectOrCancelCall(VoipCallAnswerData.RejectReason.REJECTED));
			findViewById(R.id.accessibility_answer)
				.setOnClickListener(v -> answerCall());

			// Update visibility of UI elements
			this.commonViews.accessibilityContainer.setVisibility(activityMode == MODE_INCOMING_CALL ? View.VISIBLE : View.GONE);
			this.commonViews.incomingCallButtonContainer.setVisibility(View.GONE);
			this.commonViews.incomingCallButton.setVisibility(View.GONE);
		} else {
			this.commonViews.incomingCallButtonContainer.setVisibility(activityMode == MODE_INCOMING_CALL ? View.VISIBLE : View.GONE);
			this.commonViews.incomingCallButton.setVisibility(activityMode == MODE_INCOMING_CALL ? View.VISIBLE : View.GONE);
		}

		this.commonViews.disconnectButton.setVisibility(activityMode == MODE_INCOMING_CALL ? View.GONE : View.VISIBLE);
		this.commonViews.toggleMicButton.setVisibility(activityMode == MODE_INCOMING_CALL ? View.GONE : View.VISIBLE);
		this.commonViews.audioSelectorButton.setVisibility(activityMode == MODE_INCOMING_CALL ? View.GONE : View.VISIBLE);

		// Update UI depending on activity mode
		switch (activityMode) {
			case MODE_ACTIVE_CALL:
				logger.info("Activity mode: Active call");
				this.commonViews.toggleOutgoingVideoButton.setVisibility(ConfigUtils.isVideoCallsEnabled() ? View.VISIBLE : View.GONE);
				if (this.voipStateService.getCallState().isCalling()) {
					// Call is already connected
					this.commonViews.callDuration.setVisibility(View.VISIBLE);
					this.commonViews.callStatus.setVisibility(View.GONE);
					this.startCallDurationCounter(chronoStartTime);
					updateVideoViews();
				} else {
					// Call is not yet connected
					this.commonViews.callDuration.setVisibility(View.GONE);
					this.commonViews.callStatus.setVisibility(View.VISIBLE);
					if (this.voipStateService.isPeerRinging()) {
						this.commonViews.callStatus.setText(getString(R.string.voip_status_ringing));
					} else {
						// If it is not ringing, show initialization text. The connecting state is
						// usually very short and it is unlikely to restart this activity in this
						// state. However, if it is still resumed while connecting, the call will be
						// initialized very soon anyway and the call status text will be replaced.
						this.commonViews.callStatus.setText(getString(R.string.voip_status_initializing));
					}
					if (this.videoViews != null) {
						this.videoViews.switchCamButton.setVisibility(View.GONE);
					}
				}
				break;
			case MODE_INCOMING_CALL:
				logger.info("Activity mode: Incoming call");
				setVolumeControlStream(AudioManager.STREAM_RING);
				this.commonViews.callStatus.setText(getString(R.string.voip_notification_title));
				this.commonViews.toggleOutgoingVideoButton.setVisibility(View.GONE);
				if (this.commonViews.callButtonAnimator == null && !accessibilityEnabled) {
					this.commonViews.callButtonAnimator = AnimationUtil.pulseAnimate(this.commonViews.incomingCallButton, 600);
				}
				break;
			case MODE_OUTGOING_CALL:
				logger.info("Activity mode: Outgoing call");
				this.commonViews.toggleOutgoingVideoButton.setVisibility(ConfigUtils.isVideoCallsEnabled() ? View.VISIBLE : View.GONE);
				this.commonViews.callStatus.setText(getString(R.string.voip_status_initializing));
				// copy over extras from activity
				final Intent serviceIntent = new Intent(intent);
				serviceIntent.setClass(this, VoipCallService.class);
				ContextCompat.startForegroundService(this, serviceIntent);

				if (ConfigUtils.isVideoCallsEnabled()) {
					if (preferenceService.getVideoCallToggleTooltipCount() < 1) {
						try {
							TapTargetView.showFor(CallActivity.this,
								TapTarget.forView(commonViews.toggleOutgoingVideoButton, getString(R.string.video_calls), getString(R.string.tooltip_voip_turn_on_camera))
									.outerCircleColor(ConfigUtils.getAppTheme(CallActivity.this) == ConfigUtils.THEME_DARK ? R.color.dark_accent : R.color.accent_light)      // Specify a color for the outer circle
									.outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
									.targetCircleColor(android.R.color.white)   // Specify a color for the target circle
									.titleTextSize(24)                  // Specify the size (in sp) of the title text
									.titleTextColor(android.R.color.white)      // Specify the color of the title text
									.descriptionTextSize(18)            // Specify the size (in sp) of the description text
									.descriptionTextColor(android.R.color.white)  // Specify the color of the description text
									.textColor(android.R.color.white)            // Specify a color for both the title and description text
									.textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
									.dimColor(android.R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
									.drawShadow(true)                   // Whether to draw a drop shadow or not
									.cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
									.tintTarget(true)                   // Whether to tint the target view's color
									.transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
									.targetRadius(50),                  // Specify the target radius (in dp)
								new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
									@Override
									public void onTargetClick(TapTargetView view) {
										super.onTargetClick(view);
										commonViews.toggleOutgoingVideoButton.performClick();
									}
								});
						} catch (Exception ignore) {
							// catch null typeface exception on CROSSCALL Action-X3
						}
						preferenceService.incremenetVideoCallToggleTooltipCount();
					}
				}
				break;
			case MODE_ANSWERED_CALL:
				logger.info("Activity mode: Answered call");
				this.commonViews.toggleOutgoingVideoButton.setVisibility(ConfigUtils.isVideoCallsEnabled() ? View.VISIBLE : View.GONE);
				break;
			default:
				logger.error("Cannot initialize activity if EXTRA_ACTIVITY_MODE is not set or undefined");
				finish();
		}

		// update UI depending on video configuration
		updateVideoButton((voipStateService.getVideoRenderMode() & VIDEO_RENDER_FLAG_OUTGOING) == VIDEO_RENDER_FLAG_OUTGOING);
	}

	/**
	 * Configure video capturing and rendering.
	 * Is safe to call multiple times
	 */
	@SuppressLint("ClickableViewAccessibility")
	private void setupVideoRendering() {
		logger.info("setupVideoRendering");

		// Find video views
		if (this.videoViews == null) {
			logger.debug("Video views not yet initialized, initializing!");
			this.videoViews = new VideoViews();
		} else {
			logger.debug("Video views already initialized");
			return;
		}

		// Initialize video views as soon as video context was created.
		// If the video context already exists, this will execute immediately.
		this.voipStateService.getVideoContextFuture().thenAccept(videoContext -> {
			// Initialize renderers
			logger.info("Initializing video renderers");
			this.videoViews.fullscreenVideoRenderer.init(videoContext.getEglBaseContext(), new RendererCommon.RendererEvents() {
				@Override
				public void onFirstFrameRendered() {
					logger.info("Fullscreen: First frame rendered");
				}

				@Override
				public void onFrameResolutionChanged(int x, int y, int a) {
					logger.info("Fullscreen: Resolution changed to {}x{}∠{}°", x, y, a);
					videoContext.setFrameDimensions(x, y);

					// Set picture in picture params to the given aspect ratio
					if (ConfigUtils.supportsPictureInPicture(CallActivity.this)) {
						PictureInPictureParams params = createPictureInPictureParams();
						if (params != null) {
							setPictureInPictureParams(params);
						} else {
							logger.info("PictureInPictureParams are null");
						}
					}
				}
			});
			this.videoViews.fullscreenVideoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
			this.videoViews.fullscreenVideoRenderer.setMirror(false);
			this.videoViews.fullscreenVideoRenderer.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (activityMode == MODE_ACTIVE_CALL || activityMode == MODE_OUTGOING_CALL) {
						toggleNavigation();
					}
				}
			});

			this.videoViews.pipVideoRenderer.init(videoContext.getEglBaseContext(), null);
			this.videoViews.pipVideoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
			this.videoViews.pipVideoRenderer.setMirror(true);
			this.videoViews.pipVideoRenderer.setZOrderMediaOverlay(true);
			this.videoViews.pipVideoRenderer.setOnClickListener(v -> this.setSwappedFeeds(!this.isSwappedFeeds));
			this.videoViews.pipVideoRenderer.setOnTouchListener(new View.OnTouchListener() {
				float dX, dY, oX, oY, newX, newY;

				private GestureDetector gestureDetector = new GestureDetector(CallActivity.this, new GestureDetector.SimpleOnGestureListener() {
					@Override
					public boolean onSingleTapConfirmed(MotionEvent e) {
						if (videoViews != null && videoViews.pipVideoRenderer != null) {
							videoViews.pipVideoRenderer.setTranslationX(0);
							videoViews.pipVideoRenderer.setTranslationY(0);
							videoViews.pipVideoRenderer.performClick();
						}
						return true;
					}
				});

				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (gestureDetector.onTouchEvent(event)) {
						return true;
					}

					switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN:
							dX = v.getX() - event.getRawX();
							oX = v.getX();
							dY = v.getY() - event.getRawY();
							oY = v.getY();
							break;
						case MotionEvent.ACTION_MOVE:
							newX = event.getRawX() + dX;
							newY = event.getRawY() + dY;

							if (newX < layoutMargin) {
								newX = layoutMargin;
							} else if (newX > commonViews.backgroundView.getWidth() - videoViews.pipVideoRenderer.getWidth() - layoutMargin) {
								newX = commonViews.backgroundView.getWidth() - videoViews.pipVideoRenderer.getWidth() - layoutMargin;
							}

							if (newY < layoutMargin) {
								newY = layoutMargin;
							} else if (newY > commonViews.backgroundView.getHeight() - videoViews.pipVideoRenderer.getHeight() - layoutMargin) {
								newY = commonViews.backgroundView.getHeight() - videoViews.pipVideoRenderer.getHeight() - layoutMargin;
							}

							v.animate()
								.x(newX)
								.y(newY)
								.setDuration(0)
								.start();
							break;
						case MotionEvent.ACTION_UP:
							newX = event.getRawX() + dX;
							newY = event.getRawY() + dY;
							snapPip(v, (int) newX, (int) newY);
						break;
						default:
							return false;
					}
					return true;
				}
			});

			// Set sink targets
			this.setVideoSinkTargets(videoContext);

			// Handle camera flipping
			this.videoViews.switchCamButton.setOnClickListener(v -> {
				VoipUtil.sendVoipBroadcast(getApplicationContext(), VoipCallService.ACTION_SWITCH_CAMERA);
			});
			this.videoViews.pipButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					enterPictureInPictureMode(true);
				}
			});
		}).exceptionally((e) -> {
			if (e != null) {
				logger.error("Error in setupVideoRendering", e);
				abortWithError();
			}
			return null;
		});
	}

	//endregion

	//region PIP position handling
	private void snapPip(View v, int x, int y) {
		View callerContainer = findViewById(R.id.caller_container);

		int topSnap = callerContainer.getBottom() + layoutMargin;
		int bottomSnap = commonViews.inCallButtonContainer.getTop() - videoViews.pipVideoRenderer.getHeight() - layoutMargin;
		int rightSnap = commonViews.backgroundView.getWidth() - videoViews.pipVideoRenderer.getWidth() - layoutMargin;
		int snappedX, snappedY;

		pipPosition = 0;

		if (x > ((rightSnap - layoutMargin) / 2)) {
			pipPosition |= PIP_RIGHT;
			snappedX = rightSnap;
		} else {
			pipPosition |= PIP_LEFT;
			snappedX = layoutMargin;
		}

		if (y > ((bottomSnap - layoutMargin) / 2)) {
			pipPosition |= PIP_BOTTOM;
			snappedY = bottomSnap;
		} else {
			pipPosition |= PIP_TOP;
			snappedY = topSnap;
		}

		v.animate()
			.withEndAction(() -> adjustPipLayout())
			.x(snappedX)
			.y(snappedY)
			.setDuration(150)
			.start();
	}

	@UiThread
	private void adjustPipLayout() {
		ConstraintLayout constraintLayout = findViewById(R.id.content_layout);
		if (constraintLayout == null) {
			return;
		}

		ConstraintSet constraintSet = new ConstraintSet();
		constraintSet.clone(constraintLayout);

		constraintSet.clear(R.id.pip_video_view, ConstraintSet.LEFT);
		constraintSet.clear(R.id.pip_video_view, ConstraintSet.RIGHT);
		constraintSet.clear(R.id.pip_video_view, ConstraintSet.BOTTOM);
		constraintSet.clear(R.id.pip_video_view, ConstraintSet.TOP);

		constraintSet.setTranslationX(R.id.pip_video_view, 0);
		constraintSet.setTranslationY(R.id.pip_video_view, 0);

		if ((pipPosition & PIP_RIGHT) == PIP_RIGHT) {
			constraintSet.connect(R.id.pip_video_view, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, layoutMargin);
		} else {
			constraintSet.connect(R.id.pip_video_view, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, layoutMargin);
		}

		if ((pipPosition & PIP_BOTTOM) == PIP_BOTTOM) {
			constraintSet.connect(R.id.pip_video_view, ConstraintSet.BOTTOM, R.id.incall_buttons_container, ConstraintSet.TOP, layoutMargin);
		} else {
			constraintSet.connect(R.id.pip_video_view, ConstraintSet.TOP, R.id.caller_container, ConstraintSet.BOTTOM, layoutMargin);
		}

		constraintSet.applyTo(constraintLayout);
	}

	//endregion

	//region Accept and reject calls

	@UiThread
	private void answerCall() {
		logger.info("Answer call");
		this.activityMode = MODE_ANSWERED_CALL;

		// Recreate activity with correct activity mode and with EXTRA_ACCEPT_INCOMING_CALL
		Intent restartActivityIntent = new Intent(this, CallActivity.class);
		restartActivityIntent.putExtras(getIntent().getExtras());
		restartActivityIntent.putExtra(EXTRA_ACCEPT_INCOMING_CALL, true);
		restartActivityIntent.putExtra(EXTRA_ACTIVITY_MODE, CallActivity.MODE_ACTIVE_CALL);

		finish();
		startActivity(restartActivityIntent);
		overridePendingTransition(0, 0);
	}

	/**
	 * Reject (when incoming) or cancel (when outgoing) a call with the specified reason.
	 * @param reason See `VoipCallAnswerData.RejectReason`
	 */
	@UiThread
	private void rejectOrCancelCall(byte reason) {
		final long callId = this.voipStateService.getCallState().getCallId();
		logger.info("{}: rejectOrCancelCall", callId);
		if (this.activityMode == MODE_INCOMING_CALL) {
			final Intent rejectIntent = new Intent(this, CallRejectService.class);
			rejectIntent.putExtra(VoipCallService.EXTRA_CONTACT_IDENTITY, contact.getIdentity());
			rejectIntent.putExtra(VoipCallService.EXTRA_CALL_ID, callId);
			rejectIntent.putExtra(CallRejectService.EXTRA_REJECT_REASON, reason);
			startService(rejectIntent);
		} else if (this.activityMode == MODE_ACTIVE_CALL) {
			VoipUtil.sendVoipCommand(CallActivity.this, VoipCallService.class, VoipCallService.ACTION_HANGUP);
			setResult(RESULT_CANCELED);
			finish();
		} else {
			stopService(new Intent(this, VoipCallService.class));
			disconnect(RESULT_CANCELED);
		}
	}

	//endregion

	private void abortWithError(@NonNull byte rejectReason) {
		logger.info("abortWithError");
		this.rejectOrCancelCall(rejectReason);
		this.finish();
	}

	private void abortWithError() {
		this.abortWithError(VoipCallAnswerData.RejectReason.UNKNOWN);
	}

	private void adjustWindowOffsets() {
		// Support notch
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content_layout), (v, insets) -> {
				if (!isInPictureInPictureMode) {
					if (insets.getDisplayCutout() != null) {
						logger.debug("apply cutout:"
							+ " left = " + insets.getDisplayCutout().getSafeInsetLeft()
							+ " top = " + insets.getDisplayCutout().getSafeInsetTop()
							+ " right = " + insets.getDisplayCutout().getSafeInsetRight()
							+ " bottom = " + insets.getDisplayCutout().getSafeInsetBottom()
						);

						v.setPadding(
							insets.getDisplayCutout().getSafeInsetLeft(),
							insets.getDisplayCutout().getSafeInsetTop(),
							insets.getDisplayCutout().getSafeInsetRight(),
							insets.getDisplayCutout().getSafeInsetBottom()
						);
					}
				} else {
					// reset notch margins for PIP
					v.setPadding(0, 0, 0, 0);
				}
				return insets;
			});
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		logger.debug("onWindowFocusChanged: " + hasFocus);

		super.onWindowFocusChanged(hasFocus);

		adjustWindowOffsets();

		getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility(getWindow()));

		if (sensorEnabled && sensorService != null) {
			if (hasFocus) {
				sensorService.registerSensors(SENSOR_TAG_CALL, this, false);
			} else {
				sensorService.unregisterSensors(SENSOR_TAG_CALL);
			}
		}
	}

	private static int getSystemUiVisibility(Window window) {
		logger.debug("getSystemUiVisibility");

		int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;

		flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
			View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
			View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

		flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowManager.LayoutParams params = window.getAttributes();
			params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}

		return flags;
	}

	/**
	 * Disconnect from remote resources, dispose of local resources, and exit.
	 */
	@UiThread
	private void disconnect(int result) {
		logger.info("disconnect");
		setResult(result);
		finish();
	}

	@AnyThread
	private void startCallDurationCounter(final long startTime) {
		logger.debug("*** startDuration: " + startTime);
		RuntimeUtil.runOnUiThread(() -> {
			if (this.commonViews != null) {
				this.commonViews.callDuration.setBase(startTime);
				this.commonViews.callDuration.start();
				this.commonViews.callDuration.setVisibility(View.VISIBLE);
				this.commonViews.callStatus.setVisibility(View.GONE);
			}
		});

		// unlock orientation as soon as we're connected
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
	}

	@TargetApi(Build.VERSION_CODES.S)
	@Override
	public void onRequestPermissionsResult(
		@PermissionRequest int requestCode,
		@NonNull String[] permissions,
		@NonNull int[] grantResults
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			// Permission was granted
			final CompletableFuture<PermissionRequestResult> future;
			switch (requestCode) {
				case PERMISSION_REQUEST_RECORD_AUDIO:
					future = this.micPermissionResponse;
					break;
				case PERMISSION_REQUEST_CAMERA:
					future = this.camPermissionResponse;
					break;
				case PERMISSION_REQUEST_BLUETOOTH_CONNECT:
					future = this.bluetoothConnectPermissionResponse;
					break;
				default:
					future = null;
			}
			if (future != null) {
				future.complete(new PermissionRequestResult(true, false));
			}
		} else {
			final String permission;
			final CompletableFuture<PermissionRequestResult> future;
			switch (requestCode) {
				case PERMISSION_REQUEST_RECORD_AUDIO:
					permission = Manifest.permission.RECORD_AUDIO;
					future = this.micPermissionResponse;
					break;
				case PERMISSION_REQUEST_CAMERA:
					permission = Manifest.permission.CAMERA;
					future = this.camPermissionResponse;
					break;
				case PERMISSION_REQUEST_BLUETOOTH_CONNECT:
					permission = Manifest.permission.BLUETOOTH_CONNECT;
					future = this.bluetoothConnectPermissionResponse;
					break;
				default:
					logger.warn("Invalid permission request code: {}", requestCode);
					return;
			}
			if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
				logger.warn("Could not start call, permission {} manually rejected", permission);
				if (future != null) {
					future.complete(new PermissionRequestResult(false, false));
				}
			} else {
				logger.warn("Could not get permission {}, rejected by user", permission);
				if (future != null) {
					future.complete(new PermissionRequestResult(false, false));
				}
			}
		}
	}

	/**
	 * Audio bottom sheet selection
	 * @param tag
	 */
	@Override
	public void onSelected(String tag) {
		logger.debug("*** onSelected");
		if (!TestUtil.empty(tag)) {
			int ordinal = Integer.valueOf(tag);
			final AudioDevice device = AudioDevice.values()[ordinal];
			this.selectAudioDevice(device);
		}
	}

	public void selectAudioDevice(@NonNull AudioDevice device) {
		final Intent intent = new Intent();
		intent.setAction(VoipCallService.ACTION_SET_AUDIO_DEVICE);
		intent.putExtra(VoipCallService.EXTRA_AUDIO_DEVICE, device);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	/**
	 * Override audio device selection, but only if no headphone (wired or bluetooth) is connected.
	 */
	public void setPreferredAudioDevice(@NonNull AudioDevice device) {
		logger.info("setPreferredAudioDevice {}", device);

		if (audioManager.isWiredHeadsetOn()) {
			logger.info("Wired headset is connected, not overriding audio device selection");
			return;
		}

		if (this.audioManager.isBluetoothScoOn()) {
			logger.info("Bluetooth headset is connected, not overriding audio device selection");
			return;
		}

		selectAudioDevice(device);
	}

	@Override
	public void onSensorChanged(String key, boolean value) {
		// called if sensor status changed
		logger.trace("onSensorChanged: {}={}", key, value);
	}

	@Override
	protected boolean isPinLockable() {
		return false;
	}

	@Override
	public void onYes(String tag, Object data) {
	}

	@Override
	public void onNo(String tag, Object data) {
	}

	/**
	 * Set a view as enabled or disabled.
	 * If the view is being disabled, the opacity is set to 50%.
	 */
	private void setEnabled(@NonNull View view, boolean enabled) {
		view.setAlpha(enabled ? 1.0f : 0.5f);
	}

	/**
	 * Check whether a view is enabled or disabled by looking at the opacity.
	 * See {@link #setEnabled(View, boolean)} for more details.
	 */
	private boolean isEnabled(@NonNull View view) {
		return view.getAlpha() != 0.5f;
	}

	//region Video rendering

	/**
	 * Set the video sink targets.
	 *
	 * Only call this method in video mode!
	 */
	private void setVideoSinkTargets(@NonNull VideoContext videoContext) {
		logger.debug("Setting video sink targets with video mode " + voipStateService.getVideoRenderMode());
		if (this.videoViews != null) {
			videoContext.setLocalVideoSinkTarget(this.isSwappedFeeds ? this.videoViews.fullscreenVideoRenderer : this.videoViews.pipVideoRenderer);
			videoContext.setRemoteVideoSinkTarget(this.isSwappedFeeds ? this.videoViews.pipVideoRenderer : this.videoViews.fullscreenVideoRenderer);
		} else {
			logger.error("Error: Video views not yet initialized!");
		}
	}

	/**
	 * Set to "true" in order to swap local and remote video renderer.
	 * isSwappedFeeds == true => outgoing video on big view, incoming on pip
	 * isSwappedFeeds == false => outgoing video on pip, incoming on big view
	 *
	 * Only call this in video mode!
	 */
	private void setSwappedFeeds(boolean isSwappedFeeds) {
		logger.debug("setSwappedFeeds: " + isSwappedFeeds);
		this.isSwappedFeeds = isSwappedFeeds;
		final VideoContext videoContext = this.voipStateService.getVideoContext();
		if (videoContext != null && this.videoViews != null) {
			this.setVideoSinkTargets(videoContext);
			updateVideoViewsMirror();
		} else {
			logger.error("Error: videoContext or video views are null!");
		}
	}

	//endregion

	//region picture in picture mode

	@Override
	protected void onUserLeaveHint() {
		logger.trace("onUserLeaveHint");

		super.onUserLeaveHint();
		enterPictureInPictureMode(false);
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
		this.isInPictureInPictureMode = isInPictureInPictureMode;

		if (isInPictureInPictureMode) {
			// Hide the full-screen UI (controls, etc.) while in
			// picture-in-picture mode.
			hideNavigation(false);
        } else {
			// Restore the full-screen UI.
			getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility(getWindow()));
			unhideNavigation(false);
            logger.debug("unhide Navigation");
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	private PictureInPictureParams createPictureInPictureParams() {
		final VideoContext videoContext = this.voipStateService.getVideoContext();
		final CommonViews common = this.commonViews;

		if (videoContext == null || common == null) {
			return null;
		}

		Rational aspectRatio;
		Rect launchBounds;
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
			aspectRatio = new Rational(videoContext.getFrameWidth(), videoContext.getFrameHeight());
		} else {
			aspectRatio = new Rational(videoContext.getFrameHeight(), videoContext.getFrameWidth());
		}

		launchBounds = new Rect(common.backgroundView.getLeft(),
			common.backgroundView.getTop(),
			common.backgroundView.getRight(),
			common.backgroundView.getBottom());

		PictureInPictureParams.Builder pipParamsBuilder = new PictureInPictureParams.Builder()
			.setAspectRatio(aspectRatio)
			.setSourceRectHint(launchBounds);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			pipParamsBuilder.setAutoEnterEnabled(true);
		}

		return pipParamsBuilder.build();
	}

	private void enterPictureInPictureMode(boolean launchedByUser) {
		if (voipStateService.getVideoRenderMode() == VIDEO_RENDER_FLAG_NONE || this.videoViews == null) {
			return;
		}

		if (!ConfigUtils.supportsPictureInPicture(this)) {
			return;
		}

		AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
		if (appOpsManager != null && appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), getPackageName()) != AppOpsManager.MODE_ALLOWED) {
			if (launchedByUser) {
				SimpleStringAlertDialog.newInstance(R.string.enable_picture_in_picture, getString(R.string.picture_in_picture_disabled_in_setting, getString(R.string.app_name))).show(getSupportFragmentManager(), "pipdis");
			}
			return;
		}

		hideNavigation(false);

		if (this.commonViews != null) {
			try {
				enterPictureInPictureMode();
			} catch (IllegalArgumentException e) {
				logger.error("Unable to enter PIP mode", e);
				unhideNavigation(false);
			}
		}
	}

	synchronized private void toggleNavigation() {
		synchronized (navigationLock) {
			if (this.commonViews != null) {
				if (navigationShown) {
					hideNavigation(true);
				} else {
					unhideNavigation(true);
				}
			}
		}
	}

	private void hideNavigation(boolean animated) {
		synchronized (navigationLock) {
			// hide unnecessary views
			if (this.commonViews != null) {
				ConstraintLayout container = findViewById(R.id.content_layout);
				ConstraintSet constraintSet = new ConstraintSet();
				constraintSet.clone(container);
				constraintSet.clear(R.id.incall_buttons_container, ConstraintSet.BOTTOM);
				constraintSet.clear(R.id.incall_buttons_container, ConstraintSet.TOP);
				constraintSet.connect(R.id.incall_buttons_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
				constraintSet.clear(R.id.caller_container, ConstraintSet.BOTTOM);
				constraintSet.clear(R.id.caller_container, ConstraintSet.TOP);
				constraintSet.connect(R.id.caller_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.TOP);

				if (animated) {
					Transition transition = new ChangeBounds();
					transition.setDuration(300);
					transition.addListener(new Transition.TransitionListener() {
						@Override
						public void onTransitionStart(@NonNull Transition transition) {}

						@Override
						public void onTransitionEnd(@NonNull Transition transition) {
							changeGradientVisibility(View.GONE, animated);
						}

						@Override
						public void onTransitionCancel(@NonNull Transition transition) {}

						@Override
						public void onTransitionPause(@NonNull Transition transition) {}

						@Override
						public void onTransitionResume(@NonNull Transition transition) {}
					});
					TransitionManager.beginDelayedTransition(container, transition);
				}
				else {
					changeGradientVisibility(View.GONE, animated);
				}

				constraintSet.applyTo(container);

				if (toggleVideoTooltip != null && toggleVideoTooltip.isShowing()) {
					toggleVideoTooltip.dismiss(false);
				}

				if (audioSelectorTooltip != null && audioSelectorTooltip.isShowing()) {
					audioSelectorTooltip.dismiss(false);
				}

				navigationShown = false;
			}
		}
	}

	private void unhideNavigation(boolean animated) {
		synchronized (navigationLock) {
			if (this.isInPictureInPictureMode) {
				return;
			}

			// hide unnecessary views
			if (this.commonViews != null) {
				ConstraintLayout container = findViewById(R.id.content_layout);
				ConstraintSet constraintSet = new ConstraintSet();
				constraintSet.clone(container);
				constraintSet.clear(R.id.incall_buttons_container, ConstraintSet.BOTTOM);
				constraintSet.clear(R.id.incall_buttons_container, ConstraintSet.TOP);
				constraintSet.connect(R.id.incall_buttons_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, layoutMargin);
				constraintSet.clear(R.id.caller_container, ConstraintSet.BOTTOM);
				constraintSet.clear(R.id.caller_container, ConstraintSet.TOP);
				constraintSet.connect(R.id.caller_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0);

				if (animated) {
					Transition transition = new ChangeBounds();
					transition.setDuration(300);
					transition.addListener(new Transition.TransitionListener() {
						@Override
						public void onTransitionStart(@NonNull Transition transition) {
						}

						@Override
						public void onTransitionEnd(@NonNull Transition transition) {
							changeGradientVisibility(View.VISIBLE, animated);
						}

						@Override
						public void onTransitionCancel(@NonNull Transition transition) {
						}

						@Override
						public void onTransitionPause(@NonNull Transition transition) {
						}

						@Override
						public void onTransitionResume(@NonNull Transition transition) {
						}
					});
					TransitionManager.beginDelayedTransition(container, transition);
				} else {
					changeGradientVisibility(View.VISIBLE, animated);
				}

				constraintSet.applyTo(container);
				navigationShown = true;
			}
		}
	}

	private void changeGradientVisibility(int visibility, boolean animated) {
		if (this.videoViews != null) {
			float alpha = visibility == View.VISIBLE ? 1.0f : 0f;

			if (animated) {
				this.videoViews.fullscreenVideoRendererGradient.animate().setDuration(200).alpha(alpha);
			} else {
				this.videoViews.fullscreenVideoRendererGradient.setAlpha(alpha);
			}
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ConstraintLayout container = findViewById(R.id.content_layout);

		ConstraintSet constraintSet = new ConstraintSet();
		constraintSet.clone(container);

		ConstraintLayout callerContainer = findViewById(R.id.caller_container);
		ConstraintSet callerContainerSet = new ConstraintSet();
		callerContainerSet.clone(callerContainer);

		int marginTop = getResources().getDimensionPixelSize(R.dimen.caller_container_margin_top);
		int marginLeft = getResources().getDimensionPixelSize(R.dimen.call_activity_margin);

		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			constraintSet.constrainPercentWidth(R.id.pip_video_view, 0f);
			constraintSet.constrainPercentHeight(R.id.pip_video_view, 0.25f);
			constraintSet.setDimensionRatio(R.id.pip_video_view, "W,4:3");

			callerContainerSet.clear(R.id.call_contact_name, ConstraintSet.RIGHT);

			callerContainerSet.clear(R.id.call_status, ConstraintSet.LEFT);
			callerContainerSet.clear(R.id.call_status, ConstraintSet.RIGHT);
			callerContainerSet.clear(R.id.call_status, ConstraintSet.TOP);
			callerContainerSet.clear(R.id.call_status, ConstraintSet.BASELINE);
			callerContainerSet.connect(R.id.call_status, ConstraintSet.RIGHT, R.id.button_call_switch_cam, ConstraintSet.LEFT);
			callerContainerSet.connect(R.id.call_status, ConstraintSet.LEFT, R.id.call_contact_name, ConstraintSet.RIGHT, marginLeft);
			callerContainerSet.connect(R.id.call_status, ConstraintSet.BASELINE, R.id.call_contact_name, ConstraintSet.BASELINE);

			callerContainerSet.clear(R.id.call_duration, ConstraintSet.LEFT);
			callerContainerSet.clear(R.id.call_duration, ConstraintSet.RIGHT);
			callerContainerSet.clear(R.id.call_duration, ConstraintSet.TOP);
			callerContainerSet.clear(R.id.call_duration, ConstraintSet.BASELINE);
			callerContainerSet.connect(R.id.call_duration, ConstraintSet.RIGHT, R.id.button_call_switch_cam, ConstraintSet.LEFT);
			callerContainerSet.connect(R.id.call_duration, ConstraintSet.LEFT, R.id.call_contact_name, ConstraintSet.RIGHT, marginLeft);
			callerContainerSet.connect(R.id.call_duration, ConstraintSet.BASELINE, R.id.call_contact_name, ConstraintSet.BASELINE);
		} else {
			constraintSet.constrainPercentWidth(R.id.pip_video_view, 0.25f);
			constraintSet.constrainPercentHeight(R.id.pip_video_view, 0);
			constraintSet.setDimensionRatio(R.id.pip_video_view, "H,3:4");

			callerContainerSet.clear(R.id.call_contact_name, ConstraintSet.RIGHT);
			callerContainerSet.connect(R.id.call_contact_name, ConstraintSet.RIGHT, R.id.button_call_switch_cam, ConstraintSet.LEFT);

			callerContainerSet.clear(R.id.call_status, ConstraintSet.LEFT);
			callerContainerSet.clear(R.id.call_status, ConstraintSet.RIGHT);
			callerContainerSet.clear(R.id.call_status, ConstraintSet.TOP);
			callerContainerSet.clear(R.id.call_status, ConstraintSet.BASELINE);
			callerContainerSet.connect(R.id.call_status, ConstraintSet.TOP, R.id.call_contact_dots, ConstraintSet.BOTTOM, marginTop);
			callerContainerSet.connect(R.id.call_status, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT);

			callerContainerSet.clear(R.id.call_duration, ConstraintSet.LEFT);
			callerContainerSet.clear(R.id.call_duration, ConstraintSet.RIGHT);
			callerContainerSet.clear(R.id.call_duration, ConstraintSet.TOP);
			callerContainerSet.clear(R.id.call_duration, ConstraintSet.BASELINE);
			callerContainerSet.connect(R.id.call_duration, ConstraintSet.TOP, R.id.call_contact_dots, ConstraintSet.BOTTOM, marginTop);
			callerContainerSet.connect(R.id.call_duration, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT);
		}

		callerContainerSet.applyTo(callerContainer);
		constraintSet.applyTo(container);

		adjustWindowOffsets();
		adjustPipLayout();
	}

	//endregion


	@Override
	public void finish() {
		KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
		if (keyguardManager.inKeyguardRestrictedInputMode()) {
			// finish activity stack in case phone is locked to avoid coming back to a conversation if one was left open by user
			setResult(Activity.RESULT_CANCELED);
			finishAffinity();
		} else {
			super.finish();
		}
	}
}
