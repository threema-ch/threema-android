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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ExifInterface
import ch.threema.app.utils.toJpegByteArray
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.io.File
import kotlinx.serialization.Serializable

/**
 * This class represents a profile picture. It ensures that the profile picture is [ProtocolDefines.PROFILE_PICTURE_WIDTH_PX] pixels wide and
 * [ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX] pixels high and that it is compressed as a jpeg.
 */
@Serializable
class CheckedProfilePicture private constructor(override val profilePictureBytes: ByteArray) : ProfilePicture {

    override fun isValid() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CheckedProfilePicture

        return profilePictureBytes.contentEquals(other.profilePictureBytes)
    }

    override fun hashCode() = profilePictureBytes.contentHashCode()

    companion object {
        /**
         * The quality when compressing a bitmap.
         */
        private const val QUALITY = 100

        /**
         * Read the profile picture from the file. The bytes are checked to be a valid profile picture. In case the bytes are not valid, they are
         * converted to a jpeg of the correct dimensions. If this fails, null is returned.
         */
        @JvmStatic
        fun getOrConvertFromFile(profilePictureFile: File?): CheckedProfilePicture? {
            val profilePictureBytes = profilePictureFile?.readBytes() ?: return null

            return getOrConvertFromBytes(profilePictureBytes)
        }

        /**
         * Converts the [imageBytes] to a jpeg profile picture with the correct dimensions in case the [imageBytes] can be decoded as bitmap.
         */
        @JvmStatic
        fun getOrConvertFromBytes(imageBytes: ByteArray?): CheckedProfilePicture? {
            if (imageBytes == null) {
                return null
            }

            // We decode the byte array to a bitmap because this allows us to check the dimensions and convert it to a jpeg if required.
            val bitmap: Bitmap? = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            return try {
                if (bitmap.isValidProfilePictureDimensions() && imageBytes.isJpeg()) {
                    // In case it is a jpeg and the dimensions are good, we directly use the provided bytes.
                    CheckedProfilePicture(imageBytes)
                } else {
                    // Otherwise, we compress the bitmap to a valid sized jpeg.
                    getOrConvertFromBitmap(bitmap)
                }
            } finally {
                bitmap?.recycle()
            }
        }

        /**
         * Read the profile picture from the bitmap. The bitmap will be resized if it is wider than [ProtocolDefines.PROFILE_PICTURE_WIDTH_PX] pixels
         * or higher than [ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX] pixels.
         */
        @JvmStatic
        fun getOrConvertFromBitmap(bitmap: Bitmap?): CheckedProfilePicture? {
            if (bitmap == null) {
                return null
            }

            if (bitmap.isValidProfilePictureDimensions()) {
                return bitmap.toJpegByteArray(quality = QUALITY)?.let(::CheckedProfilePicture)
            }

            val resizedBitmap = BitmapUtil.resizeBitmap(
                bitmap,
                ProtocolDefines.PROFILE_PICTURE_WIDTH_PX,
                ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX,
            )
            return resizedBitmap
                ?.toJpegByteArray(quality = QUALITY)
                .also { resizedBitmap?.recycle() }
                ?.let(::CheckedProfilePicture)
        }

        private fun ByteArray.isJpeg() = ExifInterface.isJpegFormat(this)

        private fun Bitmap?.isValidProfilePictureDimensions(): Boolean =
            this != null &&
                width <= ProtocolDefines.PROFILE_PICTURE_WIDTH_PX &&
                height <= ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX
    }
}
