/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingGroupJoinRequestTask(
    message: GroupJoinRequestMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupJoinRequestMessage>(message, triggerSource, serviceManager) {
    private val incomingGroupJoinRequestService = serviceManager.incomingGroupJoinRequestService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        return if (incomingGroupJoinRequestService.process(message)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        // TODO(ANDR-2741): Support group synchronization
        return ReceiveStepsResult.DISCARD
    }
}
