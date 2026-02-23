package ch.threema.app.utils

import android.content.ContentResolver
import android.database.Cursor
import android.provider.MediaStore
import androidx.core.net.toUri
import ch.threema.app.services.ServicesConstants
import java.io.File

class RingtoneChecker(
    private val contentResolver: ContentResolver,
) {
    fun isValidRingtoneUri(ringtoneUri: String?): Boolean {
        when {
            ringtoneUri.isNullOrEmpty() -> {
                // silent ringtone
                return true
            }

            ringtoneUri == ServicesConstants.PREFERENCES_NULL -> {
                return false
            }

            ringtoneUri == DEFAULT_RINGTONE_URI -> {
                return true
            }

            else -> {
                val uri = ringtoneUri.toUri()
                try {
                    contentResolver.query(uri, PROJECTION, null, null, null).use { cursor ->
                        if (cursor?.moveToFirst() == true) {
                            if (cursor.pointsToValidFile() && cursor.isAcceptableTypeForRingtone()) {
                                return true
                            }
                        }
                    }
                } catch (e: Exception) {
                    // failed to check the ringtone's validity, consider it invalid
                }
                return false
            }
        }
    }

    private fun Cursor.pointsToValidFile(): Boolean {
        val path = getString(getProjectionIndex(MediaStore.MediaColumns.DATA))
        return path != null && File(path).exists()
    }

    private fun Cursor.isAcceptableTypeForRingtone(): Boolean {
        // It seems that RingtoneManager (which is used to let the user pick a ringtone)
        // sometimes doesn't respect the type filter given to it, and therefore
        // not only returns ringtones, but also alarm and notification sounds.
        // This isn't a big issue, as these can be played just the same, so we allow them here.
        val isRingtone = getInt(getProjectionIndex(MediaStore.Audio.Media.IS_RINGTONE)) == 1
        val isAlarm = getInt(getProjectionIndex(MediaStore.Audio.Media.IS_ALARM)) == 1
        val isNotification = getInt(getProjectionIndex(MediaStore.Audio.Media.IS_NOTIFICATION)) == 1
        return isRingtone || isAlarm || isNotification
    }

    companion object {
        private const val DEFAULT_RINGTONE_URI = "content://settings/system/ringtone"

        private val PROJECTION = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_NOTIFICATION,
        )

        private fun getProjectionIndex(value: String): Int =
            PROJECTION.indexOf(value)
    }
}
