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
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.protobuf.d2d.MdD2D

private val logger = getThreemaLogger("ReflectedOutgoingPlaceholderTask")

/**
 * This task is used for messages that do not require any steps to be executed. We use this generic placeholder task to prevent us from having to
 * parse the unused messages. Therefore we use the [PlaceholderMessage].
 */
internal class ReflectedOutgoingPlaceholderTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
    private val logMessage: String? = null,
) : ReflectedOutgoingContactMessageTask<AbstractMessage>(
    outgoingMessage = outgoingMessage,
    message = PlaceholderMessage,
    type = outgoingMessage.type,
    serviceManager = serviceManager,
) {
    override fun processOutgoingMessage() {
        logMessage?.let(logger::warn)
    }
}

/**
 * This placeholder message is used instead of parsing the reflected outgoing message that won't be used anyways.
 */
private object PlaceholderMessage : AbstractMessage() {
    override fun allowUserProfileDistribution() = false

    override fun exemptFromBlocking() = false

    override fun createImplicitlyDirectContact() = false

    override fun protectAgainstReplay() = false

    override fun reflectIncoming() = false

    override fun reflectOutgoing() = false

    override fun reflectSentUpdate() = false

    override fun sendAutomaticDeliveryReceipt() = false

    override fun bumpLastUpdate() = false

    override fun getType() = -1

    override fun getMinimumRequiredForwardSecurityVersion() = null

    override fun getBody() = byteArrayOf()
}
