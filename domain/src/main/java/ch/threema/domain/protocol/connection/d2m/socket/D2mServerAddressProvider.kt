package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.common.toHexString
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.protobuf.d2m.MdD2M
import com.google.protobuf.ByteString

class D2mServerAddressProvider(
    private val serverAddressProvider: ServerAddressProvider,
    private val dgid: ByteArray,
    private val serverGroup: String,
) {
    fun get(): String {
        val mediatorUrl = serverAddressProvider.getMediatorUrl().get(dgid)

        val clientUrlInfo = MdD2M.ClientUrlInfo.newBuilder()
            .setDeviceGroupId(ByteString.copyFrom(dgid))
            .setServerGroup(serverGroup)
            .build()
            .toByteArray()
            .toHexString()

        return "$mediatorUrl/$clientUrlInfo"
    }
}
