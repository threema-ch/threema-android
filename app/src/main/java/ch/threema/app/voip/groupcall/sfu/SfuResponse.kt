package ch.threema.app.voip.groupcall.sfu

const val HTTP_STATUS_OK = 200
const val HTTP_STATUS_DATA_INVALID = 400
const val HTTP_STATUS_TOKEN_INVALID = 401
const val HTTP_STATUS_NO_SUCH_CALL = 404
const val HTTP_STATUS_UNSUPPORTED_PROTOCOL_VERSION = 419
const val HTTP_STATUS_SFU_NOT_AVAILABLE = 502
const val HTTP_STATUS_CALL_FULL = 503

data class SfuResponse<T>(
    val statusCode: Int,
    val body: T?,
) {
    val isHttpOk: Boolean
        get() = statusCode == HTTP_STATUS_OK

    val isHttpNotFound: Boolean
        get() = statusCode == HTTP_STATUS_NO_SUCH_CALL
}
