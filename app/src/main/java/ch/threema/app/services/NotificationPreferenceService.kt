package ch.threema.app.services

import android.net.Uri

interface NotificationPreferenceService {
    fun isMasterKeyNewMessageNotifications(): Boolean

    fun setWizardRunning(running: Boolean)

    fun getWizardRunning(): Boolean

    /**
     * The notification sound used for (default) notifications.
     * Only used on devices that don't support notification channels, i.e., Android 7
     * [Uri.EMPTY] is used to represent 'silent'.
     */
    fun getLegacyNotificationSound(): Uri?

    fun setLegacyNotificationSound(uri: Uri?)

    /**
     * The notification sound used for group notifications.
     * Only used on devices that don't support notification channels, i.e., Android 7
     * [Uri.EMPTY] is used to represent 'silent'.
     */
    fun getLegacyGroupNotificationSound(): Uri?

    fun setLegacyGroupNotificationSound(uri: Uri?)

    /**
     * The ringtone used for 1:1 calls. Only used on devices that don't support notification channels, i.e., Android 7
     * [Uri.EMPTY] is used to represent 'silent'.
     */
    fun getLegacyVoipCallRingtone(): Uri?

    fun setLegacyVoipCallRingtone(uri: Uri?)

    /**
     * The ringtone used for group calls. Only used on devices that don't support notification channels, i.e., Android 7
     * [Uri.EMPTY] is used to represent 'silent'.
     */
    fun getLegacyGroupCallRingtone(): Uri?

    /**
     * Whether to vibrate for (default) notifications. Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun isLegacyNotificationVibrate(): Boolean

    /**
     * Whether to vibrate for group notifications. Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun isLegacyGroupVibrate(): Boolean

    /**
     * Whether to vibrate for incoming 1:1 calls. Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun isLegacyVoipCallVibrate(): Boolean

    /**
     * Whether to vibrate for incoming group calls. Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun isLegacyGroupCallVibrate(): Boolean

    /**
     * Whether to use the notification light for (default) notifications.
     * Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun isLegacyNotificationLightEnabled(): Boolean

    /**
     * Whether to use the notification light for group notifications.
     * Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun isLegacyGroupNotificationLightEnabled(): Boolean

    /**
     * The priority for notifications. Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun getLegacyNotificationPriority(): Int

    fun setLegacyNotificationPriority(value: Int)

    /**
     * The mapping of custom per-conversation notification sounds.
     * Only used on devices that don't support notification channels, i.e., Android 7
     */
    fun getLegacyRingtones(): Map<String, String?>

    fun setLegacyRingtones(ringtones: Map<String, String?>)

    fun isShowMessagePreview(): Boolean

    fun getDisableSmartReplies(): Boolean
}
