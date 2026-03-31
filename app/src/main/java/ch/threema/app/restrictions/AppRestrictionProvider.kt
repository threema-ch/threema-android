package ch.threema.app.restrictions

interface AppRestrictionProvider {
    fun getBooleanRestriction(restriction: String): Boolean?

    fun getStringRestriction(restriction: String): String?

    fun getIntRestriction(restriction: String): Int?
}
