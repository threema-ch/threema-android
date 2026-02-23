package ch.threema.localcrypto

import ch.threema.common.generateRandomBytes
import ch.threema.localcrypto.models.MasterKeyData
import java.security.SecureRandom

class MasterKeyGenerator(
    private val random: SecureRandom,
) {
    fun generate(): MasterKeyData = MasterKeyData(
        random.generateRandomBytes(MasterKeyConfig.KEY_LENGTH),
    )
}
