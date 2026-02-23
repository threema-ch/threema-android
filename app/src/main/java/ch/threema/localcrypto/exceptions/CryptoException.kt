package ch.threema.localcrypto.exceptions

/**
 * Thrown when a cryptographic operation, e.g. decrypting the master key with a passphrase, fails.
 * This likely means that the provided secret/passphrase was incorrect.
 */
class CryptoException(cause: Throwable? = null) : Exception(cause)
