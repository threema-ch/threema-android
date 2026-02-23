package ch.threema.storage

class DatabaseDowngradeException(val oldDatabaseVersion: Int) : Exception()
