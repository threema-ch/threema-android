package ch.threema.app.notifications.migrations

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationManagerCompat

interface NotificationChannelMigration {
    fun migrate(
        context: Context,
        sharedPreferences: SharedPreferences,
        notificationManager: NotificationManagerCompat,
    )
}
