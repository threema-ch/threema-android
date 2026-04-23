package ch.threema.domain.protocol.api.work

import java.time.Instant

open class WorkContact(
    @JvmField val threemaId: String,
    @JvmField val publicKey: ByteArray,
    @JvmField val firstName: String?,
    @JvmField val lastName: String?,
    @JvmField val jobTitle: String?,
    @JvmField val department: String?,
    @JvmField val availability: String?,
    @JvmField val workLastFullSyncAt: Instant?,
) {
    override fun toString(): String =
        "WorkContact(threemaId=$threemaId, publicKey=${publicKey.contentToString()}, firstName=$firstName, lastName=$lastName, " +
            "jobTitle=$jobTitle, department=$department, availability=$availability, workLastFullSyncAt=$workLastFullSyncAt)"
}
