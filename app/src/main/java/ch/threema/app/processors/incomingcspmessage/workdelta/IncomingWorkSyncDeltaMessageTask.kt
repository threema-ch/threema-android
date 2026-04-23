package ch.threema.app.processors.incomingcspmessage.workdelta

import ch.threema.app.AppConstants
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.workers.WorkSyncWorker
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.ContactModel
import ch.threema.domain.protocol.csp.messages.workdelta.WorkSyncDeltaMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedContactSyncUpdate
import ch.threema.protobuf.csp.e2e.WorkSyncDelta
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.contact
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("IncomingWorkSyncDeltaMessageTask")

class IncomingWorkSyncDeltaMessageTask(
    message: WorkSyncDeltaMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<WorkSyncDeltaMessage>(message, triggerSource, serviceManager), KoinComponent {

    private val multiDeviceManager = serviceManager.multiDeviceManager
    private val nonceFactory = serviceManager.nonceFactory

    private val workSyncDeltaChangeDeterminationSteps: WorkSyncDeltaChangeDeterminationSteps by inject()

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) = processWorkSyncDeltaMessage(handle)

    /**
     *  This message is never received from sync.
     */
    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = ReceiveStepsResult.DISCARD

    private suspend fun processWorkSyncDeltaMessage(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.debug("IncomingWorkSyncDeltaMessageTask id: {}", message.messageId)

        if (!ConfigUtils.supportsAvailabilityStatus()) {
            logger.info("Discarding work sync delta message because it is not supported by current build")
            return ReceiveStepsResult.DISCARD
        }

        // 1. If not Work flavor, log a warning, discard the message and abort these steps.
        if (!ConfigUtils.isWorkBuild()) {
            logger.warn("Received a work sync delta message in a none-work build")
            return ReceiveStepsResult.DISCARD
        }

        // 2. If the sender is not *3MAW0RK, discard the message and abort these steps.
        if (message.fromIdentity != AppConstants.THREEMA_WORK_SYNC_IDENTITY) {
            logger.warn("Received a work sync delta message from an unknown sender: {}", message.fromIdentity)
            return ReceiveStepsResult.DISCARD
        }

        return when (message.workSyncDelta.actionCase) {
            // 3. If action is of variant require_work_sync, schedule a persistent task to make a full Work Sync.
            WorkSyncDelta.ActionCase.REQUIRE_WORK_SYNC -> {
                enqueueFullWorkSync()
                ReceiveStepsResult.SUCCESS
            }

            // 4. If action is of variant apply:
            WorkSyncDelta.ActionCase.APPLY -> processWorkSyncDeltaApplyAction(
                handle = handle,
                apply = message.workSyncDelta.apply,
            )

            else -> {
                // 5. If action is an unknown variant, log a warning that an unknown Work Sync variant has been encountered.
                logger.warn("Received an unknown work sync delta action value of {}", message.workSyncDelta.actionCase.number)
                ReceiveStepsResult.DISCARD
            }
        }
    }

    private suspend fun processWorkSyncDeltaApplyAction(
        handle: ActiveTaskCodec,
        apply: WorkSyncDelta.Apply,
    ): ReceiveStepsResult {
        // 4.1 Run the Work Sync Delta Change Determination Steps with apply.deltas and let changes be the result.
        val result: WorkSyncDeltaChangeDeterminationSteps.Result = workSyncDeltaChangeDeterminationSteps.run(
            deltas = apply.deltasList,
        )

        when (result) {
            // 4.2 If changes is a marker that a full Work Sync is required, schedule a persistent task to make a full Work Sync and
            // abort these steps.
            // Note - Deviation from the protocol: We do not execute the work sync in a `persistent task`. This is accepted behavior for now.
            is WorkSyncDeltaChangeDeterminationSteps.Result.PerformFullWorkSync -> {
                enqueueFullWorkSync()
                return ReceiveStepsResult.SUCCESS
            }

            is WorkSyncDeltaChangeDeterminationSteps.Result.Instruction -> {
                // 4.3 If changes is empty, discard the message and abort these steps.
                if (result.changes.isEmpty()) {
                    return ReceiveStepsResult.DISCARD
                }

                // Change instructions from the first run of WorkSyncDeltaChangeDeterminationSteps might be replaced by the multi device
                // sub-steps
                var changesToApplyLocally: Map<ContactModel, AvailabilityStatus> = result.changes

                // 4.4 (MD) Run the following sub-steps
                if (multiDeviceManager.isMultiDeviceActive) {
                    changesToApplyLocally = runMultiDeviceSubSteps(
                        handle = handle,
                        deltas = message.workSyncDelta.apply.deltasList,
                    )
                }

                // 4.5 Apply all changes persistently.
                changesToApplyLocally.forEach { (model, status) ->
                    model.setAvailabilityStatusFromSync(status)
                }

                return ReceiveStepsResult.SUCCESS
            }
        }
    }

    /**
     *  @return A map of changes that need to be applied **locally** after these sub-steps completed.
     */
    private suspend fun runMultiDeviceSubSteps(
        handle: ActiveTaskCodec,
        deltas: List<WorkSyncDelta.Delta>,
    ): Map<ContactModel, AvailabilityStatus> {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        // 4.4.1 Begin a transaction with scope WORK_SYNC_DELTA and no precondition.
        return handle.createTransaction(
            keys = multiDeviceProperties.keys,
            scope = TransactionScope.Scope.WORK_SYNC_DELTA,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            // 4.4.2 Run the Work Sync Delta Change Determination Steps another time and update changes with the result.¹
            when (val result = workSyncDeltaChangeDeterminationSteps.run(deltas)) {
                // 4.4.3 If changes is a marker that a full Work Sync is required, schedule a persistent task to make a full Work Sync.
                // Note - Deviation from the protocol: We do not execute the work sync in a `persistent task`. This is accepted behavior for now.
                is WorkSyncDeltaChangeDeterminationSteps.Result.PerformFullWorkSync -> {
                    enqueueFullWorkSync()
                    return@execute emptyMap()
                }

                // 4.4.3 ... Otherwise, reflect all changes.
                is WorkSyncDeltaChangeDeterminationSteps.Result.Instruction -> {
                    reflectAllChanges(handle, multiDeviceProperties, result.changes)
                    return@execute result.changes
                }
            }

            // 4.4.4 (Implicit by execute function): Commit the transaction and await acknowledgement.
        }
    }

    private suspend fun reflectAllChanges(
        handle: ActiveTaskCodec,
        multiDeviceProperties: MultiDeviceProperties,
        changes: Map<ContactModel, AvailabilityStatus>,
    ) {
        changes.map { (contactModel, availabilityStatus) ->
            val encryptedEnvelopeResult = getEncryptedContactSyncUpdate(
                contact = contact {
                    this.identity = contactModel.identity
                    this.workAvailabilityStatus = availabilityStatus.toProtocolModel()
                },
                multiDeviceProperties = multiDeviceProperties,
            )
            val reflectId = handle.reflect(encryptedEnvelopeResult)
            reflectId to encryptedEnvelopeResult.nonce
        }.forEach { (reflectId, nonce) ->
            handle.awaitReflectAck(reflectId)
            nonceFactory.store(
                scope = NonceScope.D2D,
                nonce = nonce,
            )
        }
    }

    private fun enqueueFullWorkSync() {
        WorkSyncWorker.performOneTimeWorkSync(
            context = ThreemaApplication.getAppContext(),
            forceUpdate = false,
            tag = null,
        )
    }
}
