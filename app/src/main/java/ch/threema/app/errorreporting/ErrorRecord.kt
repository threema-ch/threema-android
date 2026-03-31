package ch.threema.app.errorreporting

import ch.threema.common.serializers.InstantSerializer
import ch.threema.common.serializers.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class ErrorRecord(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val exceptions: List<ErrorRecordExceptionDetails>,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)
