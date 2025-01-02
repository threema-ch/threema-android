/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.processors.reflectedoutgoingmessage.groupcall

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.reflectedoutgoingmessage.ReflectedOutgoingGroupMessageTask
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D

internal class ReflectedOutgoingGroupCallStartTask(
    message: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask(message, Common.CspE2eMessageType.GROUP_CALL_START, serviceManager) {

    private val groupCallManager = serviceManager.groupCallManager

    private val identityStore = serviceManager.identityStore

    private val groupCallStartMessage = GroupCallStartMessage.fromReflected(
        message = message,
        ownIdentity = identityStore.identity
    )

    override val storeNonces: Boolean = groupCallStartMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean = groupCallStartMessage.bumpLastUpdate()

    override fun processOutgoingMessage() {
        groupCallManager.handleControlMessage(groupCallStartMessage)
    }
}
