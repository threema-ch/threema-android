package ch.threema.domain.protocol.rendezvous

import ch.threema.protobuf.d2d.rendezvous.MdD2DRendezvous

internal fun interface RendezvousPathProvider {
    fun getPaths(rendezvousInit: MdD2DRendezvous.RendezvousInit): Map<UInt, RendezvousPath>
}
