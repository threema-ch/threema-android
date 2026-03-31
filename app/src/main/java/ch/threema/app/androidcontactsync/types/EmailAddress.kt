package ch.threema.app.androidcontactsync.types

@JvmInline
value class EmailAddress(val emailAddress: String) {
    companion object {
        fun fromString(value: String): EmailAddress? =
            if (value.isNotBlank()) {
                EmailAddress(value)
            } else {
                null
            }
    }
}
