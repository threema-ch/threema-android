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

package ch.threema.domain.protocol.csp.fs

import ch.threema.domain.fs.DHSession
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.protobuf.csp.e2e.fs.VersionRange
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic

fun mockMessageBody(message: AbstractMessage, value: ByteArray): AbstractMessage {
    mockkObject(message)
    every { message.body } returns value
    return message
}

/**
 * Replaces the return value of [DHSession.getSupportedVersionRange] with the given range. Note that
 * this has only an impact on the initial handshake. A client with a restricted supported
 * version range still announces the latest minor version.
 * Also, this method sets the version range globally, so that both Alice and Bob are affected.
 *
 * @param versionRange the new supported version range
 */
fun setSupportedVersionRange(versionRange: VersionRange) {
    mockkStatic(DHSession::class)
    every { DHSession.getSupportedVersionRange() } returns versionRange
}
