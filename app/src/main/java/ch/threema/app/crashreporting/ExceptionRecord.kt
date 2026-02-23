package ch.threema.app.crashreporting

import ch.threema.common.serializers.InstantSerializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ExceptionRecord(
    val id: String,
    val stackTrace: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)
