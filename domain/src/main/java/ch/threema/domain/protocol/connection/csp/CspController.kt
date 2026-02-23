package ch.threema.domain.protocol.connection.csp

import ch.threema.domain.protocol.connection.SingleThreadedServerConnectionDispatcher
import ch.threema.domain.protocol.connection.util.Layer3Controller
import ch.threema.domain.protocol.connection.util.Layer4Controller
import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import kotlinx.coroutines.CompletableDeferred

internal class CspController(configuration: CspConnectionConfiguration) :
    ServerConnectionController,
    Layer3Controller,
    Layer4Controller,
    MainConnectionController {
    override val dispatcher = SingleThreadedServerConnectionDispatcher(
        configuration.assertDispatcherContext,
    )

    override val cspSession: CspSession = CspSession(configuration, dispatcher)

    override val cspSessionState: CspSessionState = cspSession

    override val connectionClosed = CompletableDeferred<Unit>()

    override val connected = CompletableDeferred<Unit>()

    override val cspAuthenticated = CompletableDeferred<Unit>()

    override val ioProcessingStoppedSignal = CompletableDeferred<Unit>()
}
