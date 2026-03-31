package ch.threema.app.errorreporting

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * Provides an ID which identifies the device in error reports sent to Sentry.
 * The ID is generated randomly such that no connection can be made to the user's Threema ID.
 */
class SentryIdProvider(
    private val sharedPreferences: SharedPreferences,
) {
    fun getOrGenerateSentryId(): String {
        val sentryId = getSentryId()
        if (sentryId != null) {
            return sentryId
        }
        val newSentryId = generateSentryId()
        sharedPreferences.edit {
            putString(KEY_SENTRY_ID, newSentryId)
        }
        return newSentryId
    }

    fun getSentryId(): String? =
        sharedPreferences.getString(KEY_SENTRY_ID, null)

    fun deleteSentryId() {
        sharedPreferences.edit {
            remove(KEY_SENTRY_ID)
        }
    }

    private fun generateSentryId() =
        UUID.randomUUID().toString()

    companion object {
        private const val KEY_SENTRY_ID = "sentry_id"
    }
}
