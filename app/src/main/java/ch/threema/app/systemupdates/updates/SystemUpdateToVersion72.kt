package ch.threema.app.systemupdates.updates

import ch.threema.data.datatypes.IdColor
import ch.threema.data.repositories.ContactModelRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * For ID colors we store the first byte of the SHA-256 hash of the contact identity.
 */
class SystemUpdateToVersion72() : SystemUpdate, KoinComponent {
    private val contactModelRepository: ContactModelRepository by inject()

    override fun run() {
        contactModelRepository.getAll().forEach { contactModel ->
            contactModel.setIdColor(IdColor.ofIdentity(contactModel.identity))
        }
    }

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 72
    }
}
