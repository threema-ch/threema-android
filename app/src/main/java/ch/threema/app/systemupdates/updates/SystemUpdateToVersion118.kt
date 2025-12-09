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

package ch.threema.app.systemupdates.updates

import android.content.Context
import ch.threema.app.R
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * In the past, the credentials were stored in plain text. When read, they would be migrated to the encrypted storage and
 * removed from the plain text store. This migrate-upon-read logic no longer exists, therefore now this system update exists
 * to ensure that users, who may not have had their credentials migrated yet, won't lose them.
 */
class SystemUpdateToVersion118 : SystemUpdate, KoinComponent {

    private val appContext: Context by inject()
    private val preferenceStore: PreferenceStore by inject()
    private val encryptedPreferenceStore: EncryptedPreferenceStore by inject()

    override fun run() {
        KEY_IDS.forEach { keyId ->
            migrate(appContext.getString(keyId))
        }
    }

    private fun migrate(key: String) {
        if (encryptedPreferenceStore.containsKey(key)) {
            return
        }
        val value = preferenceStore.getString(key)
            ?: return
        encryptedPreferenceStore.save(key, value)
        preferenceStore.remove(key)
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "ensure credentials are encrypted"

    companion object {
        const val VERSION = 118

        private val KEY_IDS = arrayOf(
            R.string.preferences__license_username,
            R.string.preferences__license_password,
            R.string.preferences__onprem_server,
        )
    }
}
