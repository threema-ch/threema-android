package ch.threema.domain.protocol.rendezvous

import ch.threema.protobuf.d2d.join.begin
import ch.threema.protobuf.d2d.join.edToNd
import ch.threema.protobuf.d2d.join.ndToEd
import ch.threema.protobuf.d2d.join.registered

sealed interface DeviceJoinMessage {
    val bytes: ByteArray

    @Suppress("CanSealedSubClassBeObject")
    class Begin : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                begin = begin {}
            }.toByteArray()
    }

    @Suppress("CanSealedSubClassBeObject")
    class Registered : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = ndToEd {
                registered = registered {}
            }.toByteArray()
    }

    data class BlobData(val data: ch.threema.protobuf.common.BlobData) : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                blobData = data
            }.toByteArray()
    }

    data class EssentialData(val data: ch.threema.protobuf.d2d.join.EssentialData) : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                essentialData = data
            }.toByteArray()
    }
}
