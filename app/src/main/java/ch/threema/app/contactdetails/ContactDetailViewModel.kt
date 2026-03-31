package ch.threema.app.contactdetails

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString

class ContactDetailViewModel(
    contactModelRepository: ContactModelRepository,
    identity: IdentityString,
) : ViewModel() {

    val contactModel: ContactModel = contactModelRepository.getByIdentity(identity)
        ?: error("ContactDetailViewModel: Contact with identity $identity not found")

    @JvmField
    val contactModelData: LiveData<ContactModelData?> = contactModel.liveData()

    /**
     * Update the contact's first and last name.
     */
    fun updateContactName(firstName: String, lastName: String) {
        contactModel.setNameFromLocal(firstName, lastName)
    }

    /**
     * Whether or not to show the floating edit action button.
     */
    fun showEditFAB(): Boolean {
        // Don't show the edit button for contacts linked to an Android contact
        return contactModelData.value?.isLinkedToAndroidContact() ?: false
    }
}
