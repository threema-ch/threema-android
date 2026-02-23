package ch.threema.localcrypto.exceptions

import ch.threema.base.ThreemaException

/**
 * Thrown when the master key is accessed when it is locked, or when an operation is attempted that requires the master key to be unlocked
 * (e.g. changing the passphrase) while it is actually locked.
 */
class MasterKeyLockedException : ThreemaException("Master key is locked")
