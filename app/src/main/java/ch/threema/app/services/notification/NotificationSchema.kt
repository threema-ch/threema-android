package ch.threema.app.services.notification

import android.net.Uri

// Only used on devices that don't support notification channels, i.e., Android 7
data class NotificationSchema @JvmOverloads constructor(
    @JvmField
    val shouldVibrate: Boolean = false,
    @JvmField
    val soundUri: Uri? = null,
    @JvmField
    val shouldUseLight: Boolean = false,
)
