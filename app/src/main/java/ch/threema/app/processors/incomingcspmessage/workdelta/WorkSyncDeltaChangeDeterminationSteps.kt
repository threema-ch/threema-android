package ch.threema.app.processors.incomingcspmessage.workdelta

import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.IdentityType
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.WorkSyncDelta
import ch.threema.protobuf.csp.e2e.contactSyncOrNull

private val logger = getThreemaLogger("WorkSyncDeltaChangeDeterminationSteps")

class WorkSyncDeltaChangeDeterminationSteps(
    private val contactModelRepository: ContactModelRepository,
) {

    sealed interface Result {

        data object PerformFullWorkSync : Result

        data class Instruction(val changes: Map<ContactModel, AvailabilityStatus>) : Result
    }

    /**
     *  1. Let [deltas] be a list of [WorkSyncDelta.Delta].
     */
    fun run(deltas: List<WorkSyncDelta.Delta>): Result {
        // Preparation for step 3.1.1.1 to prevent database reads in the delta loop
        val contactModels: Map<IdentityString, ContactModel> = contactModelRepository
            .getByIdentities(getAllIdentities(deltas))
            .associateBy(ContactModel::identity)

        // 2. Let changes be an empty list of change instructions that would be required to apply all deltas.
        val changes = mutableMapOf<ContactModel, AvailabilityStatus>()

        // 3. For each delta of deltas:
        for (delta in deltas) {
            when (delta.actionCase) {
                // 3.1 If delta.action is of variant contact_sync:
                WorkSyncDelta.Delta.ActionCase.CONTACT_SYNC -> {
                    when (delta.contactSync.actionCase) {
                        // 3.1.1 If contact_sync.action is of variant update:
                        WorkSyncDelta.ContactSync.ActionCase.UPDATE -> {
                            val identityDelta = delta.contactSync.update.identity

                            // 3.1.1.1 Lookup the contact associated to update.identity and let contact be the result.
                            val contact: ContactModel? = contactModels[identityDelta]
                            val contactData: ContactModelData? = contact?.data

                            // 3.1.1.2 If contact is not defined, discard update and continue with the next delta.¹
                            if (contactData == null) {
                                logger.warn("Received a work sync delta contact sync update for an unknown identity: {}", identityDelta)
                                continue
                            }

                            // 3.1.1.3 If contact is not currently considered a work contact, return that a full Work Sync is required.
                            if (contactData.identityType != IdentityType.WORK) {
                                return Result.PerformFullWorkSync
                            }

                            // 3.1.1.4 If contact's last full Work Sync timestamp is defined and ≥ delta.applied_at, discard update and continue with
                            // the next delta.
                            if (contactData.workLastFullSyncAt != null && contactData.workLastFullSyncAt.toEpochMilli() >= delta.appliedAt) {
                                continue
                            }

                            val availabilityStatusDelta = AvailabilityStatus.fromProtocolModel(
                                workAvailabilityStatus = delta.contactSync.update.availabilityStatus,
                            )
                            if (availabilityStatusDelta == null) {
                                logger.error("Failed to parse delta availability status from work sync delta change")
                                continue
                            }

                            // 3.1.1.5 If update does not diverge from the properties of contact, discard update and continue with the next delta.
                            if (availabilityStatusDelta == contactData.availabilityStatus) {
                                continue
                            }

                            // 3.1.1.6 Add a change to changes for the necessary changes defined by update to update the contact in form of a
                            // d2d_sync.Contact.
                            changes[contact] = availabilityStatusDelta
                        }

                        // 3.1.2 If contact_sync.action is an unknown variant, log a warning that an unknown Work Sync Delta contact action has been
                        // encountered.
                        WorkSyncDelta.ContactSync.ActionCase.ACTION_NOT_SET -> {
                            logger.warn("Received a work sync delta contact sync with an unknown action of {}", delta.contactSync.actionCase.number)
                            continue
                        }
                    }
                }

                // 3.2 If delta.action is an unknown variant, log a warning that an unknown Work Sync Delta action has been encountered.
                else -> {
                    logger.warn("Received a work sync delta with an unknown action of {}", delta.actionCase.number)
                    continue
                }
            }
        }
        return Result.Instruction(changes)
    }

    private fun getAllIdentities(deltas: List<WorkSyncDelta.Delta>): Set<IdentityString> =
        deltas
            .mapNotNull { delta -> delta.contactSyncOrNull?.update?.identity }
            .toSet()
}
