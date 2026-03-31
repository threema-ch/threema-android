package ch.threema.data.datatypes

import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.domain.types.Identity

sealed interface MentionNameData {

    val identity: Identity?
    val nickname: String?

    /**
     *  @return The `ResolvedString` value containing the display name, or `null` if not a single value is present.
     */
    fun getDisplayName(contactNameFormat: ContactNameFormat): ResolvableString

    data class Contact(
        override val identity: Identity,
        override val nickname: String?,
        val firstname: String,
        val lastname: String,
    ) : MentionNameData {

        /**
         *  **Priority:**
         *  1. First- and/or last-name also depending on [contactNameFormat]
         *  2. Nickname with the `~` prefix
         *  3. Identity
         */
        override fun getDisplayName(contactNameFormat: ContactNameFormat): ResolvableString {
            val hasFirstName = firstname.isNotBlank()
            val hasLastName = lastname.isNotBlank()
            val hasNickname = !nickname.isNullOrBlank() && nickname.trim() != identity.value
            val result: String =
                if (hasFirstName && hasLastName) {
                    when (contactNameFormat) {
                        ContactNameFormat.FIRSTNAME_LASTNAME -> "${firstname.trim()} ${lastname.trim()}"
                        ContactNameFormat.LASTNAME_FIRSTNAME -> "${lastname.trim()} ${firstname.trim()}"
                    }
                } else if (hasFirstName) {
                    firstname.trim()
                } else if (hasLastName) {
                    lastname.trim()
                } else if (hasNickname) {
                    "~${nickname.trim()}"
                } else {
                    identity.value
                }
            return result.toResolvedString()
        }
    }

    data class Me(
        override val identity: Identity,
        override val nickname: String?,
    ) : MentionNameData {

        /**
         *  If, and only if the [nickname] is not blank and its value is different from the own [identity], the mention should display the own
         *  nickname. Otherwise the text "`Me`" should be display.
         *
         *  The users own nickname will **not** be prefixed with a `~`.
         */
        override fun getDisplayName(contactNameFormat: ContactNameFormat): ResolvableString =
            if (!nickname.isNullOrBlank() && nickname.trim() != identity.value) {
                nickname.trim().toResolvedString()
            } else {
                ResourceIdString(R.string.me_myself_and_i)
            }
    }
}
