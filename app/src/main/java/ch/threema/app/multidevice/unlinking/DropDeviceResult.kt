/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.multidevice.unlinking

import ch.threema.app.multidevice.LinkedDevice

sealed interface DropDeviceResult {
    /**
     *  @param remainingLinkedDevicesResult A list containing all remaining linked devices in the current device group **excluding** our own.
     */
    data class Success(
        val remainingLinkedDevicesResult: Result<List<LinkedDevice>>,
    ) : DropDeviceResult

    sealed interface Failure : DropDeviceResult {
        data object Internal : Failure

        data object Timeout : Failure
    }
}
