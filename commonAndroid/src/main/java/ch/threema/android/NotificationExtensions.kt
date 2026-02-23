package ch.threema.android

import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

fun NotificationManagerCompat.exists(channelId: String): Boolean =
    getNotificationChannelCompat(channelId) != null

fun NotificationChannelCompat.Builder.setSound(sound: Uri?, usage: Int) {
    val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
        .setUsage(usage)
        .build()
    setSound(sound, audioAttributes)
}
