package ch.threema.app.services.avatarcache

import ch.threema.app.glide.AvatarOptions

/**
 * This class is used as a identifier for glide. Based on its hashcode, the objects can be cached in different resolutions.
 */
abstract class AvatarConfig<S>(
    @JvmField val subject: S?,
    @JvmField val options: AvatarOptions,
) {

    @JvmField
    val state: Long

    init {
        state = getAvatarState()
    }

    protected abstract fun getSubjectHashCode(): Int

    protected abstract fun getAvatarState(): Long

    protected abstract fun getSubjectDebugString(): String

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other is AvatarConfig<*>) {
            var areSubjectsEqual = this.subject === other.subject
            if (!areSubjectsEqual && this.subject != null) {
                areSubjectsEqual = this.subject == other.subject
            }
            return areSubjectsEqual && this.options == other.options
        }
        return false
    }

    /**
     * The hash code of this class is based only on the parameters that change the actual result, e.g., the resolution,
     * the default options and of course the [subject]. The [state] does not affect the hashcode and must be used as signature.
     */
    override fun hashCode(): Int = getSubjectHashCode() * 31 + options.hashCode()

    override fun toString(): String = "'" + getSubjectDebugString() + "' " + options
}
