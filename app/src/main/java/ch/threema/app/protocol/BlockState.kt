package ch.threema.app.protocol

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.GroupService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel.AcquaintanceLevel

/**
 * The block state of an identity.
 */
enum class BlockState(private val isBlocked: Boolean) {
    /**
     * The state of an identity that has been explicitly blocked by the user.
     */
    EXPLICITLY_BLOCKED(true),

    /**
     * The state of an identity that is unknown.
     *
     * An identity is considered unknown if it is not stored locally or it has
     * [AcquaintanceLevel.GROUP] and all common groups are marked as left.
     *
     * Note that this state is only used when unknown contacts are blocked.
     */
    IMPLICITLY_BLOCKED(true),

    /**
     * The identity is not blocked.
     */
    NOT_BLOCKED(false),
    ;

    /**
     * Returns true if it is either [EXPLICITLY_BLOCKED] or [IMPLICITLY_BLOCKED].
     */
    fun isBlocked(): Boolean = isBlocked
}

fun runIdentityBlockedSteps(
    identity: Identity,
    contactModelRepository: ContactModelRepository,
    contactStore: ContactStore,
    groupService: GroupService,
    blockedIdentitiesService: BlockedIdentitiesService,
    preferenceService: PreferenceService,
): BlockState {
    if (contactStore.isSpecialContact(identity)) {
        return BlockState.NOT_BLOCKED
    }

    if (blockedIdentitiesService.isBlocked(identity)) {
        return BlockState.EXPLICITLY_BLOCKED
    }

    if (!preferenceService.isBlockUnknown) {
        return BlockState.NOT_BLOCKED
    }

    val contactModel = contactModelRepository.getByIdentity(identity)
        ?: return BlockState.IMPLICITLY_BLOCKED

    if (contactModel.data?.acquaintanceLevel == AcquaintanceLevel.DIRECT) {
        return BlockState.NOT_BLOCKED
    }

    if (groupService.getGroupsByIdentity(identity).any { groupService.isGroupMember(it) }) {
        return BlockState.NOT_BLOCKED
    }

    return BlockState.IMPLICITLY_BLOCKED
}
