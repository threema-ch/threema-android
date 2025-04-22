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

package ch.threema.app.multidevice

import android.os.Build
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.linking.DeviceLinkingCancelledException
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.multidevice.unlinking.DropDeviceResult
import ch.threema.app.services.ContactService
import ch.threema.app.services.ServerMessageService
import ch.threema.app.services.UserService
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask
import ch.threema.app.tasks.DeleteDeviceGroupTask
import ch.threema.app.tasks.DeviceLinkingController
import ch.threema.app.tasks.FSRefreshStepsTask
import ch.threema.app.tasks.TaskCreator
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.toHexString
import ch.threema.domain.models.IdentityState
import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.ReconnectableServerConnection
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mCloseCode
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseReason
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.D2mProtocolVersion
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.protocol.rendezvous.RendezvousConnection
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.csp.e2e.fs.Terminate
import ch.threema.storage.models.ServerMessageModel
import java.util.Date
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private val logger = LoggingUtil.getThreemaLogger("MultiDeviceManagerImpl")

/**
 * ONLY SET THIS TO `false` WHEN D2D IS IMPLEMENTED AND DOES NOT SUPPORT PFS!!
 * NOTE: If set to `false` the backup version should be incremented, as
 * `ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED` cannot be restored on older versions.
 */
const val IS_FS_SUPPORTED_WITH_MD = false // TODO(ANDR-2519): Remove when md supports fs

