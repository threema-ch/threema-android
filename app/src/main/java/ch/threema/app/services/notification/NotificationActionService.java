/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.app.services.notification;

import android.app.IntentService;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.core.app.RemoteInput;
import ch.threema.app.AppConstants;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiUtil;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.ConversationNotificationUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.AbstractMessageModel;

public class NotificationActionService extends IntentService {

    private static final Logger logger = getThreemaLogger("NotificationActionService");

    private static final String TAG = "notificationAction";
    public static final String ACTION_REPLY = BuildConfig.APPLICATION_ID + ".REPLY";
    public static final String ACTION_MARK_AS_READ = BuildConfig.APPLICATION_ID + ".MARK_AS_READ";
    public static final String ACTION_ACK = BuildConfig.APPLICATION_ID + ".ACK";
    public static final String ACTION_DEC = BuildConfig.APPLICATION_ID + ".DEC";

    private static final int NOTIFICATION_ACTION_CONNECTION_LINGER = 1000 * 5;

    private MessageService messageService;
    private LifetimeService lifetimeService;
    private NotificationService notificationService;

    public NotificationActionService() {
        super(TAG);

        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            try {
                this.messageService = serviceManager.getMessageService();
                this.lifetimeService = serviceManager.getLifetimeService();
                this.notificationService = serviceManager.getNotificationService();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                MessageReceiver<?> messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(this, intent);
                if (messageReceiver != null) {
                    AbstractMessageModel messageModel = IntentDataUtil.getMessageModelFromReceiver(intent, messageReceiver);

                    switch (action) {
                        case ACTION_REPLY:
                            if (reply(messageReceiver, intent)) {
                                return;
                            }
                            break;
                        case ACTION_MARK_AS_READ:
                            markAsRead(messageReceiver);
                            return;
                        case ACTION_ACK:
                            if (messageModel != null) {
                                ack(messageReceiver, messageModel);
                                return;
                            }
                            break;
                        case ACTION_DEC:
                            if (messageModel != null) {
                                dec(messageReceiver, messageModel);
                                return;
                            }
                            break;
                        default:
                            logger.info("Unknown action {}", action);
                    }
                }
            }
        }
        showToast(R.string.verify_failed);
        logger.info("Failed to handle notification action");
    }

    @WorkerThread
    private void ack(@NonNull MessageReceiver<?> messageReceiver, @NonNull AbstractMessageModel messageModel) {
        lifetimeService.acquireConnection(TAG);
        try {
            messageService.markConversationAsRead(messageReceiver, notificationService);
            messageService.sendEmojiReaction(messageModel, EmojiUtil.THUMBS_UP_SEQUENCE, messageReceiver, false);
            notificationService.cancelConversationNotification(ConversationNotificationUtil.getUid(messageModel));
            showToast(R.string.message_acknowledged);
        } catch (Exception e) {
            logger.error("Failed to send emoji reaction", e);
        }
        lifetimeService.releaseConnectionLinger(TAG, NOTIFICATION_ACTION_CONNECTION_LINGER);
    }

    @WorkerThread
    private void dec(@NonNull MessageReceiver<?> messageReceiver, @NonNull AbstractMessageModel messageModel) {
        lifetimeService.acquireConnection(TAG);
        try {
            messageService.markConversationAsRead(messageReceiver, notificationService);
            messageService.sendEmojiReaction(messageModel, EmojiUtil.THUMBS_DOWN_SEQUENCE, messageReceiver, false);
            notificationService.cancelConversationNotification(ConversationNotificationUtil.getUid(messageModel));
            showToast(R.string.message_declined);
        } catch (Exception e) {
            logger.error("Failed to send emoji reaction", e);
        }
        lifetimeService.releaseConnectionLinger(TAG, NOTIFICATION_ACTION_CONNECTION_LINGER);
    }

    private boolean reply(@NonNull MessageReceiver<?> messageReceiver, @NonNull Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results != null) {
            String message = null;
            CharSequence messageCs = results.getCharSequence(AppConstants.EXTRA_VOICE_REPLY);
            if (messageCs != null) {
                message = messageCs.toString();
            }

            if (!TestUtil.isEmptyOrNull(message)) {
                lifetimeService.acquireConnection(TAG);

                try {
                    messageService.sendText(message, messageReceiver);
                    messageService.markConversationAsRead(messageReceiver, notificationService);
                    notificationService.cancel(messageReceiver);

                    showToast(R.string.message_sent);
                    return true;
                } catch (Exception e) {
                    logger.error("Failed to send message", e);
                }
                lifetimeService.releaseConnectionLinger(TAG, NOTIFICATION_ACTION_CONNECTION_LINGER);
            }
        }
        logger.info("Reply message is empty");
        return false;
    }

    private void markAsRead(@NonNull MessageReceiver<?> messageReceiver) {
        lifetimeService.acquireConnection(TAG);
        messageService.markConversationAsRead(messageReceiver, notificationService);
        lifetimeService.releaseConnectionLinger(TAG, NOTIFICATION_ACTION_CONNECTION_LINGER);
        notificationService.cancel(messageReceiver);
    }

    private void showToast(final @StringRes int stringRes) {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR) {
            logger.info("Toast suppressed due to car connection: {}", getString(stringRes));
        } else {
            RuntimeUtil.runOnUiThread(() -> Toast.makeText(NotificationActionService.this, stringRes, Toast.LENGTH_LONG).show());
        }
    }
}
