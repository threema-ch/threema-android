package ch.threema.localcrypto.exceptions

/**
 * Thrown when we check for remote secrets on the server but the server reports that
 * the user's remote secret has been blocked or deleted, meaning that the user is no longer allowed
 * to use the app and should not be able to access its contents.
 */
class BlockedByAdminException : Exception()
