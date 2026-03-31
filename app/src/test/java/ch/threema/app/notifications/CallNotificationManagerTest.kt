package ch.threema.app.notifications

import android.app.NotificationChannel
import androidx.core.app.NotificationManagerCompat
import ch.threema.app.utils.ConfigUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.Test
import kotlin.test.assertIs

class CallNotificationManagerTest {

    @Test
    fun globallyDisabledNotificationsAreDetected() {
        val notificationManagerMock: NotificationManagerCompat = mockk {
            every { areNotificationsEnabled() } returns false
        }

        val callNotificationManager = CallNotificationManagerImpl(
            appContext = mockk(),
            notificationPreferenceService = mockk(),
            preferenceService = mockk(),
            contactService = mockk(),
            getPersonUseCase = mockk(),
            notificationManager = notificationManagerMock,
        )

        val result = callNotificationManager.showIncomingCallNotification(
            contactModelData = mockk(),
            acceptIntent = mockk(),
            inCallPendingIntent = mockk(),
            rejectIntent = mockk(),
        )
        assertIs<CallNotificationManager.IncomingCallNotificationResult.IncomingCallNotificationsDisabled>(result)
    }

    @Test
    fun missingIncomingCallNotificationChannelIsDetected() {
        val notificationManagerMock: NotificationManagerCompat = mockk {
            every { areNotificationsEnabled() } returns true
            every { getNotificationChannel(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS) } returns null
        }

        mockkStatic(ConfigUtils::class)
        every { ConfigUtils.supportsNotificationChannels() } returns true

        val callNotificationManager = CallNotificationManagerImpl(
            appContext = mockk(),
            notificationPreferenceService = mockk(),
            preferenceService = mockk(),
            contactService = mockk(),
            getPersonUseCase = mockk(),
            notificationManager = notificationManagerMock,
        )

        val result = callNotificationManager.showIncomingCallNotification(
            contactModelData = mockk(),
            acceptIntent = mockk(),
            inCallPendingIntent = mockk(),
            rejectIntent = mockk(),
        )
        assertIs<CallNotificationManager.IncomingCallNotificationResult.IncomingCallNotificationsDisabled>(result)

        unmockkStatic(ConfigUtils::class)
    }

    @Test
    fun missingImportanceForIncomingCallNotificationChannelIsDetected() {
        val notificationChannelMock: NotificationChannel = mockk {
            every { group } returns null
            every { importance } returns NotificationManagerCompat.IMPORTANCE_NONE
        }

        mockkStatic(ConfigUtils::class)
        every { ConfigUtils.supportsNotificationChannels() } returns true

        val notificationManagerMock: NotificationManagerCompat = mockk {
            every { areNotificationsEnabled() } returns true
            every { getNotificationChannel(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS) } returns notificationChannelMock
        }
        val callNotificationManager = CallNotificationManagerImpl(
            appContext = mockk(),
            notificationPreferenceService = mockk(),
            preferenceService = mockk(),
            contactService = mockk(),
            getPersonUseCase = mockk(),
            notificationManager = notificationManagerMock,
        )

        val result = callNotificationManager.showIncomingCallNotification(
            contactModelData = mockk(),
            acceptIntent = mockk(),
            inCallPendingIntent = mockk(),
            rejectIntent = mockk(),
        )
        assertIs<CallNotificationManager.IncomingCallNotificationResult.IncomingCallNotificationsDisabled>(result)

        unmockkStatic(ConfigUtils::class)
    }
}
