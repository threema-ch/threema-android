package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.common.toHexString
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.protobuf.d2m.ClientUrlInfo
import com.google.protobuf.kotlin.toByteString

class D2mServerAddressProvider(
    private val serverAddressProvider: ServerAddressProvider,
    private val dgid: ByteArray,
    private val serverGroup: String,
) {
    fun get(): String {
        val mediatorUrl = serverAddressProvider.getMediatorUrl().get(dgid)

        val clientUrlInfo = ClientUrlInfo.newBuilder()
            .setDeviceGroupId(dgid.toByteString())
            .setServerGroup(serverGroup)
            .build()
            .toByteArray()
            .toHexString()

        // mediatorUrl is guaranteed to end with a `/` character
        return mediatorUrl + clientUrlInfo
    }
}
