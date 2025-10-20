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

package ch.threema.app.location

import androidx.annotation.WorkerThread
import ch.threema.app.ThreemaApplication
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.ServerAddressProvider
import java.lang.Exception
import kotlinx.coroutines.runBlocking
import org.maplibre.android.geometry.LatLng

private val logger = LoggingUtil.getThreemaLogger("NearbyPoiUtil")

@Deprecated("This object mainly exists as a wrapper around PoiRepository to make it usable from Java, where coroutines cannot be used.")
object NearbyPoiUtil {
    /**
     * Fetch POIs around the specified location.
     */
    @WorkerThread
    @JvmStatic
    fun getPOIs(
        center: LatLng,
        maxCount: Int,
        serverAddressProvider: ServerAddressProvider,
    ): List<NearbyPoi>? {
        if (center.latitude == 0.0 && center.longitude == 0.0) {
            logger.debug("ignoring POI fetch request for 0/0")
            return null
        }
        val serviceManager = ThreemaApplication.getServiceManager()
            ?: return null
        val poiRepository = PoiRepository(
            okHttpClient = serviceManager.okHttpClient,
            serverAddressProvider = serverAddressProvider,
        )

        try {
            return runBlocking {
                poiRepository.getNearbyPois(
                    center = center.toCoordinates(),
                    limit = maxCount,
                )
            }
        } catch (e: InterruptedException) {
            // ignore
        } catch (e: Exception) {
            logger.error("Failed to fetch nearby POIs", e)
        }

        return null
    }
}
