package ch.threema.app.rating

import androidx.annotation.WorkerThread
import ch.threema.base.ThreemaException
import ch.threema.common.buildRequest
import ch.threema.common.execute
import ch.threema.domain.protocol.ServerAddressProvider
import okhttp3.FormBody
import okhttp3.OkHttpClient

/**
 * Send ratings to the threema server
 */
class RatingService(
    private val ratingReferenceProvider: RatingReferenceProvider,
    private val okHttpClient: OkHttpClient,
    private val serverAddressProvider: ServerAddressProvider,
) {
    @WorkerThread
    @Throws(ThreemaException::class)
    fun sendRating(rating: Int, text: String, version: String) {
        try {
            val request = buildRequest {
                url(serverAddressProvider.getAppRatingUrl().get(rating))
                post(
                    FormBody.Builder()
                        .add("ref", ratingReferenceProvider.getOrCreateRatingReference())
                        .add("feedback", createFeedbackString(text, version))
                        .build(),
                )
            }

            okHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    throw ThreemaException("Failed to create rating (code ${response.code})")
                }
            }
        } catch (e: Exception) {
            throw ThreemaException("Failed to send rating", e)
        }
    }

    private fun createFeedbackString(text: String, version: String) =
        buildString {
            appendLine(text.trim())
            appendLine()
            appendLine("---")
            appendLine(version)
        }
}
