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

import androidx.annotation.WorkerThread
import kotlinx.serialization.Serializable

/**
 * This class represents a profile picture. Its byte representation is not guaranteed to be a valid profile picture - let alone a picture at all.
 * This class should be used whenever we can or must assume that we have a valid profile picture.
 */
@Serializable
data class RawProfilePicture(override val bytes: ByteArray) : ProfilePicture {
    private val checkedProfilePicture: CheckedProfilePicture? by lazy {
        CheckedProfilePicture.getOrConvertFromBytes(bytes)
    }

    override fun isValid() = checkedProfilePicture != null

    /**
     * Converts this profile picture to a valid profile picture. If this isn't possible, null is returned. If this profile picture already is a valid
     * profile picture, its byte representation is wrapped into a [CheckedProfilePicture] without conversion.
     */
    @WorkerThread
    fun toChecked(): CheckedProfilePicture? = checkedProfilePicture

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawProfilePicture

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode() = bytes.contentHashCode()
}
