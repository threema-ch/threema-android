package ch.threema.app.services.notification;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.base.SessionScoped;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.ServerMessageModel;

@SessionScoped
public interface NotificationService {

    interface FetchCacheUri {
        @Nullable
        Uri fetch();
    }

    @TargetApi(Build.VERSION_CODES.O)
    void recreateNotificationChannels();

    /**
     * Set the identity for which a conversation is currently visible (there can only be
     * one at any given time). No notifications will created for messages from this identity.
     *
     * @param receiver visible conversation identity or group (or null)
     */
    void setVisibleReceiver(MessageReceiver receiver);

    void addGroupCallNotification(@NonNull GroupModelOld group, @NonNull ContactModelData contactModelData);

    void cancelGroupCallNotification(int groupId);

    /**
     * add a new conversation notification
     */
    void showConversationNotification(ConversationNotification conversationNotification, boolean updateExisting);

    /**
     * cancel a conversation notification
     */
    void cancelConversationNotification(String... uids);

    /**
     * cancel all conversation notifications saved in notificationsService.conversationNotifications synchronously
     * called when pin lock is called
     */
    void cancelCachedConversationNotifications();

    /**
     * cancel all conversation notifications saved in notificationsService.conversationNotifications
     */
    void cancelAllCachedConversationNotifications();

    /**
     * cancel all conversation notifications of category Notification.CATEGORY_MESSAGE
     * returns true if any have been cancelled
     */
    boolean cancelAllMessageCategoryNotifications();

    /**
     * handle conversation cancellation upon pin lock depending on sdk version and saved notifications
     */
    void cancelConversationNotificationsOnLockApp();

    /**
     * helper that returns true if there are currently held conversations notifications.
     * called when pin lock is called on SDK < 23 to check if we should show a pinLockedNotification
     */
    boolean isConversationNotificationVisible();

    void cancel(ConversationModel conversationModel);

    void cancel(@Nullable MessageReceiver receiver);

    void cancel(int notificationId);

    void cancel(@NonNull String identity);

    void showMasterKeyLockedNewMessageNotification();

    void showPinLockedNewMessageNotification(NotificationSchema notificationSchema, String uid, String channelId);

    void showServerMessage(ServerMessageModel m);

    /**
     * Show a notification that a message could not be sent. Note that this is should not be used
     * for messages that were rejected because of forward security.
     *
     * @param failedMessages the failed message models
     */
    void showUnsentMessageNotification(@NonNull List<AbstractMessageModel> failedMessages);

    /**
     * Show a forward security message rejected notification for the given receiver. Note that for
     * every receiver only one notification is shown. If a notification is already shown, this call
     * has no effect. The notification remains visible until the user cancels (or clicks) it.
     */
    void showForwardSecurityMessageRejectedNotification(
        @NonNull MessageReceiver<?> messageReceiver
    );

    /**
     * Shows a notification that the safe backup has failed for the provided number of days. Note
     * that this method should only be called if safe backups are enabled and the number of days
     * that the backup has failed is at least one. Otherwise the notification may not make sense.
     *
     * @param numDays the number of days where a safe backup failed
     */
    void showSafeBackupFailed(int numDays);

    void cancelSafeBackupFailed();

    void cancelWorkSyncProgress();

    void showNewSyncedContactsNotification(List<ContactModel> contactModels);

    void showWebclientResumeFailed(String msg);

    void cancelRestartNotification();

    void cancelRestoreNotification();
}
