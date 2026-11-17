package ch.threema.app.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import ch.threema.android.DeviceInfo
import ch.threema.android.buildNotification
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.services.notification.BaseNotificationService
import ch.threema.app.usecases.contacts.GetPersonUseCase
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.models.ContactModelData
import ch.threema.domain.types.IdentityString

private val logger = getThreemaLogger("CallNotificationManagerImpl")

class CallNotificationManagerImpl(
    appContext: Context,
    notificationManagerCompat: NotificationManagerCompat,
    private val notificationPreferenceService: NotificationPreferenceService,
    private val preferenceService: PreferenceService,
    private val contactService: ContactService,
    private val getPersonUseCase: GetPersonUseCase,
) : CallNotificationManager, BaseNotificationService(appContext, notificationManagerCompat) {

    @SuppressLint("MissingPermission")
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
        try {
            try {
                notificationManagerCompat.notify(
                    contactModelData.identity,
                    NotificationIDs.INCOMING_CALL_NOTIFICATION_ID,
                    notification,
                )
            } catch (e: SecurityException) {
                if (contactModelData.androidContactLookupInfo == null) {
                    throw e
                }
                logger.warn("Failed to display notification, trying again without contact lookup info", e)

                // It might be that the SecurityException was caused by the Android contact lookup info,
                // so we try again without it.
                val notificationWithoutAndroidContactLookupInfo = getPrivateNotification(
                    callerIdentity = contactModelData.identity,
                    contactName = contactName,
                    acceptIntent = acceptIntent,
                    inCallPendingIntent = inCallPendingIntent,
                    rejectIntent = rejectIntent,
                    person = getPersonUseCase.call(
                        identity = contactModelData.identity,
                        androidContactLookupInfo = null,
                        name = contactName,
                    ),
                    publicNotification = getPublicNotification(),
                )

                notificationManagerCompat.notify(
                    contactModelData.identity,
                    NotificationIDs.INCOMING_CALL_NOTIFICATION_ID,
                    notificationWithoutAndroidContactLookupInfo,
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to display notification", e)
            return CallNotificationManager.IncomingCallNotificationResult.Failed
        }

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
    ): Notification = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS) {
        setContentTitle(appContext.getString(R.string.voip_notification_title))
        setContentText(appContext.getString(R.string.voip_notification_text, contactName))
        setOngoing(true)
        setWhen(System.currentTimeMillis())
        setAutoCancel(false)
        setShowWhen(true)
        setGroup(NotificationGroups.CALLS)
        setGroupSummary(false)

        if (!DeviceInfo.supportsNotificationChannels()) {
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
        setLocusId(
            LocusIdCompat(
                ContactConversationId(callerIdentity).obfuscated.value,
            ),
        )
        addPerson(person)
        setStyle(
            NotificationCompat.CallStyle.forIncomingCall(
                person,
                rejectIntent,
                acceptIntent,
            ),
        )
    }.apply {
        flags = flags or (NotificationCompat.FLAG_INSISTENT or NotificationCompat.FLAG_NO_CLEAR or NotificationCompat.FLAG_ONGOING_EVENT)
    }

    private fun getPublicNotification() = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS) {
        setContentTitle(appContext.getString(R.string.voip_notification_title))
        setContentText(appContext.getString(R.string.notification_hidden_text))
        setSmallIcon(R.drawable.ic_phone_locked_white_24dp)
        setGroup(NotificationGroups.CALLS)
        setGroupSummary(false)
        setCategory(NotificationCompat.CATEGORY_CALL)
    }

    private fun areIncomingCallNotificationsEnabled(): Boolean {
        if (!hasNotificationPermission()) {
            logger.warn("Notifications are disabled.")
            return false
        }

        if (!DeviceInfo.supportsNotificationChannels()) {
            return true
        }

        val notificationChannel = notificationManagerCompat.getNotificationChannel(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS)
            ?: run {
                logger.warn("Notification channel for incoming calls is missing.")
                return false
            }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val notificationChannelGroup = notificationChannel.group?.let { groupId ->
                notificationManagerCompat.getNotificationChannelGroup(groupId)
            }
            if (notificationChannelGroup?.isBlocked == true) {
                logger.warn("Notification channel group is blocked.")
                return false
            }
        }

        return notificationChannel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }
}
