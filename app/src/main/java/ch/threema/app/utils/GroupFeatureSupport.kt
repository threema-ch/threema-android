/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.utils

import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.storage.models.ContactModel

class GroupFeatureSupport(
    @ThreemaFeature.Feature feature: Long,
    groupMembers: List<ContactModel>,
) {
    @SuppressWarnings("WeakerAccess")
    val contactsWithFeatureSupport: List<ContactModel>
    val contactsWithoutFeatureSupport: List<ContactModel>

    init {
        groupMembers.groupBy { ThreemaFeature.hasFeature(it.featureMask, feature) }.also {
            contactsWithFeatureSupport = it[true] ?: emptyList()
            contactsWithoutFeatureSupport = it[false] ?: emptyList()
        }
    }

    val adoptionRate: GroupFeatureAdoptionRate =
        // deduct adoption rate
        if (contactsWithFeatureSupport.isNotEmpty() && contactsWithoutFeatureSupport.isNotEmpty()) {
            GroupFeatureAdoptionRate.PARTIAL
        } else if (contactsWithFeatureSupport.isNotEmpty()) {
            // implies that contactsWithoutFeatureSupport is empty
            GroupFeatureAdoptionRate.ALL
        } else {
            // implies that there are no contacts with feature support
            GroupFeatureAdoptionRate.NONE
        }
}

enum class GroupFeatureAdoptionRate {
    /**
     * no group member supports a feature
     */
    NONE,

    /**
     * not all group members support a feature
     */
    PARTIAL,

    /**
     * all group members support a feature
     */
    ALL,
}
