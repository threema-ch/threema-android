package ch.threema.app.voip.groupcall.sfu

import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.models.ContactModel
import ch.threema.domain.types.Identity

sealed interface ContactOrUser : DisplayableParticipant {
    val publicKey: ByteArray

    data class Contact(val contactModel: ContactModel) : ContactOrUser {
        override val identity: Identity
            get() = Identity(contactModel.identity)
        override val publicKey: ByteArray
            get() = contactModel.data!!.publicKey
        override val nickname: String
            get() = contactModel.data!!.nickname ?: ""

        override fun getDisplayName(contactNameFormat: ContactNameFormat): String {
            val contactModelData = contactModel.data!!

            val names = when (contactNameFormat) {
                ContactNameFormat.FIRSTNAME_LASTNAME -> listOfNotNull(contactModelData.firstName, contactModelData.lastName)
                ContactNameFormat.LASTNAME_FIRSTNAME -> listOfNotNull(contactModelData.lastName, contactModelData.firstName)
            }
            val name = names.joinToString(" ")
            if (name.isNotBlank()) {
                return name
            }

            if (nickname != identity.value && nickname.isNotBlank()) {
                return "~$nickname"
            }

            return identity.value
        }
    }

    class User(
        override val identity: Identity,
        override val publicKey: ByteArray,
        private val myDisplayName: String,
    ) : ContactOrUser {
        override val nickname: String = ""

        override fun getDisplayName(contactNameFormat: ContactNameFormat): String = myDisplayName
    }
}
