package ch.threema.domain.protocol.urls

import ch.threema.common.emptyByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediatorUrlTest {
    @Test
    fun `trailing slash is required`() {
        assertFailsWith<IllegalArgumentException> {
            MediatorUrl("wss://mediator-{deviceGroupIdPrefix4}.threema.ch/{deviceGroupIdPrefix8}")
        }
    }

    @Test
    fun `placeholders are replaced`() {
        val mediatorUrl = MediatorUrl("wss://mediator-{deviceGroupIdPrefix4}.threema.ch/{deviceGroupIdPrefix8}/")

        val url = mediatorUrl.get(deviceGroupId = byteArrayOf(0xDC.toByte()))

        assertEquals("wss://mediator-d.threema.ch/dc/", url)
    }

    @Test
    fun `empty device group id is considered invalid`() {
        val mediatorUrl = MediatorUrl("wss://mediator-{deviceGroupIdPrefix4}.threema.ch/{deviceGroupIdPrefix8}/")

        assertFailsWith<IllegalArgumentException> {
            mediatorUrl.get(deviceGroupId = emptyByteArray())
        }
    }
}
