/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.storage.models.data

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.location.Poi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("LocationDataModel")

data class LocationDataModel(
    @JvmField val latitude: Double,
    @JvmField val longitude: Double,
    @JvmField val accuracy: Double?,
    @JvmField val poi: Poi?,
) : MessageDataInterface {
    /**
     *  While our protocol defines [accuracy] as optional, a lot of classes
     *  (e.g. [android.location.Location]) required a value.
     *
     *  @see android.location.Location.setAccuracy
     */
    @JvmField
    val accuracyOrFallback: Double = accuracy ?: 0.0

    @JvmField
    val poiNameOrNull: String? = poi?.name?.takeIf(String::isNotBlank)

    @JvmField
    val poiAddressOrNull: String? = poi?.address?.takeIf(String::isNotBlank)

    /**
     *
     * Note that the json array will contain explicit null values for optional fields.
     * For example: `"[30.567523,-120.573323,null,null,null]"`
     *
     * @return A string defining a json array, or empty
     */
    override fun toString(): String =
        buildJsonArray {
            add(latitude)
            add(longitude)
            add(accuracy)
            add(poi?.address)
            add(poi?.name)
        }.toString()

    companion object {
        /**
         *  Creates an instance with the minimum required values by the protocol.
         */
        private fun defaultValuesInstance() = LocationDataModel(
            latitude = 0.0,
            longitude = 0.0,
            accuracy = null,
            poi = null,
        )

        /**
         *  Expecting the json string to be a json array in the form of `[Double, Double, Double?, String?, String?]`
         *
         *  The array **must** always contain at least 2 elements.
         *
         *  **Valid** examples:
         *  - `[30.461723, -120.725289]`
         *  - `[30.461723, -120.725289, null]`
         *  - `[30.461723, -120.725289, null, null]`
         *  - `[30.461723, -120.725289, null, null, null]`
         *  - `[30.461723, -120.725289, 1.000000, null, null]`
         *  - `[30.461723, -120.725289, 1.000000, "Cool City, Cool Street", null]`
         *  - `[30.461723, -120.725289, 1.000000, "Cool City, Cool Street", "Pizza Place"]`
         *  - `[30.461723, -120.725289, 1.000000, "Cool City, Cool Street", "Pizza Place", "Ignored"]`
         *  - `[30.461723, -120.725289, 1.000000, "Cool City, Cool Street"]`
         *
         *  **Invalid** examples:
         *  - `[30.461723]`
         *  - `[null, null, null, null, null]`
         *  - `[30.461723, null, null, null, null]`
         */
        @JvmStatic
        fun fromStringOrDefault(jsonString: String?): LocationDataModel {
            if (jsonString == null) {
                return defaultValuesInstance()
            }

            try {
                val jsonArray: JsonArray = Json.decodeFromString<JsonArray>(jsonString)

                check(jsonArray.size >= 2) {
                    "Expected at least 2 json array elements"
                }

                // Expect the first element to be a double, throws otherwise
                val latitude: Double = jsonArray[0].jsonPrimitive.content.toDouble()

                // Expect the second element to be a double, throws otherwise
                val longitude: Double = jsonArray[1].jsonPrimitive.content.toDouble()

                // Accuracy is not required, but if there is another value that is not a double, we throw
                val accuracy: Double? =
                    when (val jsonElement: JsonElement? = jsonArray.getOrNull(2)) {
                        JsonNull, null -> null
                        is JsonPrimitive -> jsonElement.content.toDouble()
                        else -> throw IllegalStateException("Expected a double value or explicit null at array index 2")
                    }

                // PoiAddress is not required, but if there is another value that is not a string, we throw
                val poiAddress: String? =
                    when (val jsonElement: JsonElement? = jsonArray.getOrNull(3)) {
                        JsonNull, null -> null
                        is JsonPrimitive -> if (jsonElement.isString) {
                            jsonElement.content
                        } else {
                            throw IllegalStateException("Expected a string value or explicit null at array index 3")
                        }

                        else -> throw IllegalStateException("Expected a string value or explicit null at array index 3")
                    }

                // PoiName is not required, but if there is another value that is not a string, we throw
                val poiName: String? =
                    when (val jsonElement: JsonElement? = jsonArray.getOrNull(4)) {
                        JsonNull, null -> null
                        is JsonPrimitive -> if (jsonElement.isString) {
                            jsonElement.content
                        } else {
                            throw IllegalStateException("Expected a string value or explicit null at array index 4")
                        }

                        else -> throw IllegalStateException("Expected a string value or explicit null at array index 4")
                    }

                val poi: Poi? = when {
                    !poiName.isNullOrBlank() && !poiAddress.isNullOrBlank() -> Poi.Named(
                        name = poiName,
                        address = poiAddress,
                    )

                    !poiAddress.isNullOrBlank() -> Poi.Unnamed(address = poiAddress)
                    else -> null
                }

                return LocationDataModel(
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = accuracy,
                    poi = poi,
                )
            } catch (exception: Exception) {
                logger.error(exception.message)
                return defaultValuesInstance()
            }
        }

        @Deprecated("Do not use. This only exists to handle places where null cannot be returned")
        fun createEmpty(): LocationDataModel =
            defaultValuesInstance()
    }
}
