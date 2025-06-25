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

package ch.threema.app.workers

object WorkerNames {
    const val WORKER_CONTACT_UPDATE_PERIODIC_NAME = "PeriodicContactUpdate"
    const val WORKER_WORK_SYNC = "WorkSync"
    const val WORKER_PERIODIC_WORK_SYNC = "PeriodicWorkSync"
    const val WORKER_THREEMA_SAFE_UPLOAD = "SafeUpload"
    const val WORKER_PERIODIC_THREEMA_SAFE_UPLOAD = "PeriodicSafeUpload"
    const val WORKER_CONNECTIVITY_CHANGE = "ConnectivityChange"
    const val WORKER_AUTO_DELETE = "AutoDelete"
    const val WORKER_AUTOSTART = "Autostart"
}
