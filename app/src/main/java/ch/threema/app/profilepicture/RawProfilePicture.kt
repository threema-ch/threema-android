package ch.threema.app.profilepicture

import androidx.annotation.WorkerThread
import kotlinx.serialization.Serializable

/**
 * This class represents a profile picture. Its byte representation is not guaranteed to be a valid profile picture - let alone a picture at all.
 * This class should be used whenever we can or must assume that we have a valid profile picture.
 */
@Serializable
data class RawProfilePicture(override val bytes: ByteArray) : ProfilePicture {
    private val checkedProfilePicture: CheckedProfilePicture? by lazy {
        CheckedProfilePicture.getOrConvertFromBytes(bytes)
    }

    override fun isValid() = checkedProfilePicture != null

    /**
     * Converts this profile picture to a valid profile picture. If this isn't possible, null is returned. If this profile picture already is a valid
     * profile picture, its byte representation is wrapped into a [CheckedProfilePicture] without conversion.
     */
    @WorkerThread
    fun toChecked(): CheckedProfilePicture? = checkedProfilePicture

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawProfilePicture

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode() = bytes.contentHashCode()
}
