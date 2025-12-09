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

package ch.threema.app.profilepicture

import ch.threema.domain.protocol.csp.ProtocolDefines
import kotlinx.serialization.Serializable

/**
 * Contains the bytes of a profile picture.
 */
@Serializable
sealed interface ProfilePicture {
    /**
     * The bytes that represent the profile picture.
     */
    val bytes: ByteArray

    /**
     * Checks whether the [bytes] correspond to a jpeg that is not larger than [ProtocolDefines.PROFILE_PICTURE_WIDTH_PX] and
     * [ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX] pixels.
     */
    fun isValid(): Boolean

    /**
     * Checks whether the two profile pictures are *structurally* equal to one another.
     *
     * Two profile pictures are considered structurally equal if their byte arrays have the same size, and elements at corresponding indices are
     * equal.
     */
    fun contentEquals(profilePicture: ProfilePicture): Boolean {
        return bytes.contentEquals(profilePicture.bytes)
    }
}
