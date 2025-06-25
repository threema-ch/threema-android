/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.rating

import androidx.annotation.WorkerThread
import ch.threema.app.utils.buildRequest
import ch.threema.base.ThreemaException
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

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw ThreemaException("Failed to create rating (code ${response.code})")
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
