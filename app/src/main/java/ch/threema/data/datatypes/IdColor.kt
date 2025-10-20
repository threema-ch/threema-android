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

package ch.threema.data.datatypes

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.toColorInt
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.types.Identity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

private val logger = LoggingUtil.getThreemaLogger("IdColor")

class IdColor(index: Int) {
    /**
     * The color index of this [IdColor]. If this [IdColor] is invalid [INVALID_COLOR_INDEX] is returned.
     */
    val colorIndex =
        if (isValidIdColorIndex(index)) {
            index
        } else {
            INVALID_COLOR_INDEX
        }

    val isValid: Boolean
        get() = isValidIdColorIndex(colorIndex)

    val colorLight: Int
        @ColorInt
        get() = getIdColorLightString(colorIndex).toColorInt()

    val colorDark: Int
        @ColorInt
        get() = getIdColorDarkString(colorIndex).toColorInt()

    @ColorInt
    fun getThemedColor(context: Context): Int = getThemedIdColorString(context, colorIndex).toColorInt()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdColor

        return colorIndex == other.colorIndex
    }

    override fun hashCode(): Int {
        return colorIndex
    }

    companion object {
        private const val INVALID_COLOR_INDEX = -1

        private data class IdColorHex(val light: String, val dark: String)

        /**
         * The available id colors.
         *
         * If colors are removed or added, a database migration might be required to recalculate the persisted indices.
         */
        private val ID_COLORS: Array<IdColorHex> = arrayOf(
            IdColorHex(light = "#d84315", dark = "#ff7043"),
            IdColorHex(light = "#ef6c00", dark = "#ffa726"),
            IdColorHex(light = "#ff8f00", dark = "#ffca28"),
            IdColorHex(light = "#eb9c00", dark = "#fff176"),
            IdColorHex(light = "#9e9d24", dark = "#a6a626"),
            IdColorHex(light = "#7cb342", dark = "#8bc34a"),
            IdColorHex(light = "#2e7d32", dark = "#66bb6a"),
            IdColorHex(light = "#00796b", dark = "#2ab7a9"),
            IdColorHex(light = "#0097a7", dark = "#26c6da"),
            IdColorHex(light = "#0288d1", dark = "#4fc3f7"),
            IdColorHex(light = "#1565c0", dark = "#42a5f5"),
            IdColorHex(light = "#283593", dark = "#8a93ff"),
            IdColorHex(light = "#7b3ab7", dark = "#a88ce3"),
            IdColorHex(light = "#ac24aa", dark = "#c680d1"),
            IdColorHex(light = "#ad1457", dark = "#f16f9a"),
            IdColorHex(light = "#c62828", dark = "#f2706e"),
        )

        private val INVALID_ID_COLOR = IdColorHex(
            light = "#FF777777",
            dark = "#FFAAAAAA",
        )

        /**
         * Compute the contact id color index based on the identity.
         */
        @JvmStatic
        fun ofIdentity(identity: Identity): IdColor {
            return identity.toByteArray(StandardCharsets.UTF_8).computeIdColor()
        }

        /**
         * Compute the group id color index based on the GroupIdentity.
         */
        @JvmStatic
        fun ofGroup(groupIdentity: GroupIdentity): IdColor {
            val groupCreatorIdentity = groupIdentity.creatorIdentity.toByteArray(StandardCharsets.UTF_8)
            val apiGroupIdBin = groupIdentity.groupIdByteArray
            return (groupCreatorIdentity + apiGroupIdBin).computeIdColor()
        }

        /**
         * Compute the distribution list id color based on the distribution list id.
         */
        @JvmStatic
        fun ofDistributionList(distributionListId: Long): IdColor {
            val idBytes = byteArrayOf(
                (distributionListId ushr 56).toByte(),
                (distributionListId ushr 48).toByte(),
                (distributionListId ushr 40).toByte(),
                (distributionListId ushr 32).toByte(),
                (distributionListId ushr 24).toByte(),
                (distributionListId ushr 16).toByte(),
                (distributionListId ushr 8).toByte(),
                distributionListId.toByte(),
            )
            return idBytes.computeIdColor()
        }

        @JvmStatic
        fun invalid(): IdColor = IdColor(INVALID_COLOR_INDEX)

        private fun ByteArray.computeIdColor(): IdColor =
            try {
                logger.debug("Compute id color for {}", this)
                val firstByte = MessageDigest.getInstance("SHA-256").digest(this).first()
                IdColor(firstByte.getIdColorIndex())
            } catch (e: NoSuchAlgorithmException) {
                logger.error("Could not find hashing algorithm for id color", e)
                invalid()
            }

        /**
         * Get the color index for this byte. This should always be the first byte of the color id hash.
         *
         * @return the color index for this byte
         */
        private fun Byte.getIdColorIndex(): Int {
            return (((toInt()) and 0xff) / ID_COLORS.size)
        }

        private fun isValidIdColorIndex(index: Int): Boolean = index in ID_COLORS.indices

        /**
         * The light mode id color code as a hex string. If the id color is invalid, this defaults to a gray color.
         */
        @VisibleForTesting
        fun getIdColorLightString(index: Int): String =
            if (isValidIdColorIndex(index)) {
                ID_COLORS[index].light
            } else {
                INVALID_ID_COLOR.light
            }

        /**
         * The dark mode id color code as a hex string. If the id color is invalid, this defaults to a gray color.
         */
        @VisibleForTesting
        fun getIdColorDarkString(index: Int): String =
            if (isValidIdColorIndex(index)) {
                ID_COLORS[index].dark
            } else {
                INVALID_ID_COLOR.dark
            }

        @VisibleForTesting
        fun getThemedIdColorString(context: Context, index: Int): String =
            if (ConfigUtils.isTheDarkSide(context)) {
                getIdColorDarkString(index)
            } else {
                getIdColorLightString(index)
            }
    }
}
