/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.systemupdates.updates

import ch.threema.data.datatypes.IdColor
import ch.threema.data.repositories.ContactModelRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * For ID colors we store the first byte of the SHA-256 hash of the contact identity.
 */
class SystemUpdateToVersion72() : SystemUpdate, KoinComponent {
    private val contactModelRepository: ContactModelRepository by inject()

    override fun run() {
        contactModelRepository.getAll().forEach { contactModel ->
            contactModel.setIdColor(IdColor.ofIdentity(contactModel.identity))
        }
    }

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 72
    }
}
