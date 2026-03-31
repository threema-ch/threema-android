package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.domain.protocol.urls.MediatorUrl
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class D2mServerAddressProviderTest {

    @Test
    fun `get address`() {
        val provider = D2mServerAddressProvider(
            serverAddressProvider = mockk {
                every { getMediatorUrl() } returns MediatorUrl("wss://mediator-{deviceGroupIdPrefix4}.threema.ch/{deviceGroupIdPrefix8}/")
            },
            dgid = byteArrayOf(0xfe.toByte()),
            serverGroup = "abcdefgh",
        )
        assertEquals(
            "wss://mediator-f.threema.ch/fe/0a01fe1a086162636465666768",
            provider.get(),
        )
    }
}
