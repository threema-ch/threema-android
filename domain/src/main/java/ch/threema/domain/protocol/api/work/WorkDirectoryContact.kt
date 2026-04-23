package ch.threema.domain.protocol.api.work

import java.time.Instant

class WorkDirectoryContact(
    threemaId: String,
    publicKey: ByteArray,
    firstName: String?,
    lastName: String?,
    jobTitle: String?,
    department: String?,
    availability: String?,
    workLastFullSyncAt: Instant?,
    @JvmField val csi: String?,
) : WorkContact(
    threemaId = threemaId,
    publicKey = publicKey,
    firstName = firstName,
    lastName = lastName,
    jobTitle = jobTitle,
    department = department,
    availability = availability,
    workLastFullSyncAt = workLastFullSyncAt,
) {
    @JvmField
    val categoryIds: MutableList<String?> = ArrayList()

    @JvmField
    val organization: WorkOrganization = WorkOrganization()

    fun getInitial(sortByFirstName: Boolean): String {
        val name = if (sortByFirstName) {
            (if (firstName != null) "$firstName " else "") + (lastName ?: "")
        } else {
            (if (lastName != null) "$lastName " else "") + (firstName ?: "")
        }
        if (!name.isEmpty()) {
            return name.substring(0, 1)
        }
        return " "
    }

    override fun toString(): String =
        "WorkDirectoryContact(threemaId=$threemaId, publicKey=${publicKey.contentToString()}, firstName=$firstName, lastName=$lastName, " +
            "jobTitle=$jobTitle, department=$department, availability=$availability, csi=$csi, workLastFullSyncAt=$workLastFullSyncAt)"
}
