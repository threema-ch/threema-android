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

package ch.threema.app.stores

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.Identity

class IdentityProviderImpl(
    private val preferenceStore: PreferenceStore,
) : MutableIdentityProvider {

    private var myIdentity: Identity? = null
        set(value) {
            require(value == null || value.length == ProtocolDefines.IDENTITY_LEN)
            field = value
        }

    init {
        myIdentity = preferenceStore.getString(PreferenceStore.PREFS_IDENTITY)
    }

    override fun getIdentity() = myIdentity

    override fun setIdentity(identity: Identity?) {
        myIdentity = identity
        if (identity != null) {
            preferenceStore.save(PreferenceStore.PREFS_IDENTITY, identity)
        } else {
            preferenceStore.remove(PreferenceStore.PREFS_IDENTITY)
        }
    }
}
