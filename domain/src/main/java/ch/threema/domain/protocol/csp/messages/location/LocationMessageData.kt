/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.location

import ch.threema.domain.protocol.csp.messages.BadMessageException
import java.nio.charset.StandardCharsets
import java.util.Locale

data class LocationMessageData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val poi: Poi?,
) {
    fun toBodyString(): String = buildString {
        append(String.format(Locale.US, "%f,%f", latitude, longitude))
        accuracy?.let { accuracyNotNull ->
            append(String.format(Locale.US, ",%f", accuracyNotNull))
        }
        poi?.name?.let { poiNameNotNull ->
            append("\n", poiNameNotNull)
        }
        poi?.address?.let { poiAddressNotNull ->
            append("\n", poiAddressNotNull.replace("\n", "\\n"))
        }
    }

    companion object {
        const val MINIMUM_REQUIRED_BYTES = 3

        /**
         * Build an instance of [LocationMessageData] from the given [data] bytes.
         *
         * The [data] byte array consists of at least one text line
         * - The first line **must** contain the latitude and longitude and **can** contain a third value interpreted as the accuracy.
         * These values are separated by a comma.
         * - The second optional line can contain the poi-name, **or** the poi-address.
         * - The third optional line will contain the the poi-address if filled.
         *
         * @param data   the data that represents the location message
         * @param offset the offset where the actual data starts (inclusive)
         * @param length the length of the data (needed to ignore the padding)
         * @return Instance of [LocationMessageData]
         * @throws BadMessageException if the length or the offset is invalid
         */
        @Throws(BadMessageException::class)
        fun parse(data: ByteArray, offset: Int, length: Int): LocationMessageData {
            when {
                length < MINIMUM_REQUIRED_BYTES -> throw BadMessageException("Bad length ($length) for location message")
                offset < 0 -> throw BadMessageException("Bad offset ($offset) for location message")
                offset + length > data.size -> throw BadMessageException(
                    "Passed offset $offset and length $length exceed the actual data size of ${data.size}",
                )
            }

            val locationString = String(data, offset, length, StandardCharsets.UTF_8)

            val locationStringLines: List<String> = locationString.split("\n")

            val locationValues: List<Double> = locationStringLines
                .first()
                .split(",")
                .mapIndexed { index, locationDoubleValueAsString ->
                    runCatching {
                        locationDoubleValueAsString.toDouble()
                    }.getOrElse { throwable ->
                        throw BadMessageException(
                            "Location message has bad number format for specific location value at index $index",
                            throwable,
                        )
                    }
                }

            if (locationValues.size < 2 || locationValues.size > 3) {
                throw BadMessageException("Location message has wrong number (${locationValues.size}) of specific location values in first line")
            } else if (locationValues[0] < -90.0 || locationValues[0] > 90.0) {
                throw BadMessageException("Location message has invalid latitude value of ${locationValues[0]}")
            } else if (locationValues[1] < -180.0 || locationValues[1] > 180.0) {
                throw BadMessageException("Location message has invalid longitude value of ${locationValues[1]}")
            }

            // Set basic values of latitude, longitude and maybe accuracy
            val latitude: Double = locationValues[0]
            val longitude: Double = locationValues[1]
            val accuracy: Double? = locationValues.getOrNull(2)

            // Determine poiAddress if present
            var poiAddress: String? = if (locationStringLines.size == 2) {
                locationStringLines[1]
            } else if (locationStringLines.size >= 3) {
                locationStringLines[2]
            } else {
                null
            }
            if (poiAddress != null) {
                poiAddress = poiAddress.replace("\\n", "\n")
            }

            //  Determine poiName if present
            val poiName: String? =
                if (locationStringLines.size >= 3) locationStringLines[1] else null

            val poi: Poi? = when {
                !poiName.isNullOrBlank() && !poiAddress.isNullOrBlank() -> Poi.Named(
                    name = poiName,
                    address = poiAddress,
                )

                !poiAddress.isNullOrBlank() -> Poi.Unnamed(address = poiAddress)
                else -> null
            }

            return LocationMessageData(
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                poi = poi,
            )
        }
    }
}

/**
 *  This implementation enforces the protocol rule:
 *  If `location.name` is defined but `location.address` is not defined, discard location
 */
sealed interface Poi {
    val name: String?

    val address: String?

    fun getCaptionOrNull(): String?

    fun getSnippetForSearchOrNull(): String?

    data class Named(
        override val name: String,
        override val address: String,
    ) : Poi {
        override fun getCaptionOrNull(): String? = when {
            name.isBlank() -> address.takeIf(String::isNotBlank)
            address.isNotBlank() -> "*$name*\n$address"
            else -> "*$name*"
        }

        /**
         *  @return Either `"name - address"`, `"name"`, `"address"` or ´null´
         */
        override fun getSnippetForSearchOrNull(): String? {
            val nameNotBlank = name.takeIf(String::isNotBlank)
            val addressNotBlank = address.takeIf(String::isNotBlank)
            nameNotBlank ?: addressNotBlank ?: return null

            return buildString {
                nameNotBlank?.let(::append)
                if (addressNotBlank != null) {
                    if (isNotEmpty()) {
                        append(" - ")
                    }
                    append(addressNotBlank)
                }
            }
        }
    }

    data class Unnamed(
        override val address: String,
    ) : Poi {
        override val name: String? = null

        override fun getCaptionOrNull(): String? = address.takeIf(String::isNotBlank)

        override fun getSnippetForSearchOrNull(): String? = address.takeIf(String::isNotBlank)
    }
}
