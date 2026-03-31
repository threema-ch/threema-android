package ch.threema.app.androidcontactsync.types

@JvmInline
value class ContactId(val id: ULong) {
    companion object {
        @JvmStatic
        fun fromLong(value: Long): ContactId? =
            if (value >= 0) {
                ContactId(value.toULong())
            } else {
                null
            }
    }
}
