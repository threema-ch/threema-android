package ch.threema.storage.models

class GroupCallModel internal constructor(
    val protocolVersion: Int,
    val callId: String,
    val groupId: Int,
    val sfuBaseUrl: String,
    val gck: String,
    val startedAt: Long,
    val processedAt: Long,
) {
    companion object {
        const val TABLE = "group_call"

        const val COLUMN_CALL_ID = "callId"
        const val COLUMN_GROUP_ID = "groupId"
        const val COLUMN_SFU_BASE_URL = "sfuBaseUrl"
        const val COLUMN_GCK = "gck"
        const val COLUMN_PROTOCOL_VERSION = "protocolVersion"
        const val COLUMN_STARTED_AT = "startedAt"
        const val COLUMN_PROCESSED_AT = "processedAt"
    }

    fun getProtocolVersionUnsigned(): UInt = protocolVersion.toUInt()

    fun getStartedAtUnsigned(): ULong = startedAt.toULong()

    fun getProcessedAtUnsigned(): ULong = processedAt.toULong()
}
