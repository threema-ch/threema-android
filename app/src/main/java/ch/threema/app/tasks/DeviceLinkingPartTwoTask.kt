/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.tasks

import android.content.Context
import androidx.work.await
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.linking.DeviceLinkingCancelledException
import ch.threema.app.multidevice.linking.DeviceLinkingDataCollector
import ch.threema.app.multidevice.linking.DeviceLinkingException
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactsSyncAdapterService
import ch.threema.app.webclient.services.instance.DisconnectContext
import ch.threema.app.workers.AutoDeleteWorker
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.app.workers.WorkSyncWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.rendezvous.DeviceJoinMessage
import ch.threema.domain.protocol.rendezvous.RendezvousConnection
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("DeviceLinkingPartTwoTask")

class DeviceLinkingPartTwoTask(
    private val rendezvousConnection: RendezvousConnection,
    private val serviceManager: ServiceManager,
    private val taskCancelledSignal: Deferred<Unit>,
) : ActiveTask<Result<Unit>> {
    private val preferenceService: PreferenceService by lazy { serviceManager.preferenceService }
    private val multiDeviceManager: MultiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val multiDeviceKeys: MultiDeviceKeys by lazy { multiDeviceManager.propertiesProvider.get().keys }
    private val deviceLinkingDataCollector by lazy { DeviceLinkingDataCollector(serviceManager) }

    override val type: String = "DeviceLinkingPartTwoTask"

    override suspend fun invoke(handle: ActiveTaskCodec): Result<Unit> {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cancelling linking task part two because md is not active")
            return Result.failure(IllegalStateException("MultiDevice is not active"))
        }

        return try {
            handle.createTransaction(
                multiDeviceKeys,
                MdD2D.TransactionScope.Scope.NEW_DEVICE_SYNC,
                TRANSACTION_TTL_MAX,
            ).execute(::invokeInTransaction)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun invokeInTransaction(): Result<Unit> = coroutineScope {
        val linkingPartTwoResultDeferred = CompletableDeferred<Result<Unit>>()

        var awaitOutsideCancelSignalJob: Job? = null

        val stateChangingProcesses = getStateChangingProcesses()
        val context = serviceManager.context

        val transferDataAndWaitForCloseJob = launch(start = CoroutineStart.LAZY) {
            try {
                pauseStateChangingProcesses(context, stateChangingProcesses)
                transferEssentialData()
                rendezvousConnection.closedSignal.await()
                linkingPartTwoResultDeferred.complete(Result.success(Unit))
            } catch (exception: Exception) {
                linkingPartTwoResultDeferred.completeExceptionally(exception)
            } finally {
                awaitOutsideCancelSignalJob?.cancel()
                resumeStateChangingProcesses(context, stateChangingProcesses)
            }
        }

        awaitOutsideCancelSignalJob = launch {
            taskCancelledSignal.await()
            logger.warn("Device linking part two received internal cancel signal")
            transferDataAndWaitForCloseJob.cancel()
            rendezvousConnection.close()
            linkingPartTwoResultDeferred.complete(Result.failure(DeviceLinkingCancelledException()))
        }

        transferDataAndWaitForCloseJob.start()

        return@coroutineScope linkingPartTwoResultDeferred.await()
    }

    /**
     *  **Caution**: This will suspend until the user registered on the other device
     *
     *  @throws DeviceLinkingException if the first received message ist not [DeviceJoinMessage].
     */
    @Throws(DeviceLinkingException::class)
    private suspend fun transferEssentialData() {
        logger.info("Begin sending of essential data")
        // 1. Send begin
        rendezvousConnection.write(DeviceJoinMessage.Begin())

        val deviceLinkingData = deviceLinkingDataCollector.collectData(multiDeviceKeys.dgk)
        // 2. Send blob data
        logger.info("Send blob data")
        deviceLinkingData.blobs.forEach {
            rendezvousConnection.write(DeviceJoinMessage.BlobData(it))
        }

        // 3. Send Essential data
        logger.info("Send essential data")
        rendezvousConnection.write(DeviceJoinMessage.EssentialData(deviceLinkingData.essentialDataProvider.get()))

        // 4. Wait for `Registered`
        logger.info("Waiting for device join message")
        val inboundMessage = rendezvousConnection.read()
        if (inboundMessage !is DeviceJoinMessage.Registered) {
            throw DeviceLinkingException("Received unexpected message")
        }
        logger.info("Linking of new device completed")
    }

    private fun getStateChangingProcesses(): List<StateChangingProcess> = listOf(
        StateChangingProcess(
            "ContactSyncAdapter",
            { disableContactSyncAdapter() },
            { enableContactSyncAdapter() },
        ),
        StateChangingProcess("AutoDeleteWorker", ::pauseAutoDeleteWorker, ::resumeAutoDeleteWorker),
        StateChangingProcess("WorkSync", ::pauseWorkSync, ::resumeWorkSync),
        StateChangingProcess(
            "IdentityStatesSync",
            ::pauseIdentityStatesSync,
            ::resumeIdentityStatesSync,
        ),
        // Webclient sessions are not resumed after linking
        StateChangingProcess("WebClient", { pauseWebClientSessions() }, null),
    )

    private suspend fun pauseStateChangingProcesses(
        context: Context,
        processes: List<StateChangingProcess>,
    ) {
        logger.info("Pause state changing processes")
        processes.forEach { process ->
            logger.info("Pause '{}'", process.name)
            process.pause(context)
        }
    }

    private suspend fun resumeStateChangingProcesses(
        context: Context,
        processes: List<StateChangingProcess>,
    ) {
        logger.info("Resume state changing processes")
        processes.forEach { process ->
            if (process.resume != null) {
                logger.info("Resume '{}'", process.name)
                process.resume.invoke(context)
            } else {
                logger.info("Do not resume '{}'", process.name)
            }
        }
    }

    private fun disableContactSyncAdapter() {
        ContactsSyncAdapterService.disableSync()
    }

    private fun enableContactSyncAdapter() {
        ContactsSyncAdapterService.enableSync()
    }

    private suspend fun pauseAutoDeleteWorker(context: Context) {
        AutoDeleteWorker.cancelAutoDeleteAwait(context)
    }

    private fun resumeAutoDeleteWorker(context: Context) {
        AutoDeleteWorker.scheduleAutoDelete(context)
    }

    private suspend fun pauseWorkSync(context: Context) {
        WorkSyncWorker.cancelPeriodicWorkSyncAwait(context)
    }

    private fun resumeWorkSync(context: Context) {
        WorkSyncWorker.schedulePeriodicWorkSync(context, preferenceService)
    }

    private suspend fun pauseIdentityStatesSync(context: Context) {
        ContactUpdateWorker.cancelPeriodicSync(context).await()
    }

    private fun resumeIdentityStatesSync(context: Context) {
        ContactUpdateWorker.schedulePeriodicSync(context, preferenceService)
    }

    private fun pauseWebClientSessions() {
        serviceManager.webClientServiceManager.sessionService.stopAll(
            DisconnectContext.byUs(
                DisconnectContext.REASON_SESSION_STOPPED,
            ),
        )
    }
}

private data class StateChangingProcess(
    val name: String,
    val pause: suspend (Context) -> Unit,
    val resume: (suspend (Context) -> Unit)?,
)
