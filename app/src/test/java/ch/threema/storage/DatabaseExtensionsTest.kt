package ch.threema.storage

import ch.threema.common.toHexString
import ch.threema.localcrypto.MasterKeyImpl
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import kotlin.test.Test
import kotlin.test.assertContentEquals

class DatabaseExtensionsTest {
    @Test
    fun `derive database password from master key`() {
        val masterKey = MasterKeyImpl(MASTER_KEY)

        assertContentEquals(
            "x\"${masterKey.value.toHexString()}\"".toByteArray(),
            masterKey.deriveDatabasePassword(),
        )
    }
}
