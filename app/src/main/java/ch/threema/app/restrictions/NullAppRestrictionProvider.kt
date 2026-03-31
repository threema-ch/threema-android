package ch.threema.app.restrictions

class NullAppRestrictionProvider : AppRestrictionProvider {
    override fun getBooleanRestriction(restriction: String) = null

    override fun getStringRestriction(restriction: String) = null

    override fun getIntRestriction(restriction: String) = null
}
