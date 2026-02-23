package ch.threema.domain.models

data class UserCredentials(
    @JvmField
    val username: String,
    @JvmField
    val password: String,
) : LicenseCredentials {
    override fun toString() = username
}
