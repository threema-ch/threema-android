package ch.threema.storage

class DatabaseUpdateException(val failedDatabaseUpdateVersion: Int) : Exception()
