/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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
import ch.threema.app.multidevice.linking.Completed
import ch.threema.app.multidevice.linking.Connected
import ch.threema.app.multidevice.linking.DeviceLinkingCancelledException
import ch.threema.app.multidevice.linking.DeviceLinkingDataCollector
import ch.threema.app.multidevice.linking.DeviceLinkingException
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.multidevice.linking.Failed
import ch.threema.app.services.ContactsSyncAdapterService
import ch.threema.app.services.PreferenceService
import ch.threema.app.webclient.services.instance.DisconnectContext
import ch.threema.app.workers.AutoDeleteWorker
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.app.workers.WorkSyncWorker
import ch.threema.base.utils.Base64UrlSafe
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.toHexString
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.rendezvous.RendezvousConnection
import ch.threema.domain.protocol.rendezvous.DeviceJoinMessage
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D.TransactionScope.Scope
import ch.threema.protobuf.url_payloads.DeviceGroupJoinRequestOrOffer
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

private val logger = LoggingUtil.getThreemaLogger("DeviceLinkingTask")

private val supportedVersionRange = 0..0

class DeviceLinkingTask(
    private val deviceJoinOfferUri: String,
    private val serviceManager: ServiceManager,
    private val cancelledSignal: Deferred<Unit>
) : ActiveTask<Result<Unit>> {
    override val type: String = "DeviceLinkingTask"

    private val preferenceService: PreferenceService by lazy { serviceManager.preferenceService }
    private val multiDeviceManager: MultiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val multiDeviceKeys: MultiDeviceKeys by lazy { multiDeviceManager.propertiesProvider.get().keys }
    private val deviceLinkingDataCollector by lazy { DeviceLinkingDataCollector(serviceManager) }
    private val okHttpClient: OkHttpClient by lazy { serviceManager.okHttpClient }

    val deviceLinkingController = DeviceLinkingController()

    override suspend fun invoke(handle: ActiveTaskCodec): Result<Unit> {
        return coroutineScope {
            val linkingResult = CompletableDeferred<Result<Unit>>()

            val stateChangingProcesses = getStateChangingProcesses()
            val context = serviceManager.context
            val linkingJob = launch {
                try {
                    pauseStateChangingProcesses(context, stateChangingProcesses)
                    linkingResult.complete(performLinking(handle))
                } catch (e: Exception) {
                    linkingResult.complete(Result.failure(e))
                } finally {
                    resumeStateChangingProcesses(context, stateChangingProcesses)
                }
            }

            val cancelJob = launch {
                cancelledSignal.await()
                logger.warn("Linking has been cancelled")
                linkingJob.cancel()
                linkingResult.complete(Result.failure(DeviceLinkingCancelledException()))
            }

            try {
                linkingResult.await()
            } catch (e: Exception) {
                linkingJob.cancel()
                Result.failure(e)
            } finally {
                cancelJob.cancel()
            }
        }
    }

    private suspend fun pauseStateChangingProcesses(
        context: Context,
        processes: List<StateChangingProcess>
    ) {
        logger.info("Pause state changing processes")
        processes.forEach {
            logger.info("Pause '{}'", it.name)
            it.pause.invoke(context)
        }
    }

    private suspend fun resumeStateChangingProcesses(
        context: Context,
        processes: List<StateChangingProcess>
    ) {
        logger.info("Resume state changing processes")
        processes.forEach {
            if (it.resume != null) {
                logger.info("Resume '{}'", it.name)
                it.resume.invoke(context)
            } else {
                logger.info("Do not resume '{}'", it.name)
            }
        }
    }

    private suspend fun performLinking(handle: ActiveTaskCodec): Result<Unit> {
        var connection: RendezvousConnection? = null
        return try {
            connection = createConnection()

            deviceLinkingController.waitForRendezvousPathConfirmation(connection.rph)

            handle.createTransaction(
                multiDeviceKeys,
                Scope.NEW_DEVICE_SYNC,
                TRANSACTION_TTL_MAX,
            ).execute {
                transferEssentialData(connection)
            }
            connection.closedSignal.await()
            Result.success(Unit)
        } catch (e: Exception) {
            connection?.close()
            when (e) {
                is DeviceLinkingException,
                is IOException -> Result.failure(e)

                else -> throw e
            }
        } finally {
            connection?.close()
        }
    }

    private suspend fun transferEssentialData(connection: RendezvousConnection) {
        logger.info("Begin sending of essential data")
        // 1. Send begin
        connection.write(DeviceJoinMessage.Begin())

        val essentialData = deviceLinkingDataCollector.collectData(multiDeviceKeys.dgk)
        // 2. Send blob data
        logger.debug("Send blob data")
        essentialData.blobs.forEach {
            connection.write(DeviceJoinMessage.BlobData(it))
        }

        // 3. Send Essential data
        logger.debug("Send essential data")
        connection.write(DeviceJoinMessage.EssentialData(essentialData.essentialData))

        // 4. Wait for `Registered`
        val inboundMessage = connection.read()
        if (inboundMessage is DeviceJoinMessage.Registered) {
            logger.info("Linking of new device completed")
            deviceLinkingController.onCompleted()
        } else {
            throw DeviceLinkingException("Received unexpected message")
        }
    }

    private fun parseDeviceJoinOfferUri(deviceJoinOfferUri: String): DeviceGroupJoinRequestOrOffer {
        val parts = deviceJoinOfferUri.split("#")
        if (parts.size != 2 && parts[0] != "threema://device-group/join") {
            throw DeviceLinkingException("Invalid device join offer uri: $deviceJoinOfferUri")
        }
        val bytes = Base64UrlSafe.decode(parts[1])
        return try {
            DeviceGroupJoinRequestOrOffer.parseFrom(bytes)
        } catch (e: InvalidProtocolBufferException) {
            throw DeviceLinkingException("Could not decode device join offer", e)
        }
    }

    private suspend fun createConnection(): RendezvousConnection {
        logger.trace("Join with device join offer uri `{}`", deviceJoinOfferUri)
        val offer = parseDeviceJoinOfferUri(deviceJoinOfferUri)
        logger.trace("Parsed offer: {}", offer)

        offer.version.number.let {
            if (it !in supportedVersionRange) {
                throw DeviceLinkingException("Unsupported offer version `$it`")
            }
        }

        offer.variant.typeCase.let {
            if (it != DeviceGroupJoinRequestOrOffer.Variant.TypeCase.REQUEST_TO_JOIN) {
                throw DeviceLinkingException("Unsupported offer variant `$it`")
            }
        }

        return RendezvousConnection.connect(okHttpClient, offer.rendezvousInit).also {
            logger.debug("Connection created (rph={})", it.rph.toHexString())
        }
    }

    private fun getStateChangingProcesses(): List<StateChangingProcess> {
        return listOf(
            StateChangingProcess(
                "ContactSyncAdapter",
                { disableContactSyncAdapter() },
                { enableContactSyncAdapter() }),
            StateChangingProcess(
                "AutoDeleteWorker",
                ::pauseAutoDeleteWorker,
                ::resumeAutoDeleteWorker
            ),
            StateChangingProcess("WorkSync", ::pauseWorkSync, ::resumeWorkSync),
            StateChangingProcess(
                "IdentityStatesSync",
                ::pauseIdentityStatesSync,
                ::resumeIdentityStatesSync
            ),
            // Webclient sessions are not resumed after linking
            StateChangingProcess("WebClient", { pauseWebClientSessions() }, null),
        )
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
                DisconnectContext.REASON_SESSION_STOPPED
            )
        )
    }
}

class DeviceLinkingController {
    private val _linkingStatus = MutableSharedFlow<DeviceLinkingStatus>(1, 0)
    val linkingStatus: Flow<DeviceLinkingStatus> = _linkingStatus.transformWhile {
        emit(it)
        when (it) {
            is Completed, is Failed -> false
            else -> true
        }
    }

    suspend fun waitForRendezvousPathConfirmation(rph: ByteArray) {
        val connectedState = Connected(rph)
        _linkingStatus.emit(connectedState)
        // Wait for the path to be confirmed in the ui (emoji verification)
        connectedState.rendezvousPathConfirmedSignal.await()
    }

    suspend fun onCompleted() {
        _linkingStatus.emit(Completed())
    }
}

private data class StateChangingProcess(
    val name: String,
    val pause: suspend (Context) -> Unit,
    val resume: (suspend (Context) -> Unit)?
)
