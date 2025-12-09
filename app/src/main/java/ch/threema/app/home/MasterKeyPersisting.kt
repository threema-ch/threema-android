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

package ch.threema.app.home

import androidx.lifecycle.lifecycleScope
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("MasterKeyPersisting")

class MasterKeyPersisting(
    private val masterKeyManager: MasterKeyManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun persistMasterKeyIfNeeded(activity: HomeActivity, onError: Runnable) {
        activity.lifecycleScope.launch(dispatcherProvider.io) {
            try {
                masterKeyManager.persistKeyDataIfNeeded()
            } catch (e: Exception) {
                logger.error("Failed to persist master key", e)
                withContext(dispatcherProvider.main) {
                    onError.run()
                }
            }
        }
    }
}
