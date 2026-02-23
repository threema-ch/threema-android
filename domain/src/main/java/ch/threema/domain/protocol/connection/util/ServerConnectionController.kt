package ch.threema.domain.protocol.connection.util

import ch.threema.domain.protocol.connection.ServerConnectionDispatcher
import ch.threema.domain.protocol.connection.csp.CspSession
import ch.threema.domain.protocol.connection.csp.CspSessionState
import ch.threema.domain.protocol.connection.d2m.D2mSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal interface ServerConnectionControllers {
    val serverConnectionController: ServerConnectionController
    val mainController: MainConnectionController
    val layer3Controller: Layer3Controller
    val layer4Controller: Layer4Controller
}

/**
 * A controller that is valid during one "connection cycle".
 *
 * The controller will be re-instantiated on every connection attempt.
 *
 * Therefore the controller can only contain connection state that is valid during a single connection
 * cycle.
 */
internal interface ServerConnectionController {
    val connected: Deferred<Unit>
    val cspSessionState: CspSessionState
    val cspAuthenticated: Deferred<Unit>
    val connectionClosed: Deferred<Unit>
    val ioProcessingStoppedSignal: CompletableDeferred<Unit>
    val dispatcher: ServerConnectionDispatcher
}

internal interface MdServerConnectionController : ServerConnectionController

internal interface MainConnectionController : ServerConnectionController {
    override val connected: CompletableDeferred<Unit>
    override val connectionClosed: CompletableDeferred<Unit>
    override val dispatcher: ServerConnectionDispatcher
}

internal interface Layer3Controller : ServerConnectionController {
    override val cspAuthenticated: CompletableDeferred<Unit>
    val cspSession: CspSession
}

/**
 * The [Layer3Controller] that must be used when multi device is activated.
 * With multi device active, both the [CspSession] AND the [D2mSession] are required.
 */
internal interface MdLayer3Controller : Layer3Controller {
    val d2mSession: D2mSession
}

internal interface Layer4Controller : ServerConnectionController

internal interface MdLayer4Controller : Layer4Controller {
    val reflectionQueueDry: CompletableDeferred<Unit>
}
