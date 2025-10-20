/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.passphrase

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import ch.threema.app.utils.buildActivityIntent
import ch.threema.localcrypto.MasterKeyManager
import org.koin.android.ext.android.inject

/**
 * This activity is used to lock the master key with the currently configured passphrase.
 * It will be called from the persistent notification when the user wishes to lock the app.
 * When started, it will lock the master key.
 */
class PassphraseLockActivity : ComponentActivity() {

    private val masterKeyManager: MasterKeyManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (masterKeyManager.isProtectedWithPassphrase() && !masterKeyManager.isLockedWithPassphrase()) {
            masterKeyManager.lockWithPassphrase()
        }
        finishAndRemoveTask()
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<PassphraseLockActivity>(context)
    }
}
