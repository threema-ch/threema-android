/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import androidx.annotation.WorkerThread
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.IS_FS_SUPPORTED_WITH_MD
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.PersistedMultiDeviceProperties
import ch.threema.app.multidevice.linking.DeviceLinkingException
import ch.threema.app.multidevice.linking.DeviceLinkingInvalidQrCodeException
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.multidevice.linking.DeviceLinkingUnsupportedProtocolException
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.base.utils.Base64UrlSafe
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.SecureRandomUtil.generateRandomBytes
import ch.threema.base.utils.SecureRandomUtil.generateRandomU64
import ch.threema.base.utils.toHexString
import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.rendezvous.RendezvousConnection
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.url_payloads.DeviceGroupJoinRequestOrOffer
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private val logger = LoggingUtil.getThreemaLogger("DeviceLinkingPartOneTask")

private val supportedVersionRange = 0..0

/**
 * @param taskCancelledSignal This is used to cancel the inner coroutine that establishes
 * the rendezvous connection. This is important because the inner job could potentially run
 * for while because it suspends until the user selected the correct matching emoji combination.
 */
class DeviceLinkingPartOneTask(
    private val deviceLinkingController: DeviceLinkingController,
    private val deviceJoinOfferUri: String,
    private val serviceManager: ServiceManager,
    private val taskCancelledSignal: Deferred<Unit>,
) : ActiveTask<Result<RendezvousConnection>> {
    override val type: String = "DeviceLinkingPartOneTask"

    private val multiDeviceManager: MultiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val contactService: ContactService by lazy { serviceManager.contactService }
    private val userService: UserService by lazy { serviceManager.userService }
    private val fsMessageProcessor: ForwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }
    private val taskCreator: TaskCreator by lazy { serviceManager.taskCreator }

    private val okHttpClient: OkHttpClient by lazy { serviceManager.okHttpClient }

    override suspend fun invoke(handle: ActiveTaskCodec): Result<RendezvousConnection> {
        val establishRendezvousConnectionResult: Result<RendezvousConnection> = try {
            coroutineScope {
                var awaitOutsideCancelSignalJob: Job? = null

                // Start lazily to wait for initialisation of `awaitOutsideCancelSignalJob`
                val establishConnectionAsync: Deferred<Result<RendezvousConnection>> = async(start = CoroutineStart.LAZY) {
                    try {
                        createdVerifiedRendezvousConnection()
                    } finally {
                        awaitOutsideCancelSignalJob?.cancel()
                    }
                }

                awaitOutsideCancelSignalJob = launch {
                    taskCancelledSignal.await()
                    logger.warn("Device linking part one received internal cancel signal")
                    establishConnectionAsync.cancel()
                }

                establishConnectionAsync.await()
            }
        } catch (exception: Exception) {
            return Result.failure(exception)
        }

        if (establishRendezvousConnectionResult.isSuccess) {
            if (!multiDeviceManager.isMultiDeviceActive) {
                logger.info("Activating multi device")
                try {
                    activateMultiDevice(handle)
                    logger.info("Multi device was activated")
                } catch (exception: Exception) {
                    logger.error("Failed to activate multi device", exception)
                    return Result.failure(exception)
                }
            } else {
                logger.info("Multi device is already active")
            }
        }

        return establishRendezvousConnectionResult
    }

    private suspend fun createdVerifiedRendezvousConnection(): Result<RendezvousConnection> {
        var connection: RendezvousConnection? = null
        return try {
            connection = createConnection()
            val connectedState = deviceLinkingController.onConnected(connection.rph)
            // Wait for the path to be confirmed in the ui (emoji verification)
            connectedState.awaitRendezvousPathConfirmation()
            Result.success(connection)
        } catch (exception: Exception) {
            connection?.close()
            Result.failure(exception)
        }
    }

    @WorkerThread
    private suspend fun activateMultiDevice(handle: ActiveTaskCodec) {
        multiDeviceManager.setProperties(generateMdProperties())
        // TODO(ANDR-2519): Remove when md allows fs by default `activate` could then be non-suspending
        if (!IS_FS_SUPPORTED_WITH_MD) {
            multiDeviceManager.disableForwardSecurity(
                handle,
                contactService,
                userService,
                fsMessageProcessor,
                taskCreator,
            )
        }
        multiDeviceManager.reconnect()
    }

    /**
     * Generate the properties required to activate multi device.
     * This includes device ids and md key material.
     *
     * TODO(ANDR-2487): deviceLabel should be user-selectable (and updatable)
     */
    private fun generateMdProperties(): PersistedMultiDeviceProperties =
        PersistedMultiDeviceProperties(
            registrationTime = null,
            deviceLabel = "Android Client",
            mediatorDeviceId = DeviceId(generateRandomU64()),
            cspDeviceId = DeviceId(generateRandomU64()),
            dgk = generateRandomBytes(D2mProtocolDefines.DGK_LENGTH_BYTES),
        )

    /**
     *  @throws DeviceLinkingInvalidQrCodeException if the [deviceJoinOfferUri] is not in correct form
     */
    private fun parseDeviceJoinOfferUri(deviceJoinOfferUri: String): DeviceGroupJoinRequestOrOffer {
        val parts = deviceJoinOfferUri.split("#")
        if (parts.size != 2 && parts[0] != MultiDeviceManager.DEVICE_JOIN_OFFER_URI_PREFIX) {
            throw DeviceLinkingInvalidQrCodeException("Invalid device join offer uri: $deviceJoinOfferUri")
        }
        val bytes = Base64UrlSafe.decode(parts[1])
        return try {
            DeviceGroupJoinRequestOrOffer.parseFrom(bytes)
        } catch (e: InvalidProtocolBufferException) {
            throw DeviceLinkingInvalidQrCodeException("Could not decode device join offer from uri $deviceLinkingController", e)
        }
    }

    /**
     * Decodes the passed [deviceJoinOfferUri] and tries to create a socket connection.
     *
     * @throws DeviceLinkingUnsupportedProtocolException if the d2d protocol version is too old
     * @throws DeviceLinkingException if the offer version is not in [supportedVersionRange] or the offer variant is not `REQUEST_TO_JOIN`
     */
    private suspend fun createConnection(): RendezvousConnection {
        logger.trace("Join with device join offer uri `{}`", deviceJoinOfferUri)
        val offer = parseDeviceJoinOfferUri(deviceJoinOfferUri)
        logger.trace("Parsed offer: {}", offer)

        if (offer.d2DProtocolVersion < MultiDeviceManager.minimumSupportedD2dProtocolVersion.number) {
            throw DeviceLinkingUnsupportedProtocolException("Unsupported d2d protocol version: ${offer.d2DProtocolVersion}")
        }

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
}

class DeviceLinkingController {
    private val _linkingStatus = MutableSharedFlow<DeviceLinkingStatus>(1, 0)
    val linkingStatus: Flow<DeviceLinkingStatus> =
        _linkingStatus.transformWhile { deviceLinkingStatus ->
            emit(deviceLinkingStatus)
            when (deviceLinkingStatus) {
                is DeviceLinkingStatus.Completed, is DeviceLinkingStatus.Failed -> false
                else -> true
            }
        }

    suspend fun onConnected(rph: ByteArray): DeviceLinkingStatus.Connected =
        DeviceLinkingStatus.Connected(rph).also {
            _linkingStatus.emit(it)
        }

    suspend fun onCompleted() {
        _linkingStatus.emit(DeviceLinkingStatus.Completed)
    }

    suspend fun onFailed(throwable: Throwable?) {
        _linkingStatus.emit(DeviceLinkingStatus.Failed(throwable))
    }
}
