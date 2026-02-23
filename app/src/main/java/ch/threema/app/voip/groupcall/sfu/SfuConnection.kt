package ch.threema.app.voip.groupcall.sfu

import androidx.annotation.AnyThread
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.domain.protocol.api.SfuToken

interface SfuConnection {
    /**
     * Obtain a sfu token from the directory api.
     *
     * A token should be cached as long as it is valid according to its expiration date.
     *
     * @param forceRefresh Force a reload of the token, even if the token is cached and still valid
     *
     * @throws {@link SfuException} When the sfu cannot be reached, a timeout or another exception occurred
     *
     * TODO(ANDR-2090): add an option for a timeout
     */
    @AnyThread
    suspend fun obtainSfuToken(forceRefresh: Boolean = false): SfuToken

    /**
     * @throws {@link SfuException} When the sfu cannot be reached, a timeout or another exception occurred
     */
    @AnyThread
    suspend fun peek(token: SfuToken, sfuBaseUrl: String, callId: CallId): PeekResponse

    /**
     * @throws {@link SfuException} When the sfu cannot be reached, a timeout or another exception occurred
     */
    @AnyThread
    suspend fun join(
        token: SfuToken,
        sfuBaseUrl: String,
        callDescription: GroupCallDescription,
        dtlsFingerprint: ByteArray,
    ): JoinResponse
}
