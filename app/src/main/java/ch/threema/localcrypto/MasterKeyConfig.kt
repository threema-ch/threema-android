package ch.threema.localcrypto

object MasterKeyConfig {
    const val KEY_LENGTH = 32

    const val ARGON2_MEMORY_BYTES = 128 * 1024
    const val ARGON2_ITERATIONS = 8
    const val ARGON2_PARALLELIZATION = 1
    const val ARGON2_SALT_LENGTH = 16

    const val SECRET_KEY_LENGTH = 32
    const val NONCE_LENGTH = 24

    const val REMOTE_SECRET_HASH_LENGTH = 32
    const val REMOTE_SECRET_AUTH_TOKEN_LENGTH = 32

    const val VERSION1_VERIFICATION_LENGTH = 4
}
