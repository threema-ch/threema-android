package ch.threema.domain.protocol.api

internal sealed class HttpRequesterResult {
    data class Success(@JvmField val responseBody: String) : HttpRequesterResult()

    data class Error(@JvmField val responseCode: Int) : HttpRequesterResult()
}
