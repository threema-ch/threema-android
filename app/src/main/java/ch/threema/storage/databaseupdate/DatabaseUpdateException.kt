package ch.threema.storage.databaseupdate

/**
 * Thrown when a database migration cannot be completed.
 */
class DatabaseUpdateException(message: String) : Exception(message)
