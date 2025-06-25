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

package ch.threema.app.services

import ch.threema.domain.taskmanager.TriggerSource

/**
 * This service manages the identity exclusion list for the address book synchronization. Identities that are excluded won't be added when the address
 * book synchronisation is run.
 */
interface ExcludedSyncIdentitiesService {

    /**
     * Adds the [identity] to the exclusion list. Depending on the [triggerSource], the change is reflected.
     */
    fun excludeFromSync(identity: String, triggerSource: TriggerSource)

    /**
     * Removes the [identity] from the exclusion list, so that it will be synced again. Depending on the [triggerSource], the change is reflected.
     */
    fun removeExcludedIdentity(identity: String, triggerSource: TriggerSource)

    /**
     * Replace all existing excluded identities with the [identities].
     */
    fun setExcludedIdentities(identities: Set<String>, triggerSource: TriggerSource)

    /**
     * Get all excluded identities.
     */
    fun getExcludedIdentities(): Set<String>

    /**
     * Check whether the [identity] is excluded from sync or not.
     */
    fun isExcluded(identity: String): Boolean
}
