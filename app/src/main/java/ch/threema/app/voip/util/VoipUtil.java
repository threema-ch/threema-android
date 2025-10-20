/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.slf4j.Logger;

import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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
    private static final String DIALOG_TAG_PSTN_OUTGOING = "pstn_outgoing";

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
        if (!TestUtil.isEmptyOrNull(extraName)) {
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
     * @param activity         The activity that triggered this call.
     * @param contactModel     The contact to call
     * @param onFinishRunnable
     * @return true if the call could be initiated, false otherwise
     */
    public static boolean initiateCall(
        @NonNull final AppCompatActivity activity,
        @NonNull final ContactModel contactModel,
        boolean launchVideo,
        @Nullable Runnable onFinishRunnable
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

        if (serviceManager.getBlockedIdentitiesService().isBlocked(contactModel.getIdentity())) {
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
                        UpdateFeatureLevelRoutine.removeTimeCache(contactModel.getIdentity());

                        new UpdateFeatureLevelRoutine(
                            serviceManager.getModelRepositories().getContacts(),
                            serviceManager.getUserService(),
                            serviceManager.getAPIConnector(),
                            Collections.singletonList(contactModel.getIdentity())
                        ).run();
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
        if (!ThreemaFeature.canVoip(contactModel.getFeatureMask()) && !TestUtil.isInDeviceTest()) {
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
            activity.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_close_exit);
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
     * <p>
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
