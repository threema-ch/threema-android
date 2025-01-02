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

package ch.threema.app.processors.incomingcspmessage.fs

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate
import ch.threema.domain.taskmanager.ActiveTaskCodec

private val logger = LoggingUtil.getThreemaLogger("IncomingForwardSecurityTerminateTask")

class IncomingForwardSecurityTerminateTask(
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    private val contact: Contact,
    private val data: ForwardSecurityDataTerminate,
) : IncomingForwardSecurityEnvelopeTask {
    override suspend fun run(handle: ActiveTaskCodec): ForwardSecurityDecryptionResult {
        logger.info("Received forward security terminate message")
        // TODO(ANDR-2519): Remove when md allows fs
        // Note that in this case we should not send a terminate if we do not support fs. Sending a
        // terminate could trigger the sender to respond with a terminate again.
        if (!forwardSecurityMessageProcessor.canForwardSecurityMessageBeProcessed(
                contact, data.sessionId, false, handle
            )
        ) {
            return ForwardSecurityDecryptionResult.NONE
        }

        forwardSecurityMessageProcessor.processTerminate(contact, data)
        return ForwardSecurityDecryptionResult.NONE
    }
}
