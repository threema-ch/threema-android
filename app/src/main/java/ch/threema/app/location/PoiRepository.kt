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

package ch.threema.app.location

import ch.threema.base.ThreemaException
import ch.threema.common.buildRequest
import ch.threema.common.executeAsync
import ch.threema.common.getStringOrNull
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.models.Coordinates
import ch.threema.common.toJSONObjectList
import ch.threema.domain.protocol.ServerAddressProvider
import java.io.IOException
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PoiRepository(
    private val okHttpClient: OkHttpClient,
    private val serverAddressProvider: ServerAddressProvider,
) {
    @Throws(ThreemaException::class, IOException::class)
    suspend fun getNearbyPois(
        center: Coordinates,
        limit: Int,
    ): List<NearbyPoi> {
        val parameterizedUrl = serverAddressProvider.getMapPoiAroundUrl()
            ?: throw ThreemaException("No URL available to fetch nearby POIs")
        val request = buildRequest {
            url(parameterizedUrl.get(center, NEARBY_RADIUS))
        }

        val response = okHttpClient.executeAsync(request)
        try {
            val results = JSONArray(response.getSuccessBodyOrThrow().string())
            return results.toJSONObjectList()
                .mapNotNull { result ->
                    parseNearbyPoiResult(result)
                }
                .take(limit)
        } catch (e: JSONException) {
            throw ThreemaException("Failed to parse nearby POI response", e)
        }
    }

    private fun parseNearbyPoiResult(result: JSONObject): NearbyPoi? {
        return NearbyPoi(
            id = result.getLong("id"),
            name = result.getString("name"),
            coordinates = Coordinates(
                latitude = result.getDouble("lat"),
                longitude = result.getDouble("lon"),
            ),
            type = parseNearbyPoiType(result) ?: return null,
            isNatural = result.has("natural"),
        )
    }

    private fun parseNearbyPoiType(result: JSONObject): String? {
        val type = result.getStringOrNull("amenity")
            ?: result.getStringOrNull("shop")
            ?: result.getStringOrNull("tourism")
            ?: result.getStringOrNull("sport")
            ?: result.getStringOrNull("leisure")
            ?: result.getStringOrNull("natural")
            ?: result.getStringOrNull("public_transport")
            ?: (if (result.has("aeroway")) "airport" else null)
            ?: (if (result.has("aerialway")) "cablecar" else null)
            ?: result.getStringOrNull("highway")

        return when (type) {
            "fast_food",
            "food_court",
            -> "food"
            "hairdresser",
            -> "hair_care"
            "fitness_centre",
            -> "gym"
            "do_it_yourself",
            "doityourself",
            -> "hardware_store"
            "kindergarten",
            -> "child_care"
            "nursing_home",
            "clinic",
            "social_facility",
            -> "hospital"
            "parking_entrace",
            -> "parking"
            "playground",
            -> "park"
            "car_sharing",
            -> "car_rental"
            "car",
            -> "car_dealer"
            "hotel",
            -> "lodging"
            "doctors",
            -> "doctor"
            "recycling",
            -> "establishment"
            "mall",
            -> "shopping_mall"
            "swimming_pool",
            "water_park",
            -> "swimming"
            "arts_centre",
            -> "gallery"
            "dry_cleaning",
            -> "laundry"
            "second_hand",
            -> "furniture"
            "boutique",
            "clothes",
            -> "clothing_store"
            "beauty",
            "depilation",
            -> "beauty_salon"
            "chemist",
            -> "health"
            "townhall",
            -> "local_government_office"
            "bicycle",
            "bicycle_rental",
            -> "bicycle_store"
            "cave_entrance",
            -> "park"
            "peak",
            "volcano",
            -> "mountain"
            "pub",
            -> "bar"
            "stop_position",
            -> "bus_station"
            "stationery",
            "kiosk",
            -> "store"
            "shoes",
            -> "shoe_store"
            "aerialway",
            -> "cablecar"
            "rest_area",
            "services",
            -> "highway"
            "cinema",
            -> "movie_theater"
            else -> type
        }
    }

    @Throws(ThreemaException::class, IOException::class)
    suspend fun getPoiNames(
        center: Coordinates,
        query: String,
    ): List<NamedPoi> {
        val parameterizedUrl = serverAddressProvider.getMapPoiNamesUrl()
            ?: throw ThreemaException("No URL available to fetch POI names")
        val request = buildRequest {
            url(parameterizedUrl.get(center, query))
        }

        val response = okHttpClient.executeAsync(request)
        try {
            val results = JSONArray(response.getSuccessBodyOrThrow().string())
            return results.toJSONObjectList()
                .mapNotNull { result ->
                    parseNamedPoiResult(result)
                }
        } catch (e: JSONException) {
            throw ThreemaException("Failed to parse POI names response", e)
        }
    }

    private fun parseNamedPoiResult(result: JSONObject): NamedPoi? {
        return NamedPoi(
            name = result.getString("name"),
            coordinates = Coordinates(
                latitude = result.getDouble("lat"),
                longitude = result.getDouble("lon"),
            ),
            distance = if (result.has("dist")) {
                result.getInt("dist")
            } else {
                return null
            },
            description = if (result.has("highway")) {
                "street"
            } else {
                result.getStringOrNull("place")
            },
        )
    }

    companion object {
        /**
         * Search radius in meters
         */
        private const val NEARBY_RADIUS = 750
    }
}
