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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.csp.e2e.fs.Version

class EmptyMessage : AbstractMessage() {

    override fun getType() = ProtocolDefines.MSGTYPE_EMPTY

    // We do never send empty messages like regular messages. Therefore, the version here is not
    // checked when sending an empty message. However, we set it to version 1.0, as we want to get
    // warned if such a message is being received without forward security. This warning is only
    // useful for finding bugs.
    override fun getMinimumRequiredForwardSecurityVersion() = Version.V1_0

    override fun allowUserProfileDistribution() = false

    override fun exemptFromBlocking() = true

    override fun createImplicitlyDirectContact() = false

    override fun protectAgainstReplay() = true

    override fun reflectIncoming() = false

    override fun reflectOutgoing() = false

    override fun reflectSentUpdate() = false

    override fun sendAutomaticDeliveryReceipt() = false

    override fun bumpLastUpdate() = false

    override fun getBody() = ByteArray(0)
}
