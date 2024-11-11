/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.data.models

import kotlinx.coroutines.flow.MutableStateFlow

class EditHistoryListModel(data: List<EditHistoryEntryData>) : BaseModel<List<EditHistoryEntryData>>(
    modelName = "EditHistoryListModel",
    mutableData = MutableStateFlow(data)
) {
    fun addEntry(entry: EditHistoryEntryData) {
        if (mutableData.value?.none { it == entry } == true) {
            mutableData.value = mutableData.value?.toMutableList()?.apply { add(0, entry) }
        }
    }

    fun clear() {
        mutableData.value = emptyList()
    }
}
