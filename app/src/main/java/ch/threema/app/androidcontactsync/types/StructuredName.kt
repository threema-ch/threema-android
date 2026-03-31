package ch.threema.app.androidcontactsync.types

data class StructuredName(
    val prefix: String? = null,
    val givenName: String? = null,
    val middleName: String? = null,
    val familyName: String? = null,
    val suffix: String? = null,
    val displayName: String? = null,
) {
    /**
     * Group the structured name properties into a first and last name. If all the structured fields contain null or blank values, null is returned.
     */
    fun reduceToFirstAndLastName(): ContactName? =
        getNameFromStructuredFields() ?: getNameFromDisplayName()

    private fun getNameFromStructuredFields(): ContactName? {
        // The first name consists of the prefix followed by the given name and middle name.
        val firstName = listOfNotNull(prefix, givenName, middleName)
            .joinToString(separator = " ", transform = String::trim)

        // The last name consists of the family name followed by the suffix.
        val lastName = listOfNotNull(familyName, suffix)
            .joinToString(separator = " ", transform = String::trim)

        return ContactName.create(
            firstName = firstName,
            lastName = lastName,
        )
    }

    private fun getNameFromDisplayName(): ContactName? {
        if (displayName.isNullOrBlank()) {
            return null
        }

        val parts = displayName
            .trim()
            .split(' ')
            .filter { part -> part.isNotBlank() }
        // The first part is considered to be the first name
        val firstName = parts.firstOrNull()
        // All remaining parts are concatenated to the last name
        val lastName = parts.drop(1).joinToString(separator = " ")

        return ContactName.create(
            firstName = firstName,
            lastName = lastName,
        )
    }
}
