package ch.threema.app.services

object ServicesConstants {
    /**
     * In some cases 'null' has been written as string to the preferences instead of the null value.
     * Therefore, this string must be treated as null in certain cases.
     */
    const val PREFERENCES_NULL = "null"
}
