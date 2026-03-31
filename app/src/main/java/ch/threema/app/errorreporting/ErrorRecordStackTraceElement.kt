package ch.threema.app.errorreporting

import kotlinx.serialization.Serializable

@Serializable
data class ErrorRecordStackTraceElement(
    val fileName: String?,
    val className: String?,
    val lineNumber: Int,
    val methodName: String?,
    val isNative: Boolean,
) {
    val isInApp: Boolean
        get() = className?.startsWith("ch.threema") == true
}