class MultiDeviceManagerImpl(
    private val preferenceStore: PreferenceStoreInterface,
    private val serverMessageService: ServerMessageService,
    private val version: Version,
) : MultiDeviceManager {
    private var reconnectHandle: ReconnectableServerConnection? = null

    private var _persistedProperties: PersistedMultiDeviceProperties? = null
    private var _properties = MutableSharedFlow<MultiDeviceProperties?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        CoroutineScope(Dispatchers.Default).launch {
            _persistedProperties = loadProperties()
                .also {
                    _properties.emit(it?.let(::mapPersistedProperties))
                }
        }
    }

    fun setReconnectHandle(reconnectHandle: ReconnectableServerConnection) {
        this.reconnectHandle = reconnectHandle
    }

    /**
     * Setting [persistedProperties] with a new value will also
     *  - persist the properties
     *  - update the value of [properties]
     */
    private var persistedProperties: PersistedMultiDeviceProperties?
        set(value) {
            _persistedProperties = value
            saveProperties(value)
            _properties.tryEmit(value?.let(::mapPersistedProperties))
        }
        get() = _persistedProperties

    private val properties: MultiDeviceProperties?
        get() = runBlocking { _properties.first() }

    override val propertiesProvider: MultiDevicePropertyProvider = MultiDevicePropertyProvider {
        properties ?: throw NullPointerException("MultiDeviceProperties must not be null")
    }

    override val socketCloseListener = D2mSocketCloseListener {
        onSocketClosed(it)
    }

    // TODO(ANDR-2519): Remove when md allows fs
    override val isMdDisabledOrSupportsFs = !isMultiDeviceActive || IS_FS_SUPPORTED_WITH_MD

    override val isMultiDeviceActive: Boolean
        get() = properties != null

    private var serverInfo: InboundD2mMessage.ServerInfo? = null

    /**
     * TODO(ANDR-3763): This will probably also need to start a transaction.
     */
    @WorkerThread
    override suspend fun deactivate(serviceManager: ServiceManager, handle: ActiveTaskCodec) {
        logger.info("Deactivating multi device ...")

        // Delete device group
        logger.info("Delete device group")
        DeleteDeviceGroupTask(serviceManager).invoke(handle)

        // Remove local properties and enable forward security again
        removeMultiDeviceLocally(serviceManager)
    }

    override suspend fun setDeviceLabel(deviceLabel: String) {
        persistedProperties = persistedProperties!!.withDeviceLabel(deviceLabel)
    }

    @WorkerThread
    override suspend fun linkDevice(
        serviceManager: ServiceManager,
        deviceJoinOfferUri: String,
        taskCreator: TaskCreator,
    ): Flow<DeviceLinkingStatus> = channelFlow {
        logger.debug("Linking device started with uri: {}", deviceJoinOfferUri)

        val deviceLinkingController = DeviceLinkingController()

        try {
            launch {
                deviceLinkingController.linkingStatus.collect { deviceLinkingStatus ->
                    send(deviceLinkingStatus)
                }
            }

            val taskOneCancelledSignal = CompletableDeferred<Unit>()
            val linkingPartOneResult: Result<RendezvousConnection> = try {
                taskCreator.scheduleDeviceLinkingPartOneTask(
                    deviceLinkingController = deviceLinkingController,
                    deviceJoinOfferUri = deviceJoinOfferUri,
                    taskCancelledSignal = taskOneCancelledSignal,
                ).await()
            } catch (cancellationException: CancellationException) {
                logger.warn("Linking step one task got cancelled, sending internal cancel signal")
                taskOneCancelledSignal.complete(Unit)
                Result.failure(
                    DeviceLinkingCancelledException(cause = cancellationException),
                )
            }

            linkingPartOneResult.fold(
                onSuccess = {
                    logger.debug("Linking device step one success")
                },
                onFailure = { exception ->
                    // Cause could for example be a MasterKeyLockedException since the data collector
                    // initialises some dependencies when data is collected (e.g. ContactService)
                    // or any other exception that can occur during device join 😉
                    when (exception) {
                        is CancellationException -> logger.warn("Device linking step one was cancelled", exception)
                        else -> logger.error("Device linking step one failed with exception", exception)
                    }
                    deviceLinkingController.onFailed(exception)
                    return@channelFlow
                },
            )

            val taskTwoCancelledSignal = CompletableDeferred<Unit>()
            val linkingPartTwoResult: Result<Unit> = try {
                taskCreator.scheduleDeviceLinkingPartTwoTask(
                    rendezvousConnection = linkingPartOneResult.getOrThrow(),
                    serviceManager = serviceManager,
                    taskCancelledSignal = taskTwoCancelledSignal,
                ).await()
            } catch (cancellationException: CancellationException) {
                logger.warn("Linking step two task got cancelled, sending internal cancel signal")
                taskTwoCancelledSignal.complete(Unit)
                Result.failure(
                    DeviceLinkingCancelledException(cause = cancellationException),
                )
            }

            linkingPartTwoResult.fold(
                onSuccess = {
                    logger.debug("Linking device step two success")
                    deviceLinkingController.onCompleted()
                },
                onFailure = { exception ->
                    when (exception) {
                        is CancellationException -> logger.warn("Device linking was cancelled in step two", exception)
                        else -> logger.error("Device linking failed with exception for step two", exception)
                    }
                    deviceLinkingController.onFailed(exception)
                },
            )
        } catch (exception: Exception) {
            logger.error("Device linking failed with exception", exception)
            deviceLinkingController.onFailed(exception)
        }
    }

    @WorkerThread
    override suspend fun dropDevice(
        deviceId: DeviceId,
        taskCreator: TaskCreator,
        timeout: Duration,
    ): DropDeviceResult {
        if (!isMultiDeviceActive) {
            return DropDeviceResult.Failure.Internal
        }

        logger.debug("Drop device: {}", deviceId.id)

        // drop the device from the current group with a timeout
        try {
            withTimeout(timeout) {
                taskCreator.scheduleDropDeviceTask(deviceId).await()
            }
        } catch (timeoutCancellationException: TimeoutCancellationException) {
            timeoutCancellationException.printStackTrace()
            return DropDeviceResult.Failure.Timeout
        }

        // re-load all remaining devices in the current group
        val remainingLinkedDevicesResult: Result<List<LinkedDevice>> = loadLinkedDevices(taskCreator)

        return DropDeviceResult.Success(
            remainingLinkedDevicesResult = remainingLinkedDevicesResult,
        )
    }

    @AnyThread
    override suspend fun loadLinkedDevices(taskCreator: TaskCreator): Result<List<LinkedDevice>> = withContext(Dispatchers.Default) {
        if (isMultiDeviceActive) {
            taskCreator.scheduleGetLinkedDevicesTask().await().toResult()
        } else {
            Result.failure(IllegalStateException("MD is not active anymore"))
        }
    }

    override suspend fun setProperties(persistedProperties: PersistedMultiDeviceProperties?) {
        this.persistedProperties = persistedProperties
    }

    private fun removeMultiDeviceLocally(serviceManager: ServiceManager) {
        // Delete dgk
        logger.info("Delete multi device properties")
        persistedProperties = null

        // Enable FS
        if (!IS_FS_SUPPORTED_WITH_MD) {
            enableForwardSecurity(serviceManager)
        }

        // Cleanup
        serverInfo = null

        logger.info("Deactivated multi device")

        // Reconnect
        reconnect()
    }

    private fun onSocketClosed(reason: ServerSocketCloseReason) {
        logger.info("Socket was closed with {}", reason)
        if (reason is D2mSocketCloseReason) {
            handleD2mSocketClose(reason)
        }
    }

    private fun handleD2mSocketClose(reason: D2mSocketCloseReason) {
        logger.debug("Latest close reason: {}", reason.closeCode.toString())

        // Handle close codes which do not allow a reconnect
        when (reason.closeCode.code) {
            D2mCloseCode.D2M_UNSUPPORTED_PROTOCOL_VERSION -> handleUnsupportedProtocolVersion()
            D2mCloseCode.D2M_DEVICE_LIMIT_REACHED -> handleDeviceLimitReached()
            D2mCloseCode.D2M_DUPLICATE_CONNECTION -> handleDuplicateConnection()
            D2mCloseCode.D2M_DEVICE_DROPPED -> handleDeviceDropped()
            D2mCloseCode.D2M_REFLECTION_QUEUE_LIMIT_REACHED -> handleDeviceDropped()
            D2mCloseCode.D2M_EXPECTED_DEVICE_SLOT_MISMATCH -> handleDeviceSlotMismatch()
        }
    }

    private fun handleUnsupportedProtocolVersion() {
        displayConnectionError("Unsupported protocol version")
    }

    private fun handleDeviceLimitReached() {
        displayConnectionError("Device limit reached")
    }

    private fun handleDuplicateConnection() {
        displayConnectionError("Duplicate connection")
    }

    private fun handleDeviceDropped() {
        // TODO(ANDR-2604): The dialog should offer the possibility to use threema without server connection
        //  (no messages can be sent or received) or to reset the App (see SE-137)
        displayConnectionError("Device was dropped")
    }

    private fun handleDeviceSlotMismatch() {
        // TODO(ANDR-2604): The dialog should offer the possibility to use threema without server connection
        //  (no messages can be sent or received) or to reset the App (see SE-137)
        logger.error("Device slot mismatch")
        // In this case we just connect to the chat server to allow continuing using the device
        val serviceManager = ThreemaApplication.getServiceManager()
        if (serviceManager != null) {
            removeMultiDeviceLocally(serviceManager)
        } else {
            logger.error("Could not disable multi device locally because the service manager is null")
        }
    }

    /**
     * Display a connection error to the user if a reconnect is not allowed.
     */
    private fun displayConnectionError(msg: String) {
        // TODO(ANDR-2604): Show actual dialog to user
        // TODO(ANDR-2604): Use string resources instead of string
        // TODO(ANDR-2604): Only show error if a reconnect is not allowed (see `D2mCloseCode#isReconnectAllowed()`)
        logger.warn("Reconnect is not allowed: {}", msg)

        val message = ServerMessageModel(msg, ServerMessageModel.TYPE_ERROR)
        serverMessageService.saveIncomingServerMessage(message)
    }

    override fun reconnect() {
        logger.info("Reconnect server connection")
        reconnectHandle?.reconnect() ?: logger.error("Reconnect handle is null")
    }

    @WorkerThread
    override suspend fun disableForwardSecurity(
        handle: ActiveTaskCodec,
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
    ) {
        updateFeatureMask(
            userService = userService,
            isFsAllowed = false,
        )
        contactService.all.forEach { contactModel ->
            DeleteAndTerminateFSSessionsTask(
                fsmp = fsMessageProcessor,
                contact = contactModel,
                cause = Terminate.Cause.DISABLED_BY_LOCAL,
            ).invoke(handle)
        }
        fsMessageProcessor.setForwardSecurityEnabled(false)
    }

    // TODO(ANDR-2519): Remove when md allows fs
    @WorkerThread
    private fun enableForwardSecurity(serviceManager: ServiceManager) {
        val userService = serviceManager.userService
        val contactService = serviceManager.contactService
        val groupService = serviceManager.groupService
        val fsMessageProcessor = serviceManager.forwardSecurityMessageProcessor

        // First update the user's feature mask
        updateFeatureMask(userService, true)

        // Then enable forward security again
        fsMessageProcessor.setForwardSecurityEnabled(true)

        // TODO(ANDR-3583) Logic for FS Refresh Steps is duplicated
        // And afterwards determine the solicited contacts to
        // Find group contacts of groups that are not left
        val groups = groupService.all.filter { groupService.isGroupMember(it) }
        val groupContacts = groups.flatMap(groupService::getMembers).toSet()

        // Find valid contacts with defined last-update flag
        val contactsWithConversation = contactService.all
            .filter { it.lastUpdate != null }
            .toSet()

        // Determine the solicited contacts defined by group contacts and conversation contacts and
        // remove invalid contacts
        val myIdentity = userService.identity
        val solicitedContacts = (groupContacts + contactsWithConversation)
            .filter { it.state != IdentityState.INVALID }
            .filter { it.identity != myIdentity }
            .toSet()

        serviceManager.taskManager.schedule(
            FSRefreshStepsTask(
                solicitedContacts,
                serviceManager,
            ),
        )
    }

    // TODO(ANDR-2519): Do not update feature mask when md supports fs
    @WorkerThread
    private fun updateFeatureMask(userService: UserService, isFsAllowed: Boolean) {
        userService.setForwardSecurityEnabled(isFsAllowed)
        userService.sendFeatureMask()
    }

    private fun mapPersistedProperties(persistedProperties: PersistedMultiDeviceProperties): MultiDeviceProperties {
        return MultiDeviceProperties(
            persistedProperties.registrationTime,
            persistedProperties.mediatorDeviceId,
            persistedProperties.cspDeviceId,
            MultiDeviceKeys(persistedProperties.dgk),
            createDeviceInfo(persistedProperties.deviceLabel),
            D2mProtocolVersion(
                D2mProtocolDefines.D2M_PROTOCOL_VERSION_MIN,
                D2mProtocolDefines.D2M_PROTOCOL_VERSION_MAX,
            ),
            ::updateServerInfo,
        )
    }

    private fun createDeviceInfo(deviceLabel: String): D2dMessage.DeviceInfo {
        val platformDetails = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")

        return D2dMessage.DeviceInfo(
            D2dMessage.DeviceInfo.Platform.ANDROID,
            platformDetails,
            version.versionNumber,
            deviceLabel,
        ).also { logger.trace("Device info created: {}", it) }
    }

    private suspend fun loadProperties(): PersistedMultiDeviceProperties? {
        return withContext(Dispatchers.IO) {
            if (preferenceStore.containsKey(PreferenceStore.PREFS_MD_PROPERTIES, true)) {
                val bytes = preferenceStore.getBytes(PreferenceStore.PREFS_MD_PROPERTIES, true)
                logger.trace("Properties size={}", bytes.size)
                try {
                    // return:
                    PersistedMultiDeviceProperties.deserialize(bytes).also {
                        logger.trace(
                            "Deserialized persisted md properties: {} -> {}",
                            bytes.toHexString(5),
                            it,
                        )
                    }
                } catch (e: PersistedMultiDeviceProperties.DeserializeException) {
                    logger.error("Persisted properties are invalid. Remove properties.", e)
                    preferenceStore.remove(PreferenceStore.PREFS_MD_PROPERTIES, true)
                    // return:
                    null
                }
            } else {
                // return:
                null
            }
        }
    }

    /**
     * Store the properties in the preference store. If [properties] is null
     * any persisted property will be removed.
     */
    @AnyThread
    private fun saveProperties(properties: PersistedMultiDeviceProperties?) {
        CoroutineScope(Dispatchers.IO).launch {
            if (properties == null) {
                logger.info("Delete md properties")
                preferenceStore.remove(PreferenceStore.PREFS_MD_PROPERTIES, true)
            } else {
                val bytes = properties.serialize()
                logger.trace("Serialize md properties: {} -> {}", properties, bytes.toHexString(5))
                preferenceStore.save(
                    PreferenceStore.PREFS_MD_PROPERTIES,
                    properties.serialize(),
                    true,
                )
            }
        }
    }

    @AnyThread
    private fun updateServerInfo(serverInfo: InboundD2mMessage.ServerInfo) {
        this.serverInfo = serverInfo
        // TODO(ANDR-2580): Trigger whatever is required based on the server info
        CoroutineScope(Dispatchers.Default).launch {
            preferenceStore.save(
                PreferenceStore.PREFS_MD_MEDIATOR_MAX_SLOTS,
                serverInfo.maxDeviceSlots.toLong(),
            )
            updateRegistrationTime()
        }
    }

    @AnyThread
    private fun updateRegistrationTime() {
        persistedProperties!!.let { properties ->
            if (properties.registrationTime == null) {
                logger.debug("Set time of first registration")
                persistedProperties = properties.withRegistrationTime(Date().time.toULong())
            } else {
                logger.debug("Registration time already set. Ignore ServerInfo")
            }
        }
    }
}
