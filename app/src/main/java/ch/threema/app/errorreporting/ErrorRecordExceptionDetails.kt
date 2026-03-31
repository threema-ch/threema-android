package ch.threema.app.errorreporting

import kotlinx.serialization.Serializable

@Serializable
data class ErrorRecordExceptionDetails(
    val type: String,
    val message: String?,
    val packageName: String?,
    val stackTrace: List<ErrorRecordStackTraceElement>,
)
