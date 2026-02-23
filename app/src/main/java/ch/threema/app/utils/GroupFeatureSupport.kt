package ch.threema.app.utils

import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.storage.models.ContactModel

class GroupFeatureSupport(
    @ThreemaFeature.Feature feature: Long,
    groupMembers: List<ContactModel>,
) {
    @SuppressWarnings("WeakerAccess")
    val contactsWithFeatureSupport: List<ContactModel>
    val contactsWithoutFeatureSupport: List<ContactModel>

    init {
        groupMembers.groupBy { ThreemaFeature.hasFeature(it.featureMask, feature) }.also {
            contactsWithFeatureSupport = it[true] ?: emptyList()
            contactsWithoutFeatureSupport = it[false] ?: emptyList()
        }
    }

    val adoptionRate: GroupFeatureAdoptionRate =
        // deduct adoption rate
        if (contactsWithFeatureSupport.isNotEmpty() && contactsWithoutFeatureSupport.isNotEmpty()) {
            GroupFeatureAdoptionRate.PARTIAL
        } else if (contactsWithFeatureSupport.isNotEmpty()) {
            // implies that contactsWithoutFeatureSupport is empty
            GroupFeatureAdoptionRate.ALL
        } else {
            // implies that there are no contacts with feature support
            GroupFeatureAdoptionRate.NONE
        }
}

enum class GroupFeatureAdoptionRate {
    /**
     * no group member supports a feature
     */
    NONE,

    /**
     * not all group members support a feature
     */
    PARTIAL,

    /**
     * all group members support a feature
     */
    ALL,
}
