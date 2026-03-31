package ch.threema.app.androidcontactsync.read

sealed class LookupInfoException : Throwable() {
    /**
     * The permission to read contacts is missing.
     */
    class MissingPermission(override val cause: Throwable? = null) : LookupInfoException()

    /**
     * The cursor could not be created.
     */
    class CursorCreateException(override val message: String?) : LookupInfoException()

    /**
     * Another error happened while getting the lookup info.
     */
    class Other(override val cause: Throwable? = null) : LookupInfoException()
}
