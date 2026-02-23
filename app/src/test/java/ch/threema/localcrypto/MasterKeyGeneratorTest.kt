package ch.threema.localcrypto

import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.models.MasterKeyData
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class MasterKeyGeneratorTest {
    @Test
    fun `generate a random key`() {
        val masterKeyGenerator = MasterKeyGenerator(
            random = mockk {
                every { nextBytes(any()) } answers { MASTER_KEY.copyInto(firstArg<ByteArray>()) }
            },
        )

        val masterKeyData = masterKeyGenerator.generate()

        assertEquals(MasterKeyData(MASTER_KEY), masterKeyData)
    }
}
