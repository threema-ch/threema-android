/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.services

import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.blob.BlobLoader
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.blob.BlobUploader
import ch.threema.domain.types.Identity
import java.io.IOException
import java.net.URL

@SessionScoped
interface ApiService {
    /**
     * @param blobScope Will only have an effect if multi-device is currently active
     */
    @Throws(ThreemaException::class)
    fun createUploader(
        data: ByteArray,
        shouldPersist: Boolean,
        blobScope: BlobScope,
    ): BlobUploader

    fun createLoader(blobId: ByteArray): BlobLoader

    @Throws(ThreemaException::class)
    fun getAuthToken(): String?

    /**
     * Invalidate the auth token (only used for onprem). This forces a new fetch of the auth token
     * the next time the token is obtained with [.getAuthToken].
     */
    fun invalidateAuthToken()

    @Throws(ThreemaException::class, IOException::class)
    fun getAvatarURL(identity: Identity): URL
}
