package ch.threema.domain.protocol.connection.d2m

import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.protocol.connection.util.MdLayer3Controller
import ch.threema.domain.protocol.connection.util.MdLayer4Controller
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionControllers

internal class D2mControllers(d2mConnectionConfiguration: D2mConnectionConfiguration) :
    ServerConnectionControllers {
    private val d2mController = D2mController(d2mConnectionConfiguration)

    override val serverConnectionController: ServerConnectionController = d2mController
    override val mainController: MainConnectionController = d2mController
    override val layer3Controller: MdLayer3Controller = d2mController
    override val layer4Controller: MdLayer4Controller = d2mController
}
