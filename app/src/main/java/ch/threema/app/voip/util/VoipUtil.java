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

package ch.threema.app.voip.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import org.slf4j.Logger;

import java.util.Collections;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.UpdateFeatureLevelRoutine;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.logging.ThreemaLogger;
import ch.threema.storage.models.ContactModel;


public class VoipUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VoipUtil");

	private static final String DIALOG_TAG_FETCHING_FEATURE_MASK = "fetchingFeatureMask";

	/**
	 * Send a VoIP broadcast without any intent extras
	 */
	public static void sendVoipBroadcast(Context context, String action) {
		sendVoipBroadcast(context, action, null, null);
	}

	/**
	 * Send a VoIP broadcast with a single intent extra
	 */
	public static void sendVoipBroadcast(
		Context context,
		String action,
		String extraName,
		String extra
	) {
		Intent intent = new Intent();
		intent.setAction(action);
		if (!TestUtil.empty(extraName)) {
			intent.putExtra(extraName, extra);
		}
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	}

	/**
	 * Send a command to a service
	 */
	public static void sendVoipCommand(Context context, Class service, String action) {
		final Intent intent = new Intent(context, service);
		intent.setAction(action);
		context.startService(intent);
	}

	// TODO(ANDR-1953): It looks like all usages of sendVoipCommand(Context, Class, String)
	//  actually send a hangup command. So they could be refactored to call this method instead.
	public static void sendOneToOneCallHangupCommand(@NonNull Context context) {
		sendVoipCommand(context, VoipCallService.class, VoipCallService.ACTION_HANGUP);
	}


	/**
	 * Start a call. If necessary, fetch the feature mask of the specified contact.
	 *
	 * @param activity         The activity context
	 * @param contactModel     The contact model of the peer
	 * @param launchVideo      If {@code true}, video is launched
	 * @param onFinishRunnable The runnable that is executed after the feature mask has been fetched
	 * @param resultLauncher   The result launcher to request the READ_PHONE_STATE permission
	 * @return {@code true} if the call could have been started
	 */
	public static boolean initiateCall(
		@NonNull final AppCompatActivity activity,
		@NonNull final ContactModel contactModel,
		boolean launchVideo,
		@Nullable Runnable onFinishRunnable,
		@Nullable ActivityResultLauncher<String> resultLauncher
	) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		boolean requestPermission = prefs.getBoolean(activity.getString(R.string.preferences__enable_read_phone_state_permission_request), true);
		return initiateCall(activity, contactModel, launchVideo, onFinishRunnable, resultLauncher, requestPermission);
	}

	/**
	 * Start a call. If necessary, fetch the feature mask of the specified contact.
	 *
	 * @param activity          The activity that triggered this call.
	 * @param contactModel      The contact to call
	 * @param onFinishRunnable
	 * @param resultLauncher    The result launcher to request the READ_PHONE_STATE permission
	 * @param requestPermission If true, a permission request is shown if the permission is not granted
	 * @return true if the call could be initiated, false otherwise
	 */
	private static boolean initiateCall(
		@NonNull final AppCompatActivity activity,
	    @NonNull final ContactModel contactModel,
		boolean launchVideo,
	    @Nullable Runnable onFinishRunnable,
		@Nullable ActivityResultLauncher<String> resultLauncher,
		boolean requestPermission
	) {

		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			return false;
		}

		try {
			if (!ConfigUtils.isCallsEnabled()) {
				return false;
			}
		} catch (Exception e) {
			logger.error("Exception", e);
			return false;
		}

		if (serviceManager.getBlackListService().has(contactModel.getIdentity())) {
			Toast.makeText(activity, R.string.blocked_cannot_send, Toast.LENGTH_LONG).show();
			return false;
		}

		// Check for internet connection
		if (!serviceManager.getDeviceService().isOnline()) {
			SimpleStringAlertDialog.newInstance(R.string.internet_connection_required, R.string.connection_error).show(activity.getSupportFragmentManager(), "err");
			return false;
		}

		VoipStateService voipStateService = null;
		try {
			voipStateService = serviceManager.getVoipStateService();
		} catch (ThreemaException e) {
			return false;
		}

		if (!voipStateService.getCallState().isIdle()) {
			SimpleStringAlertDialog.newInstance(R.string.threema_call, R.string.voip_another_call).show(activity.getSupportFragmentManager(), "err");
			return false;
		}

		if (requestPermission) {
			// Ask for READ_PHONE_STATE permission (because the user did not choose to not being asked again)
			try {
				// If permission not granted: Displays a dialog asking for the permission and throws a SecurityException
				if (isPSTNCallOngoingRequestPermission(activity, () -> initiateCall(activity, contactModel, launchVideo, onFinishRunnable, resultLauncher, false), resultLauncher)) {
					// A PSTN call is ongoing
					SimpleStringAlertDialog.newInstance(R.string.threema_call, R.string.voip_another_pstn_call).show(activity.getSupportFragmentManager(), "err");
					return false;
				}
			} catch (SecurityException exception) {
				logger.info("Permission not granted: ", exception);
				return false;
			}
		} else {
			// Don't ask for permission (user doesn't want to)
			if (isPSTNCallOngoing(activity)) {
				SimpleStringAlertDialog.newInstance(R.string.threema_call, R.string.voip_another_pstn_call).show(activity.getSupportFragmentManager(), "err");
				return false;
			}
		}

		if (!ThreemaFeature.canVoip(contactModel.getFeatureMask()) || (ConfigUtils.isVideoCallsEnabled() && !ThreemaFeature.canVideocall(contactModel.getFeatureMask()))) {
			// 1.a Try to fetch the feature mask

			// Start fetching routine in a separate thread
			new AsyncTask<Void, Void, Exception>() {
				@Override
				protected void onPreExecute() {
					// Show a loading
					GenericProgressDialog.newInstance(R.string.please_wait, R.string.voip_checking_compatibility)
							.show(activity.getSupportFragmentManager(), DIALOG_TAG_FETCHING_FEATURE_MASK);
				}

				@Override
				protected Exception doInBackground(Void... params) {
					try {
						// Reset the cache (only for Beta?)
						UpdateFeatureLevelRoutine.removeTimeCache(contactModel);

						(new UpdateFeatureLevelRoutine
								(
										serviceManager.getContactService(),
										// Bad code
										serviceManager.getAPIConnector(),
										Collections.singletonList(contactModel)
								)).run();
					} catch (Exception e) {
						return e;
					}
					return null;
				}

				@Override
				protected void onPostExecute(Exception exception) {
					if (!activity.isDestroyed()) {
						DialogUtil.dismissDialog(activity.getSupportFragmentManager(), DIALOG_TAG_FETCHING_FEATURE_MASK, true);
						phoneAction(activity, activity.getSupportFragmentManager(), contactModel, onFinishRunnable != null, launchVideo);
						if (onFinishRunnable != null) {
							onFinishRunnable.run();
						}
					}
				}
			}.execute();
		} else {
			phoneAction(activity, activity.getSupportFragmentManager(), contactModel, onFinishRunnable != null, launchVideo);
		}

		return true;
	}

	/**
	 * Start the call activity, but do not fetch the feature mask.
	 */
	private static void phoneAction(
		final AppCompatActivity activity,
		final FragmentManager fragmentManager,
		final ContactModel contactModel,
		boolean useToast,
		boolean launchVideo
	) {
		if (!ThreemaFeature.canVoip(contactModel.getFeatureMask()) && !RuntimeUtil.isInTest()) {
			if (useToast) {
				Toast.makeText(ThreemaApplication.getAppContext(), R.string.voip_incompatible, Toast.LENGTH_LONG).show();
			} else {
				SimpleStringAlertDialog.newInstance(R.string.threema_call, R.string.voip_incompatible).show(fragmentManager, "tc");
			}
		} else {
			final Intent callActivityIntent = new Intent(activity, CallActivity.class);
			callActivityIntent.putExtra(VoipCallService.EXTRA_ACTIVITY_MODE, CallActivity.MODE_OUTGOING_CALL);
			callActivityIntent.putExtra(VoipCallService.EXTRA_CONTACT_IDENTITY, contactModel.getIdentity());
			callActivityIntent.putExtra(VoipCallService.EXTRA_IS_INITIATOR, true);
			callActivityIntent.putExtra(VoipCallService.EXTRA_LAUNCH_VIDEO, launchVideo);
			callActivityIntent.putExtra(VoipCallService.EXTRA_CALL_ID, -1L);
			activity.startActivity(callActivityIntent);
			activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
		}
	}

	/**
	 * Check if a PSTN call is ongoing. If a permission is needed that is not given, a dialog is shown
	 * to request the permission if the user did not deny this (previously clicked "don't show again"
	 * option of the dialog). If this dialog is shown, this method throws an exception as the call
	 * initialization will be triggered by the dialog with the initiateCallRunnable argument.
	 *
	 * @param activity the activity
	 * @param initiateCallRunnable the runnable is only executed when a dialog should be shown (and the permission is not given to check for calls)
	 * @param resultLauncher the result launcher is only used, when the user wants to grant the permission
	 * @throws SecurityException if the permission is not given adn a dialog is shown
	 * @return {@code true} if a call is ongoing
	 */
	public static boolean isPSTNCallOngoingRespectPreference(@NonNull Activity activity, @Nullable Runnable initiateCallRunnable, @Nullable ActivityResultLauncher<String> resultLauncher) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		if (prefs.getBoolean(activity.getString(R.string.preferences__enable_read_phone_state_permission_request), true)) {
			// Ask for permission if the user did not forbid that
			return isPSTNCallOngoingRequestPermission(activity, initiateCallRunnable, resultLauncher);
		} else {
			// User doesn't care about the permission. Try to check for PSTN call, otherwise return false.
			return isPSTNCallOngoing(activity);
		}
	}

	/**
	 * Check if a PSTN call is ongoing. If a permission is needed that is not given, a dialog is
	 * shown to ask for the permission. If the permission is not given, a dialog requesting the
	 * permission is shown. The call initialization is handled in this case with the initiateCallRunnable
	 * and resultLauncher if not null.
	 *
	 * @param activity the activity
	 * @param initiateCallRunnable the runnable that initiates a call without requesting the permission
	 * @throws SecurityException if the permission is not given and a dialog is shown
	 * @return {@code true} if a call is ongoing, {@code false} otherwise or when the state cannot be accessed
	 */
	private static boolean isPSTNCallOngoingRequestPermission(@NonNull Activity activity, @Nullable Runnable initiateCallRunnable, @Nullable ActivityResultLauncher<String> resultLauncher) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
				return isPSTNCallOngoingAndroidS(activity);
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
				builder.setTitle(R.string.read_phone_state_dialog_title);
				builder.setMessage(R.string.read_phone_state_dialog_message);
				builder.setPositiveButton(R.string.read_phone_state_dialog_allow, (dialog, which) -> ConfigUtils.requestReadPhonePermission(activity, resultLauncher));
				builder.setNegativeButton(R.string.read_phone_state_dialog_disallow, (dialog, which) -> {
					// User does not want to grant the permission now, so just make the call independent of the permission
					if (initiateCallRunnable != null) {
						initiateCallRunnable.run();
					}
				});
				builder.setNeutralButton(R.string.read_phone_state_dialog_never_ask_again, (dialog, which) -> {
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
					prefs.edit().putBoolean(activity.getString(R.string.preferences__enable_read_phone_state_permission_request), false).apply();
					// User does not want to grant the permission, so just make the call independent of the permission
					if (initiateCallRunnable != null) {
						initiateCallRunnable.run();
					}
				});
				builder.show();
				throw new SecurityException("Permission READ_PHONE_STATE required to get current phone state");
			}
		} else {
			return isPSTNCallOngoingPreS(activity);
		}
	}

	/**
	 * Check for ongoing PSTN call. On API level >= S a permission is required to check the call state.
	 *
	 * @param context the context
	 * @return {@code true} if there is a call ongoing, {@code false} otherwise or when the state could not be checked
	 */
	public static boolean isPSTNCallOngoing(@NonNull Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return isPSTNCallOngoingAndroidS(context);
		} else {
			return isPSTNCallOngoingPreS(context);
		}
	}

	/**
	 * Check for ongoing PSTN call on API level >= S. If the call state cannot be accessed, {@code false}
	 * is returned.
	 *
	 * @param context the context
	 * @return {@code true} if a call is ongoing, {@code false} otherwise
	 */
	@RequiresApi(api = Build.VERSION_CODES.S)
	private static boolean isPSTNCallOngoingAndroidS(@NonNull Context context) {
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
			TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
			return telecomManager != null && telecomManager.isInCall();
		}
		return false;
	}

	/**
	 * Check for ongoing PSTN call on API level < S. If the call state cannot be accessed, {@code false}
	 * is returned.
	 *
	 * @param context the context
	 * @return {@code true} if a call is ongoing, {@code false} otherwise
	 */
	private static boolean isPSTNCallOngoingPreS(@NonNull Context context) {
		try {
			TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			return (telephonyManager != null && telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE);
		} catch (SecurityException exception) {
			logger.error("Couldn't get PSTN call state", exception);
			return false;
		}
	}

	/**
	 * If the logger is a {@link ch.threema.logging.ThreemaLogger}, set the appropriate
	 * call ID logging prefix.
	 *
	 * If the logger is null, or if it's not a {@link ch.threema.logging.ThreemaLogger}, do nothing.
	 */
	public static void setLoggerPrefix(@Nullable Logger logger, long callId) {
		if (logger == null) {
			return;
		}
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix("[cid=" + callId + "]");
		}
	}
}
