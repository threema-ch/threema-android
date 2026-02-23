package ch.threema.domain.protocol.rendezvous

import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.join.MdD2DJoin
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

    data class BlobData(val data: Common.BlobData) : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                blobData = data
            }.toByteArray()
    }

    data class EssentialData(val data: MdD2DJoin.EssentialData) : DeviceJoinMessage {
        override val bytes: ByteArray
            get() = edToNd {
                essentialData = data
            }.toByteArray()
    }
}
