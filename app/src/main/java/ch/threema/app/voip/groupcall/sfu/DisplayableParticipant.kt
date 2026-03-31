package ch.threema.app.voip.groupcall.sfu

import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.types.Identity

interface DisplayableParticipant {
    val identity: Identity
    val nickname: String

    fun getDisplayName(contactNameFormat: ContactNameFormat): String
}
