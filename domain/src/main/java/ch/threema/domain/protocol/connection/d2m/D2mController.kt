package ch.threema.domain.protocol.connection.d2m

import ch.threema.domain.protocol.connection.SingleThreadedServerConnectionDispatcher
import ch.threema.domain.protocol.connection.csp.CspSession
import ch.threema.domain.protocol.connection.csp.CspSessionState
import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.protocol.connection.util.MdLayer3Controller
import ch.threema.domain.protocol.connection.util.MdLayer4Controller
import ch.threema.domain.protocol.connection.util.MdServerConnectionController
import kotlinx.coroutines.CompletableDeferred

internal class D2mController(configuration: D2mConnectionConfiguration) :
    MdServerConnectionController,
    MdLayer3Controller,
    MdLayer4Controller,
    MainConnectionController {
    override val dispatcher = SingleThreadedServerConnectionDispatcher(
        configuration.assertDispatcherContext,
    )

    override val cspSession: CspSession = CspSession(configuration, dispatcher)
    override val d2mSession: D2mSession = D2mSession(configuration, dispatcher)

    override val cspSessionState: CspSessionState = cspSession

    override val connectionClosed = CompletableDeferred<Unit>()

    override val connected = CompletableDeferred<Unit>()

    override val cspAuthenticated = CompletableDeferred<Unit>()

    override val ioProcessingStoppedSignal = CompletableDeferred<Unit>()

    override val reflectionQueueDry = CompletableDeferred<Unit>()
}
