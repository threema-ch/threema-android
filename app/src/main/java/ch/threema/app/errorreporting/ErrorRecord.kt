package ch.threema.app.errorreporting

import ch.threema.common.serializers.InstantSerializer
import ch.threema.common.serializers.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorRecord(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val level: ErrorRecordLevel = ErrorRecordLevel.FATAL,
    @SerialName("exceptions")
    val exceptionDetails: List<ErrorRecordExceptionDetails>? = null,
    val message: ErrorRecordMessage? = null,
    val attachments: List<ErrorRecordAttachment>? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val versionCode: Int,
)
