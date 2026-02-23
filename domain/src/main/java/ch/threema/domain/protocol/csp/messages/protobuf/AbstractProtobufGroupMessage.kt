package ch.threema.domain.protocol.csp.messages.protobuf

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.nio.charset.StandardCharsets

private val logger = getThreemaLogger("AbstractProtobufGroupMessage")

/**
 * @param type Protocol type of the message as defined in [ch.threema.domain.protocol.csp.ProtocolDefines]
 * @param data Parsed protobuf data
 */
abstract class AbstractProtobufGroupMessage<D : ProtobufDataInterface<*>?>(
    private val type: Int,
    val data: D,
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
            null
        }
    }

    override fun getType(): Int = type
}
