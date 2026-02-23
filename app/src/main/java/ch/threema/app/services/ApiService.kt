package ch.threema.app.services

import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.blob.BlobLoader
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.blob.BlobUploader
import ch.threema.domain.types.Identity
import java.io.IOException
import java.net.URL

@SessionScoped
interface ApiService {
    /**
     * @param blobScope Will only have an effect if multi-device is currently active
     */
    @Throws(ThreemaException::class)
    fun createUploader(
        data: ByteArray,
        shouldPersist: Boolean,
        blobScope: BlobScope,
    ): BlobUploader

    fun createLoader(blobId: ByteArray): BlobLoader

    @Throws(ThreemaException::class)
    fun getAuthToken(): String?

    /**
     * Invalidate the auth token (only used for onprem). This forces a new fetch of the auth token
     * the next time the token is obtained with [.getAuthToken].
     */
    fun invalidateAuthToken()

    @Throws(ThreemaException::class, IOException::class)
    fun getAvatarURL(identity: Identity): URL
}
