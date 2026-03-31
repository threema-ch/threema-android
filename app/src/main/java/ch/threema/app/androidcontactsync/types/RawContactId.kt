package ch.threema.app.androidcontactsync.types

@JvmInline
value class RawContactId(val id: ULong) : Comparable<RawContactId> {
    override fun compareTo(other: RawContactId) =
        id.compareTo(other.id)

    companion object {
        fun fromLong(rawContactId: Long): RawContactId? =
            if (rawContactId > 0) {
                RawContactId(rawContactId.toULong())
            } else {
                null
            }
    }
}
