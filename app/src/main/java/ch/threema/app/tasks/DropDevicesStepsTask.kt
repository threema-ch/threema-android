/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.unlinking.DropDeviceResult
import ch.threema.app.multidevice.unlinking.DropDevicesIntent
import ch.threema.app.multidevice.unlinking.runDropDevicesSteps
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.DropOnDisconnectTask

/**
 * Runs the drop devices steps with the given [intent].
 */
class DropDevicesStepsTask(
    private val intent: DropDevicesIntent,
    private val serviceManager: ServiceManager,
) : DropOnDisconnectTask<DropDeviceResult> {
    override val type: String = "DropDevicesStepsTask"

    override suspend fun invoke(handle: ActiveTaskCodec): DropDeviceResult = runDropDevicesSteps(
        intent = intent,
        serviceManager = serviceManager,
        handle = handle,
    )
}
