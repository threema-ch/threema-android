package ch.threema.app.location

import androidx.annotation.WorkerThread
import ch.threema.app.ThreemaApplication
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.ServerAddressProvider
import java.lang.Exception
import kotlinx.coroutines.runBlocking
import org.maplibre.android.geometry.LatLng

private val logger = getThreemaLogger("NearbyPoiUtil")

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
