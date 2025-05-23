/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D

private val logger = LoggingUtil.getThreemaLogger("ReflectedOutgoingGroupSyncRequestTask")

internal class ReflectedOutgoingGroupSyncRequestTask(
    message: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask(
    message,
    Common.CspE2eMessageType.GROUP_SYNC_REQUEST,
    serviceManager,
) {
    override val shouldBumpLastUpdate: Boolean
        get() = groupSyncRequestMessage.bumpLastUpdate()

    override val storeNonces: Boolean
        get() = groupSyncRequestMessage.protectAgainstReplay()

    private val groupSyncRequestMessage: GroupSyncRequestMessage by lazy {
        GroupSyncRequestMessage.fromReflected(
            message,
        )
    }

    override fun processOutgoingMessage() {
        logger.info("Discarding reflected outgoing group sync request message")
    }
}
