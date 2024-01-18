/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.protobuf

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.nio.charset.StandardCharsets

private val logger = LoggingUtil.getThreemaLogger("AbstractProtobufGroupMessage")

/**
 * @param type Protocol type of the message as defined in [ch.threema.domain.protocol.csp.ProtocolDefines]
 * @param protobufData Parsed protobuf data
 */
abstract class AbstractProtobufGroupMessage<D : ProtobufDataInterface<*>?>(
    private val type: Int,
    val data: D
) : AbstractGroupMessage() {
    override fun getBody(): ByteArray? {
        return try {
            val bos = ByteArrayOutputStream()
            bos.write(groupCreator.toByteArray(StandardCharsets.US_ASCII))
            bos.write(apiGroupId.groupId)
            bos.write(data!!.toProtobufBytes())
            bos.toByteArray()
        } catch (e: Exception) {
            logger.error(e.message)
            byteArrayOf()
        }
    }

    override fun getType(): Int {
        return type
    }
}
