package ch.threema.domain.protocol.connection.csp

import ch.threema.domain.protocol.connection.util.Layer3Controller
import ch.threema.domain.protocol.connection.util.Layer4Controller
import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionControllers

internal class CspControllers(cspConnectionConfiguration: CspConnectionConfiguration) :
    ServerConnectionControllers {
    private val cspController = CspController(cspConnectionConfiguration)

    override val serverConnectionController: ServerConnectionController = cspController
    override val mainController: MainConnectionController = cspController
    override val layer3Controller: Layer3Controller = cspController
    override val layer4Controller: Layer4Controller = cspController
}
