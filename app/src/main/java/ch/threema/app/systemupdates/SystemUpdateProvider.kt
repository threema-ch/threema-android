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

package ch.threema.app.systemupdates

import android.content.Context
import ch.threema.app.managers.ServiceManager
import ch.threema.app.systemupdates.updates.*

class SystemUpdateProvider(
    private val context: Context,
    private val serviceManager: ServiceManager,
) {
    fun getUpdates(oldVersion: Int): List<SystemUpdate> = buildList {
        if (oldVersion < 12) {
            add(SystemUpdateToVersion12(serviceManager))
        }
        if (oldVersion < 14) {
            add(SystemUpdateToVersion14(serviceManager))
        }
        if (oldVersion < 31) {
            add(SystemUpdateToVersion31(context))
        }
        if (oldVersion < 39) {
            add(SystemUpdateToVersion39(serviceManager))
        }
        if (oldVersion < 40) {
            add(SystemUpdateToVersion40(serviceManager))
        }
        if (oldVersion < 42) {
            add(SystemUpdateToVersion42(context, serviceManager))
        }
        if (oldVersion < 43) {
            add(SystemUpdateToVersion43(serviceManager))
        }
        if (oldVersion < 46) {
            add(SystemUpdateToVersion46(serviceManager))
        }
        if (oldVersion < 48) {
            add(SystemUpdateToVersion48(context))
        }
        if (oldVersion < 53) {
            add(SystemUpdateToVersion53(context))
        }
        if (oldVersion < 54) {
            add(SystemUpdateToVersion54(context))
        }
        if (oldVersion < 55) {
            add(SystemUpdateToVersion55())
        }
        if (oldVersion < 63) {
            add(SystemUpdateToVersion63(context))
        }
        if (oldVersion < 64) {
            add(SystemUpdateToVersion64(context))
        }
        if (oldVersion < SystemUpdateToVersion66.VERSION) {
            add(SystemUpdateToVersion66(context, serviceManager))
        }
        if (oldVersion < SystemUpdateToVersion72.VERSION) {
            add(SystemUpdateToVersion72(serviceManager))
        }
        if (oldVersion < SystemUpdateToVersion91.VERSION) {
            add(SystemUpdateToVersion91(context))
        }
        if (oldVersion < SystemUpdateToVersion110.VERSION) {
            add(SystemUpdateToVersion110(serviceManager))
        }
        if (oldVersion < SystemUpdateToVersion111.VERSION) {
            add(SystemUpdateToVersion111(serviceManager))
        }
        if (oldVersion < SystemUpdateToVersion112.VERSION) {
            add(SystemUpdateToVersion112(serviceManager))
        }
        if (oldVersion < SystemUpdateToVersion113.VERSION) {
            add(SystemUpdateToVersion113(serviceManager))
        }
    }

    fun getVersion() = VERSION

    companion object {
        const val VERSION = SystemUpdateToVersion113.VERSION
    }
}
