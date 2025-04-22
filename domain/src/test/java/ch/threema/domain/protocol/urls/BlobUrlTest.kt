/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.domain.protocol.urls

import ch.threema.domain.protocol.csp.ProtocolDefines.BLOB_ID_LEN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlobUrlTest {
    @Test
    fun `blobId placeholders are replaced`() {
        val blobUrl = BlobUrl("https://ds-blobp-{blobIdPrefix}.test.threema.ch/download/{blobId}/")

        val url = blobUrl.get(blobId = BLOB_ID)

        assertEquals("https://ds-blobp-f0.test.threema.ch/download/f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff/", url)
    }

    @Test
    fun `empty blobId is considered invalid`() {
        val blobUrl = BlobUrl("https://ds-blobp-{blobIdPrefix}.test.threema.ch/download/{blobId}/")

        assertFailsWith<IllegalArgumentException> {
            blobUrl.get(blobId = ByteArray(0))
        }
    }

    companion object {
        private val BLOB_ID = ByteArray(BLOB_ID_LEN) { (it + 0xF0).toByte() }
    }
}
