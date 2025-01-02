/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.domain.protocol.blob

/**
 *  This scope only takes effect when dealing with the blob mirror server.
 *  It needs to be passed as a query parameter to all 3 endpoints (upload, download, done).
 */
sealed class BlobScope(
    @JvmField val name: String
) {
    /**
     *  Blob is **only** present blob mirror server to share between devices in the same device group
     */
    data object Local : BlobScope("local")

    /**
     *  Blob is **both** present on the mirror server and on the default blob server
     */
    data object Public : BlobScope("public")
}
