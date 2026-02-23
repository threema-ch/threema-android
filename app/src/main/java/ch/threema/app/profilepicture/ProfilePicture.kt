package ch.threema.app.profilepicture

import ch.threema.domain.protocol.csp.ProtocolDefines
import kotlinx.serialization.Serializable

/**
 * Contains the bytes of a profile picture.
 */
@Serializable
sealed interface ProfilePicture {
    /**
     * The bytes that represent the profile picture.
     */
    val bytes: ByteArray

    /**
     * Checks whether the [bytes] correspond to a jpeg that is not larger than [ProtocolDefines.PROFILE_PICTURE_WIDTH_PX] and
     * [ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX] pixels.
     */
    fun isValid(): Boolean

    /**
     * Checks whether the two profile pictures are *structurally* equal to one another.
     *
     * Two profile pictures are considered structurally equal if their byte arrays have the same size, and elements at corresponding indices are
     * equal.
     */
    fun contentEquals(profilePicture: ProfilePicture): Boolean {
        return bytes.contentEquals(profilePicture.bytes)
    }
}
