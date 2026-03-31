package ch.threema.app.androidcontactsync.types

/**
 * Represents a contact's name structured into a [firstName] and a [lastName]. Note that either [firstName] or [lastName] may be empty, but not both.
 */
class ContactName private constructor(val firstName: String, val lastName: String) {
    /**
     * The full name consists of the first name followed by the last name separated by a space if both are non-empty. If one of them is empty, the
     * other name is used.
     */
    val fullName: String
        get() = "$firstName $lastName".trim()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactName) return false
        return this.firstName == other.firstName && this.lastName == other.lastName
    }

    override fun hashCode(): Int {
        var result = firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        return result
    }

    companion object {
        /**
         * Create a contact name with the [firstName] and [lastName] if at least one of them is not blank.
         */
        fun create(firstName: String?, lastName: String?): ContactName? {
            val trimmedFirstName = firstName?.trim() ?: ""
            val trimmedLastName = lastName?.trim() ?: ""
            if (trimmedFirstName.isEmpty() && trimmedLastName.isEmpty()) {
                return null
            }
            return ContactName(firstName = trimmedFirstName, lastName = trimmedLastName)
        }
    }
}
