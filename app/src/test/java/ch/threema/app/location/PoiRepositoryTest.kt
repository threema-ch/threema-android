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

import ch.threema.common.models.Coordinates
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.testhelpers.loadResource
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class PoiRepositoryTest {
    @Test
    fun `fetch poi names`() = runTest {
        val repository = PoiRepository(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                assertEquals(getPoiNamesUrl(CENTER, query = "Test"), request.url.toString())
                request.respondWith(loadResource("api/responses/poi-names.json"))
            },
            serverAddressProvider = mockServerAddressProvider(),
        )

        val pois = repository.getPoiNames(CENTER, query = "Test")

        assertEquals(
            listOf(
                NamedPoi(
                    name = "POI 1",
                    coordinates = Coordinates(47.5, 8.1),
                    distance = 20,
                    description = "village",
                ),
                NamedPoi(
                    name = "POI 2",
                    coordinates = Coordinates(47.1, 8.0),
                    distance = 25,
                    description = "hamlet",
                ),
                NamedPoi(
                    name = "POI 3",
                    coordinates = Coordinates(47.1, 8.0),
                    distance = 30,
                    description = "street",
                ),
            ),
            pois,
        )
    }

    @Test
    fun `fetching poi names fails with IOException`() = runTest {
        val repository = PoiRepository(
            okHttpClient = mockOkHttpClient {
                throw IOException()
            },
            serverAddressProvider = mockServerAddressProvider(),
        )

        assertFailsWith<IOException> {
            repository.getPoiNames(CENTER, query = "Test")
        }
    }

    @Test
    fun `fetch nearby pois`() = runTest {
        val repository = PoiRepository(
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                assertEquals(getNearbyPoiUrl(CENTER, radius = 750), request.url.toString())

                request.respondWith(loadResource("api/responses/nearby-poi.json"))
            },
            serverAddressProvider = mockServerAddressProvider(),
        )

        val pois = repository.getNearbyPois(CENTER, limit = 14)

        assertEquals(
            listOf(
                NearbyPoi(
                    id = 3925446122,
                    name = "Rathaus",
                    type = "platform",
                    isNatural = false,
                    coordinates = Coordinates(47.2690785, 8.7142272),
                ),
                NearbyPoi(
                    id = -24434288,
                    name = "Rathaus Parkplatz",
                    type = "parking",
                    isNatural = false,
                    coordinates = Coordinates(47.26955135, 8.71836055),
                ),
                NearbyPoi(
                    id = -25904302,
                    name = "Schützenhaus",
                    type = "shooting",
                    isNatural = false,
                    coordinates = Coordinates(47.271989850000004, 8.715951500000003),
                ),
                NearbyPoi(
                    id = 9127136671,
                    name = "Charging station",
                    type = "charging_station",
                    isNatural = false,
                    coordinates = Coordinates(47.2690642, 8.7185135),
                ),
                NearbyPoi(
                    id = 7556528544,
                    name = "Besucher-Info",
                    type = "information",
                    isNatural = false,
                    coordinates = Coordinates(47.2650615, 8.711721),
                ),
                NearbyPoi(
                    id = 443720477,
                    name = "Waldhüüsli",
                    type = "shelter",
                    isNatural = false,
                    coordinates = Coordinates(47.264990300000006, 8.7106736),
                ),
                NearbyPoi(
                    id = -263135458,
                    name = "Klinik am Waldrand",
                    type = "hospital",
                    isNatural = false,
                    coordinates = Coordinates(47.268759900000006, 8.72112655),
                ),
                NearbyPoi(
                    id = -25904311,
                    name = "Landgasthof Hirschen",
                    type = "restaurant",
                    isNatural = false,
                    coordinates = Coordinates(47.2709529, 8.7194953),
                ),
                NearbyPoi(
                    id = 2410744401,
                    name = "Bergli",
                    type = "mountain",
                    isNatural = true,
                    coordinates = Coordinates(47.266857900000005, 8.7068393),
                ),
                NearbyPoi(
                    id = 34094737,
                    name = "Au",
                    type = "saddle",
                    isNatural = true,
                    coordinates = Coordinates(47.2639846, 8.7129321),
                ),
                NearbyPoi(
                    id = 3350776547,
                    name = "Zum Gipfeli",
                    type = "bakery",
                    isNatural = false,
                    coordinates = Coordinates(47.270968100000005, 8.7202064),
                ),
                NearbyPoi(
                    id = -795158662,
                    name = "Spa",
                    type = "hospital",
                    isNatural = false,
                    coordinates = Coordinates(47.270551499999996, 8.72090025),
                ),
                NearbyPoi(
                    id = 7533838091,
                    name = "Info-Tafel",
                    type = "information",
                    isNatural = false,
                    coordinates = Coordinates(47.269865700000004, 8.721253200000001),
                ),
                NearbyPoi(
                    id = 3337928383,
                    name = "Bank GmbH",
                    type = "atm",
                    isNatural = false,
                    coordinates = Coordinates(47.270215500000006, 8.7213434),
                ),
            ),
            pois,
        )
    }

    @Test
    fun `fetching nearby pois fails with IOException`() = runTest {
        val repository = PoiRepository(
            okHttpClient = mockOkHttpClient {
                throw IOException()
            },
            serverAddressProvider = mockServerAddressProvider(),
        )

        assertFailsWith<IOException> {
            repository.getNearbyPois(CENTER, limit = 4)
        }
    }

    companion object {
        private val CENTER = Coordinates(47.2014375, 8.7809431)

        private fun mockServerAddressProvider() =
            mockk<ServerAddressProvider> {
                every { getMapPoiNamesUrl() } returns mockk {
                    every { get(any(), any()) } answers { getPoiNamesUrl(firstArg(), secondArg()) }
                }
                every { getMapPoiAroundUrl() } returns mockk {
                    every { get(any(), any()) } answers { getNearbyPoiUrl(firstArg(), secondArg()) }
                }
            }

        private fun getPoiNamesUrl(center: Coordinates, query: String): String =
            "https://example.com/names/${center.latitude}/${center.longitude}/$query"

        private fun getNearbyPoiUrl(center: Coordinates, radius: Int): String =
            "https://example.com/nearby/${center.latitude}/${center.longitude}/$radius"
    }
}
