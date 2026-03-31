package ch.threema.app.androidcontactsync.types

@JvmInline
value class PhoneNumber(val phoneNumber: String) {
    companion object {
        fun fromString(value: String): PhoneNumber? =
            if (value.isNotBlank()) {
                PhoneNumber(value)
            } else {
                null
            }
    }
}
