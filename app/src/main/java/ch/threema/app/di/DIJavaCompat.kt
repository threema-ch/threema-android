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

package ch.threema.app.di

import android.content.Context
import ch.threema.localcrypto.MasterKeyManager
import org.koin.mp.KoinPlatformTools

/**
 * Provides convenient access to dependencies in the singletons scope from Java.
 */
object DIJavaCompat {
    @JvmStatic
    fun getAppContext(): Context =
        getKoin().get()

    @JvmStatic
    fun getMasterKeyManager(): MasterKeyManager =
        getKoin().get()

    @JvmStatic
    fun isSessionScopeReady(): Boolean =
        getKoin().isSessionScopeReady()

    private fun getKoin() =
        KoinPlatformTools.defaultContext().get()
}
