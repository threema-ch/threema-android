package ch.threema.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.usecases.contacts.GetPersonUseCase
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.data.models.ContactModelData
import ch.threema.domain.types.IdentityString

private val logger = getThreemaLogger("CallNotificationManagerImpl")

class CallNotificationManagerImpl(
    private val appContext: Context,
    private val notificationPreferenceService: NotificationPreferenceService,
    private val preferenceService: PreferenceService,
    private val contactService: ContactService,
    private val getPersonUseCase: GetPersonUseCase,
    private val notificationManager: NotificationManagerCompat,
) : CallNotificationManager {

    override fun showIncomingCallNotification(
        contactModelData: ContactModelData,
        acceptIntent: PendingIntent,
        inCallPendingIntent: PendingIntent,
        rejectIntent: PendingIntent,
    ): CallNotificationManager.IncomingCallNotificationResult {
        if (!areIncomingCallNotificationsEnabled()) {
            return CallNotificationManager.IncomingCallNotificationResult.IncomingCallNotificationsDisabled
        }

        val contactName = contactModelData.getDisplayName(
            contactNameFormat = preferenceService.getContactNameFormat(),
            nicknameHasPrefix = true,
        )

        // The private notification serves as the main notification and is additionally packed with the public notification. The private notification
        // contains sensitive data that may not be shown on the lock screen.
        val notification = getPrivateNotification(
            callerIdentity = contactModelData.identity,
            contactName = contactName,
            acceptIntent = acceptIntent,
            inCallPendingIntent = inCallPendingIntent,
            rejectIntent = rejectIntent,
            person = getPersonUseCase.call(
                identity = contactModelData.identity,
                androidContactLookupInfo = contactModelData.androidContactLookupInfo,
                name = contactName,
            ),
            publicNotification = getPublicNotification(),
        )

        // Show the notification
        notificationManager.notify(contactModelData.identity, NotificationIDs.INCOMING_CALL_NOTIFICATION_ID, notification)

        return CallNotificationManager.IncomingCallNotificationResult.Success
    }

    private fun getPrivateNotification(
        callerIdentity: IdentityString,
        contactName: String,
        acceptIntent: PendingIntent,
        inCallPendingIntent: PendingIntent,
        rejectIntent: PendingIntent,
        person: Person,
        publicNotification: Notification,
    ): Notification = with(NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS)) {
        setContentTitle(appContext.getString(R.string.voip_notification_title))
        setContentText(appContext.getString(R.string.voip_notification_text, contactName))
        setOngoing(true)
        setWhen(now().time)
        setAutoCancel(false)
        setShowWhen(true)
        setGroup(NotificationGroups.CALLS)
        setGroupSummary(false)

        if (!ConfigUtils.supportsNotificationChannels()) {
            // If notification channels are not supported, we fall back to explicitly setting sound and vibration on the notification.
            // On devices that do support notification channels, this would have no effect, so we can skip it there.
            setSound(notificationPreferenceService.getLegacyVoipCallRingtone(), AudioManager.STREAM_RING)
            if (notificationPreferenceService.isLegacyVoipCallVibrate()) {
                setVibrate(NotificationChannels.VIBRATE_PATTERN_INCOMING_CALL)
            }
        }

        // We want a full screen notification
        // Set up the main intent to send the user to the incoming call screen
        setFullScreenIntent(inCallPendingIntent, true)
        setContentIntent(inCallPendingIntent)

        // Icons
        setLargeIcon(contactService.getAvatar(callerIdentity, false))
        setSmallIcon(R.drawable.ic_phone_locked_white_24dp)

        // Alerting
        setPriority(NotificationCompat.PRIORITY_MAX)
        setCategory(NotificationCompat.CATEGORY_CALL)

        // Privacy
        setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        setPublicVersion(publicNotification)

        // Caller information
        setLocusId(LocusIdCompat(ContactUtil.getUniqueIdString(callerIdentity)))
        addPerson(person)
        setStyle(
            NotificationCompat.CallStyle.forIncomingCall(
                person,
                rejectIntent,
                acceptIntent,
            ),
        )

        build()
    }.apply {
        // Set flags
        flags = flags or (NotificationCompat.FLAG_INSISTENT or NotificationCompat.FLAG_NO_CLEAR or NotificationCompat.FLAG_ONGOING_EVENT)
    }

    private fun getPublicNotification(): Notification =
        with(NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS)) {
            setContentTitle(appContext.getString(R.string.voip_notification_title))
            setContentText(appContext.getString(R.string.notification_hidden_text))
            setSmallIcon(R.drawable.ic_phone_locked_white_24dp)
            setGroup(NotificationGroups.CALLS)
            setGroupSummary(false)
            setCategory(NotificationCompat.CATEGORY_CALL)
            build()
        }

    private fun areIncomingCallNotificationsEnabled(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) {
            logger.warn("Notifications are disabled.")
            return false
        }

        if (!ConfigUtils.supportsNotificationChannels()) {
            return true
        }

        val notificationChannel = notificationManager.getNotificationChannel(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS) ?: run {
            logger.warn("Notification channel for incoming calls is missing.")
            return false
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val notificationChannelGroup = notificationChannel.group?.let { groupId ->
                notificationManager.getNotificationChannelGroup(groupId)
            }
            if (notificationChannelGroup?.isBlocked == true) {
                logger.warn("Notification channel group is blocked.")
                return false
            }
        }

        return notificationChannel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }
}
