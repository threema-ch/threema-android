package ch.threema.app.services

import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity

/**
 * This service manages the identity exclusion list for the address book synchronization. Identities that are excluded won't be added when the address
 * book synchronisation is run.
 */
interface ExcludedSyncIdentitiesService {

    /**
     * Adds the [identity] to the exclusion list. Depending on the [triggerSource], the change is reflected.
     */
    fun excludeFromSync(identity: Identity, triggerSource: TriggerSource)

    /**
     * Removes the [identity] from the exclusion list, so that it will be synced again. Depending on the [triggerSource], the change is reflected.
     */
    fun removeExcludedIdentity(identity: Identity, triggerSource: TriggerSource)

    /**
     * Replace all existing excluded identities with the [identities].
     */
    fun setExcludedIdentities(identities: Set<Identity>, triggerSource: TriggerSource)

    /**
     * Get all excluded identities.
     */
    fun getExcludedIdentities(): Set<Identity>

    /**
     * Check whether the [identity] is excluded from sync or not.
     */
    fun isExcluded(identity: Identity): Boolean
}
