package ch.threema.domain.protocol.urls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceGroupUrlTest {
    @Test
    fun `placeholders are replaced`() {
        val deviceGroupUrl = DeviceGroupUrl("https://blob-mirror-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")

        val url = deviceGroupUrl.get(deviceGroupId = byteArrayOf(0xDC.toByte()))

        assertEquals("https://blob-mirror-d.test.threema.ch/dc", url)
    }

    @Test
    fun `empty device group id is considered invalid`() {
        val deviceGroupUrl = DeviceGroupUrl("https://blob-mirror-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")

        assertFailsWith<IllegalArgumentException> {
            deviceGroupUrl.get(deviceGroupId = ByteArray(0))
        }
    }
}
