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

package ch.threema.app.utils;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.jobs.ReConnectJobService;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.push.PushRegistrationWorker;
import ch.threema.app.receivers.AlarmManagerBroadcastReceiver;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.services.NotificationPreferenceServiceImpl;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.services.notification.NotificationServiceImpl;
import ch.threema.app.services.PollingHelper;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModel;
import ch.threema.protobuf.d2d.sync.MdD2DSync;

public class PushUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("PushUtil");

    public static final String EXTRA_CLEAR_TOKEN = "clear";
    public static final String EXTRA_WITH_CALLBACK = "cb";
    public static final String EXTRA_REGISTRATION_ERROR_BROADCAST = "rer";

    private static final String WEBCLIENT_SESSION = "wcs";
    private static final String WEBCLIENT_TIMESTAMP = "wct";
    private static final String WEBCLIENT_VERSION = "wcv";
    private static final String WEBCLIENT_AFFILIATION_ID = "wca";

    private static final int RECONNECT_JOB = 89;

    /**
     * Send push token to server
     *
     * @param context      Context
     * @param clear        Remove token from sever
     * @param withCallback Send broadcast after token refresh has been completed or failed
     */
    public static void enqueuePushTokenUpdate(Context context, boolean clear, boolean withCallback) {
        Data workerFlags = new Data.Builder()
            .putBoolean(EXTRA_CLEAR_TOKEN, clear)
            .putBoolean(EXTRA_WITH_CALLBACK, withCallback)
            .build();

        Constraints workerConstraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        // worker differs between hms and regular builds, see fcm and hms directory for for overwriting push worker versions
        WorkRequest pushTokenRegistrationRequest = new OneTimeWorkRequest.Builder(PushRegistrationWorker.class)
            .setInputData(workerFlags)
            .setConstraints(workerConstraints)
            .build();

        WorkManager.getInstance(context).enqueue(pushTokenRegistrationRequest);
    }

    /**
     * Send a push token to the server
     *
     * @param token String representing the token
     * @param type  int representing the token type (fcm, hms or none in case of a reset)
     */
    public static void sendTokenToServer(@NonNull String token, int type) throws ThreemaException {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager != null) {
            serviceManager.getTaskCreator().scheduleSendPushTokenTask(token, type);
            logger.info("Sending push token of type 0x{} successfully scheduled", Integer.toHexString(type));
            // Note that the last sent date of the push token is set in the task
        } else {
            throw new ThreemaException("Unable to send / clear push token. ServiceManager not available");
        }
    }

    /**
     * Signal a push token update through a local broadcast
     *
     * @param error      String potential error message
     * @param clearToken boolean whether the token was reset
     */
    public static void signalRegistrationFinished(@Nullable String error, boolean clearToken) {
        final Intent intent = new Intent(AppConstants.INTENT_PUSH_REGISTRATION_COMPLETE);
        if (error != null) {
            logger.error("Failed to get push token {}", error);
            intent.putExtra(PushUtil.EXTRA_REGISTRATION_ERROR_BROADCAST, true);
        } else {
            intent.putExtra(PushUtil.EXTRA_CLEAR_TOKEN, clearToken);
        }
        LocalBroadcastManager.getInstance(ThreemaApplication.getAppContext()).sendBroadcast(intent);
    }

    /**
     * Process the Data mapping received from a FCM message
     *
     * @param data Map<String, String> key value pairs with webclient session infos
     */
    public static void processRemoteMessage(Map<String, String> data) {
        logger.info("processRemoteMessage");

        // Webclient push
        if (data != null && data.containsKey(WEBCLIENT_SESSION) && data.containsKey(WEBCLIENT_TIMESTAMP)) {
            sendWebclientNotification(data);
        } else { // New messages push, trigger a reconnect and show new message notification(s)
            sendNotification();
        }
    }

    private static void sendNotification() {
        logger.info("sendNotification");
        Context appContext = ThreemaApplication.getAppContext();
        PollingHelper pollingHelper = new PollingHelper(appContext, "fcm");

        if (ConfigUtils.isBackgroundDataRestricted(appContext, true)) {
            logger.warn("Network blocked (background data disabled?)");
            // The same message may arrive twice (due to a network change). so we simply ignore messages that we were unable to fetch due to a blocked network
            // Simply schedule a poll when the device is back online
            JobScheduler js = (JobScheduler) appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            js.cancel(RECONNECT_JOB);

            JobInfo job = new JobInfo.Builder(RECONNECT_JOB,
                new ComponentName(appContext, ReConnectJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .build();

            if (js.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                logger.error("Job scheduling failed");
            }
            return;
        }

        //recheck after one minute
        AlarmManagerBroadcastReceiver.requireLoggedInConnection(
            appContext,
            (int) DateUtils.MINUTE_IN_MILLIS
        );

        PreferenceStore preferenceStore = new PreferenceStore(appContext, null);
        NotificationPreferenceService preferenceService = new NotificationPreferenceServiceImpl(appContext, preferenceStore);

        if (ThreemaApplication.getMasterKey().isLocked() &&
            preferenceService.isMasterKeyNewMessageNotifications()) {

            displayAdHocNotification();
        }

        if (!pollingHelper.poll(true)) {
            logger.warn("Unable to establish connection");
        }
    }

    private static void displayAdHocNotification() {
        logger.info("displayAdHocNotification");
        final Context appContext = ThreemaApplication.getAppContext();
        final ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        NotificationService notificationService;
        if (serviceManager != null) {
            notificationService = serviceManager.getNotificationService();
        } else {
            // Because the master key is locked, there is no preference service object.
            // We need to create one for ourselves so that we can read the user's notification prefs
            // (which are unencrypted).
            //create a temporary service class (with some implementations) to use the showMasterKeyLockedNewMessageNotification
            PreferenceStore ps = new PreferenceStore(appContext, ThreemaApplication.getMasterKey());
            NotificationPreferenceService p = new NotificationPreferenceServiceImpl(appContext, ps);

            notificationService = new NotificationServiceImpl(
                appContext,
                new LockAppService() {
                    @Override
                    public boolean isLockingEnabled() {
                        return false;
                    }

                    @Override
                    public boolean unlock(String pin) {
                        return false;
                    }

                    @Override
                    public void lock() {
                        //not needed in this context
                    }

                    @Override
                    public boolean checkLock() {
                        return false;
                    }

                    @Override
                    public boolean isLocked() {
                        return false;
                    }

                    @Override
                    public LockAppService resetLockTimer(boolean restartAfterReset) {
                        return null;
                    }

                    @Override
                    public void addOnLockAppStateChanged(OnLockAppStateChanged c) {
                        //not needed in this context
                    }

                    @Override
                    public void removeOnLockAppStateChanged(OnLockAppStateChanged c) {
                        //not needed in this context
                    }
                },
                new ConversationCategoryService() {
                    @Override
                    public void markContactChatAsPrivate(@NonNull ContactModel contactModel) {
                        // Nothing to do
                    }

                    @Override
                    public void removePrivateMarkFromContactChat(@NonNull ContactModel contactModel) {
                        // Nothing to do
                    }

                    @Override
                    public void removePrivateMarkFromContactChat(@NonNull ch.threema.storage.models.ContactModel contactModel) {
                        // Nothing to do
                    }

                    @Override
                    public void markGroupChatAsPrivate(long groupDatabaseId) {
                        // Nothing to do
                    }

                    @Override
                    public void removePrivateMarkFromGroupChat(long groupDatabaseId) {
                        // Nothing to do
                    }

                    @Override
                    public boolean isPrivateGroupChat(long groupDatabaseId) {
                        return false;
                    }

                    @Override
                    public boolean isPrivateChat(@NonNull String uniqueIdString) {
                        return false;
                    }

                    @NonNull
                    @Override
                    public MdD2DSync.ConversationCategory getConversationCategory(@NonNull String uniqueIdString) {
                        return MdD2DSync.ConversationCategory.DEFAULT;
                    }

                    @Override
                    public boolean markAsPrivate(@NonNull MessageReceiver<?> messageReceiver) {
                        return false;
                    }

                    @Override
                    public boolean removePrivateMark(@NonNull MessageReceiver<?> messageReceiver) {
                        return false;
                    }

                    @Override
                    public void persistPrivateChat(@NonNull String uniqueIdString) {
                        // Nothing to do
                    }

                    @Override
                    public void persistDefaultChat(@NonNull String uniqueIdString) {
                        // Nothing to do
                    }

                    @Override
                    public boolean hasPrivateChats() {
                        return false;
                    }

                    @Override
                    public void invalidateCache() {
                        // Nothing to do
                    }
                },
                p,
                new RingtoneService() {
                    @Override
                    public void init() {
                        //not needed in this context
                    }

                    @Override
                    public void setRingtone(String uniqueId, Uri ringtoneUri) {
                        //not needed in this context
                    }

                    @Override
                    public Uri getRingtoneFromUniqueId(String uniqueId) {
                        return null;
                    }

                    @Override
                    public boolean hasCustomRingtone(String uniqueId) {
                        return false;
                    }

                    @Override
                    public void removeCustomRingtone(String uniqueId) {
                        //not needed in this context
                    }

                    @Override
                    public void resetRingtones(Context context) {
                        //not needed in this context
                    }

                    @Override
                    public Uri getContactRingtone(String uniqueId) {
                        return null;
                    }

                    @Override
                    public Uri getGroupRingtone(String uniqueId) {
                        return null;
                    }

                    @Override
                    public Uri getDefaultContactRingtone() {
                        return null;
                    }

                    @Override
                    public Uri getDefaultGroupRingtone() {
                        return null;
                    }

                    @Override
                    public boolean isSilent(String uniqueId, boolean isGroup) {
                        return false;
                    }
                });
        }

        notificationService.showMasterKeyLockedNewMessageNotification();
    }

    private static void sendWebclientNotification(Map<String, String> data) {
        final String session = data.get(WEBCLIENT_SESSION);
        final String timestamp = data.get(WEBCLIENT_TIMESTAMP);
        final String version = data.get(WEBCLIENT_VERSION);
        final String affiliationId = data.get(WEBCLIENT_AFFILIATION_ID);
        if (session != null && !session.isEmpty() && timestamp != null && !timestamp.isEmpty()) {
            logger.debug("Received webclient wakeup for session {}", session);

            final Thread t = new Thread(() -> {
                logger.info("Trying to wake up webclient session {}", session);

                // Parse version number
                Integer versionNumber = null;
                if (version != null) { // Can be null during beta, if an old client doesn't yet send the version field
                    try {
                        versionNumber = Integer.parseInt(version, 10);
                    } catch (NumberFormatException e) {
                        logger.error("Could not parse webclient protocol version number: ", e);
                        return;
                    }
                }

                // Try to wake up session
                SessionWakeUpServiceImpl.getInstance()
                    .resume(session, versionNumber == null ? 0 : versionNumber, affiliationId);
            });
            t.setName("webclient-wakeup");
            t.start();
        }
    }

    /**
     * Clear the "token last updated" setting in shared preferences
     *
     * @param context Context
     */
    public static void clearPushTokenSentDate(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences != null) {
            sharedPreferences
                .edit()
                .putLong(context.getString(R.string.preferences__token_sent_date), 0L)
                .apply();
        }
    }

    /**
     * Check if the token needs to be uploaded to the server i.e. no more than once a day.
     *
     * @param context Context
     * @return true if more than a day has passed since the token has been last sent to the server, false otherwise
     */
    public static boolean pushTokenNeedsRefresh(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (sharedPreferences != null) {
            long lastDate = sharedPreferences.getLong(context.getString(R.string.preferences__token_sent_date), 0L);
            // refresh push token at least once a day
            return (System.currentTimeMillis() - lastDate) > DateUtils.DAY_IN_MILLIS;
        }
        return true;
    }

    /**
     * Check if push services are enabled and polling is not used.
     *
     * @param context Context
     * @return true if polling is disabled or shared preferences are not available, false otherwise
     */
    public static boolean isPushEnabled(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (sharedPreferences != null) {
            return !sharedPreferences.getBoolean(context.getString(R.string.preferences__threema_push_switch), false);
        }
        return true;
    }
}
