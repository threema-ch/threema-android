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

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map

private val logger = getThreemaLogger("LocationAutocompleteViewModel")

class LocationAutocompleteViewModel(
    private val poiRepository: PoiRepository,
) : ViewModel() {
    private val currentQuery = MutableStateFlow<PoiQuery?>(null)
    private val _places: Flow<List<NamedPoi>> = currentQuery
        .map { query ->
            performSearch(query) ?: emptyList()
        }
        .conflate()
    val places: LiveData<List<NamedPoi>> = _places.asLiveData()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: LiveData<Boolean> = _isLoading.asLiveData()

    fun search(poiQuery: PoiQuery) {
        currentQuery.value = poiQuery
    }

    private suspend fun performSearch(poiQuery: PoiQuery?): List<NamedPoi>? {
        poiQuery ?: return null
        val query = poiQuery.query
        val center = poiQuery.center
        if (query == null || query.length < QUERY_MIN_LENGTH || (center.latitude == 0.0 && center.longitude == 0.0)) {
            return null
        }

        try {
            _isLoading.value = true
            return poiRepository.getPoiNames(center, query)
        } catch (e: ThreemaException) {
            logger.warn("Failed to fetch POI", e)
            return null
        } catch (e: IOException) {
            logger.warn("Failed to fetch POI", e)
            return null
        } finally {
            _isLoading.value = false
        }
    }

    companion object {
        const val QUERY_MIN_LENGTH = 3
    }
}
