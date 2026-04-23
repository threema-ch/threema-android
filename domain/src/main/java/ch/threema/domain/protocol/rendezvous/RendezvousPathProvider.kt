package ch.threema.domain.protocol.rendezvous

import ch.threema.protobuf.d2d.rendezvous.RendezvousInit

internal fun interface RendezvousPathProvider {
    fun getPaths(rendezvousInit: RendezvousInit): Map<UInt, RendezvousPath>
}
