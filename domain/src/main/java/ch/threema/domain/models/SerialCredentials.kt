package ch.threema.domain.models

class SerialCredentials(
    @JvmField
    val licenseKey: String,
) : LicenseCredentials {
    override fun toString() = licenseKey
}
