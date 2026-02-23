package ch.threema.domain.protocol.connection.d2m

import ch.threema.base.utils.TimeMeasureUtil
import ch.threema.domain.protocol.connection.InputPipe
import ch.threema.domain.protocol.connection.ServerConnectionDispatcher
import ch.threema.domain.protocol.connection.data.D2mProtocolException
import ch.threema.domain.protocol.connection.data.DeviceSlotExpirationPolicy
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import kotlin.math.min

private val logger = ConnectionLoggingUtil.getConnectionLogger("D2mSession")

internal interface D2mSessionState {
    val isLoginDone: Boolean
}

internal class D2mSession(
    configuration: D2mConnectionConfiguration,
    private val dispatcher: ServerConnectionDispatcher,
) : D2mSessionState {
    private enum class LoginState {
        AWAIT_SERVER_HELLO,
        AWAIT_SERVER_INFO,
        DONE,
    }

    private val timeMeasureUtil = TimeMeasureUtil()

    private val propertiesProvider = configuration.multiDevicePropertyProvider

    private var loginState = LoginState.AWAIT_SERVER_HELLO

    override val isLoginDone: Boolean
        get() = loginState == LoginState.DONE

    fun handleHandshakeMessage(
        message: InboundD2mMessage,
        outbound: InputPipe<in OutboundD2mMessage, Unit>,
    ) {
        dispatcher.assertDispatcherContext()

        loginState = when (message) {
            is InboundD2mMessage.ServerHello -> {
                processServerHello(message, outbound)
                LoginState.AWAIT_SERVER_INFO
            }

            is InboundD2mMessage.ServerInfo -> {
                processServerInfo(message)
                LoginState.DONE
            }

            else -> throw getUnexpectedMessageException(message)
        }
    }

    private fun processServerHello(
        serverHello: InboundD2mMessage.ServerHello,
        outbound: InputPipe<in OutboundD2mMessage, Unit>,
    ) {
        if (loginState != LoginState.AWAIT_SERVER_HELLO) {
            throw getUnexpectedMessageException(serverHello)
        }

        val clientHello = createClientHello(serverHello)
        timeMeasureUtil.start()
        outbound.send(clientHello)
    }

    private fun createClientHello(
        serverHello: InboundD2mMessage.ServerHello,
    ): OutboundD2mMessage.ClientHello = withProperties { properties ->
        val serverVersion = serverHello.version
        val localVersionMin = properties.protocolVersion.min
        val localVersionMax = properties.protocolVersion.max
        logger.trace("Check if server version ($serverVersion) in $localVersionMin..$localVersionMax")
        if (serverVersion < localVersionMin || serverVersion > localVersionMax) {
            throw D2mProtocolException("Unsupported d2m protocol version: $serverVersion not in $localVersionMin..$localVersionMax")
        }

        OutboundD2mMessage.ClientHello(
            min(serverHello.version, properties.protocolVersion.max),
            properties.keys.createServerHelloResponse(serverHello),
            properties.mediatorDeviceId,
            OutboundD2mMessage.ClientHello.DeviceSlotsExhaustedPolicy.REJECT,
            DeviceSlotExpirationPolicy.PERSISTENT,
            properties.deviceSlotState,
            properties.keys.encryptDeviceInfo(properties.deviceInfo),
        ).also { logger.trace("{}", it) }
    }

    private fun processServerInfo(serverInfo: InboundD2mMessage.ServerInfo) {
        timeMeasureUtil.stop()
        if (loginState != LoginState.AWAIT_SERVER_INFO) {
            throw getUnexpectedMessageException(serverInfo)
        }
        logger.info("Server info received (rtt: {} ms)", timeMeasureUtil.elapsedTime)
        withProperties { it.notifyServerInfo(serverInfo) }
    }

    private fun <T> withProperties(block: (MultiDeviceProperties) -> T): T {
        return block.invoke(propertiesProvider.get())
    }

    private fun getUnexpectedMessageException(message: InboundD2mMessage): D2mProtocolException {
        return D2mProtocolException(
            "Unexpected message of type `${message.type}` in login state `$loginState`",
        )
    }
}
