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

package ch.threema.app.multidevice

import android.os.Build
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.BuildConfig
import ch.threema.app.multidevice.linking.DeviceJoinData
import ch.threema.app.multidevice.linking.DeviceJoinDataCollector
import ch.threema.app.services.ContactService
import ch.threema.app.services.ServerMessageService
import ch.threema.app.services.UserService
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask
import ch.threema.app.tasks.OutgoingDropDeviceTask
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.SecureRandomUtil.generateRandomBytes
import ch.threema.base.utils.SecureRandomUtil.generateRandomU64
import ch.threema.base.utils.toHexString
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
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.protobuf.csp.e2e.fs.Terminate
import ch.threema.storage.models.ServerMessageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.saltyrtc.client.exceptions.InvalidStateException
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("MultiDeviceManagerImpl")

/** ONLY SET THIS TO `false` WHEN D2D IS IMPLEMENTED AND DOES NOT SUPPORT PFS!!
 * NOTE: If set to `false` the backup version should be incremented, as
 * `ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED` cannot be restored on older versions.
 */
private const val IS_FS_SUPPORTED_WITH_MD = true // TODO(ANDR-2519): Remove when md supports fs

class MultiDeviceManagerImpl(
    private val reconnectHandle: ReconnectableServerConnection,
    private val preferenceStore: PreferenceStoreInterface,
    private val serverMessageService: ServerMessageService,
    private val version: Version,
    private val deviceJoinDataCollector: DeviceJoinDataCollector
    ) : MultiDeviceManager {

    private var _persistedProperties: PersistedMultiDeviceProperties? = null
    private var _properties = MutableSharedFlow<MultiDeviceProperties?>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        CoroutineScope(Dispatchers.Default).launch {
            _persistedProperties = loadProperties()
                .also {
                    _properties.emit(it?.let(::mapPersistedProperties)) }
        }
    }

    /**
     * Setting [persistedProperties] with a new value will also
     *  - persist the properties
     *  - update the value of [properties]
     */
    private var persistedProperties: PersistedMultiDeviceProperties?
        set(value) {
            _persistedProperties = value
            storeProperties(value)
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

    private val _linkedDevices = mutableListOf<String>()
    override val linkedDevices: List<String>
        get() = _linkedDevices // TODO(ANDR-2484): persist linked devices

    override val latestSocketCloseReason = MutableSharedFlow<D2mSocketCloseReason?>(1, 0, BufferOverflow.DROP_OLDEST)

    private var serverInfo: InboundD2mMessage.ServerInfo? = null

    private var deactivationOngoing = false

    @AnyThread
    override suspend fun activate(
        deviceLabel: String,
        taskManager: TaskManager,
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor
    ) {
        logger.info("Activate multi device")
        if (!BuildConfig.MD_ENABLED) {
            logger.warn("Md is disabled in build configuration")
            return
        }

        if (isMultiDeviceActive) {
            logger.info("MD is already activated")
            return
        }

        persistedProperties = generateProperties(deviceLabel)

        // TODO(ANDR-2519): Remove when md allows fs by default `activate` could then be non-suspending
        if (!IS_FS_SUPPORTED_WITH_MD) {
            disableForwardSecurity(taskManager, contactService, userService, fsMessageProcessor)
        }
        latestSocketCloseReason.tryEmit(null)
        reconnect()
    }

    @AnyThread
    override suspend fun deactivate(
        taskManager: TaskManager,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor
    ) {
        logger.debug("Deactivate multi device")

        val mdProperties = properties ?: throw MultiDeviceException("Multi device properties are missing")

        // TODO(ANDR-2519): Remove when md allows fs by default
        if (!IS_FS_SUPPORTED_WITH_MD) {
            enableForwardSecurity(userService, fsMessageProcessor)
        }

        serverInfo = null
        _linkedDevices.clear()

        deactivationOngoing = true

        taskManager.schedule(OutgoingDropDeviceTask(mdProperties.mediatorDeviceId)).await()

        // TODO(ANDR-2603): Unlink all linked devices (including own device id):
        //  Ensure all linked devices are removed, then kick own device. When the connection is closed by the mediator with
        //  a close code "kicked from group" the dgk can be deleted an md deactivated. It
        //  would be even nicer if we can wait for the drop device ack of the own device
        //  and then complete the task without being cancelled. This should be possible if the code
        //  executed after the drop device ack is not cancellable.
        //  Will it be possible to trigger a reconnect from the task?
        //  There should probably be a dedicated task, that ensures that _all_ other devices are dropped and only then
        //  drops the own device. If we are sure every other device is dropped and no device could be linked in the meantime
        //  the task could still trigger deletion of the properties if connection to the mediator is not possible anymore because
        //  the own device has already been dropped.
        //   oh no -> if no connection is possible, no tasks will be executed...
    }

    override suspend fun setDeviceLabel(deviceLabel: String) {
        persistedProperties = persistedProperties!!.withDeviceLabel(deviceLabel)
    }

    @AnyThread
    override suspend fun linkDevice(deviceJoinOfferUri: String) {
        logger.debug("Link device: {}", deviceJoinOfferUri)

        _linkedDevices.add(deviceJoinOfferUri)
        // TODO(ANDR-2484): Actual device linking

        return try {
            val deviceJoinData = withContext(Dispatchers.Default) {
                collectDeviceJoinData()
            }
            deviceJoinData.essentialData.toString().lines().forEach {
                logger.debug("Essential data: {}", it)
            }
        } catch (e: Exception) {
            // This could for example be a MasterKeyLockedException since the data collector
            // initialises some dependencies when data is collected (e.g. ContactService)
            logger.error("Linking failed due to an exception", e)
            // TODO(ANDR-2484): rethrow (dedicated type?) and abort linking
            // TODO(ANDR-2487): show a message to users that linking failed
        }
    }

    @WorkerThread
    private fun collectDeviceJoinData(): DeviceJoinData {
        // TODO(ANDR-2484): Make sure the state of the data cannot change during collection:
        //  - disconnect from server
        //  - do not perform any api calls?
        //  - disconnect web clients
        //  - stop workers..?
        //  --> how is this done during a backup?

        val dgk = properties?.keys?.dgk ?: throw InvalidStateException("Multi device is not active")

        return deviceJoinDataCollector.collectData(dgk)
    }

    private fun onSocketClosed(reason: ServerSocketCloseReason) {
        logger.info("Socket was closed with {}", reason)
        if (reason is D2mSocketCloseReason) {
            handleD2mSocketClose(reason)
        }
    }

    private fun handleD2mSocketClose(reason: D2mSocketCloseReason) {
        latestSocketCloseReason.tryEmit(reason)

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
        if (deactivationOngoing) {
            logger.debug("Device dropped during ongoing md deactivation. Delete properties.")
            // complete deactivation: delete dgk etc.
            persistedProperties = null
            deactivationOngoing = false
            reconnect()
        } else {
            displayConnectionError("Device was dropped")
        }
    }

    private fun handleDeviceSlotMismatch() {
        displayConnectionError("Device slot mismatch")

        // TODO(ANDR-2603): Remove
        deleteMdPropertiesAfterSlotMismatch()
    }

    private fun deleteMdPropertiesAfterSlotMismatch() {
        // TODO(ANDR-2603): Remove this, as it is just a temporary workaround for an unsuccessful
        //  md deactivation.
        //  If deactivation of md has not been properly completed the client might already be dropped,
        //  but the properties are not yet deleted.
        //  In that state it is not possible to login on the server (expected slot mismatch) and therefore
        //  a drop device cannot be sent (and actually does not have to, since the device has already
        //  been dropped).
        //  How to handle that case? How does iOS handle this situation?
        //  Only if we are sure there are no other remaining devices in the device group md should be deactivated.
        //  --> could lead to many "Another connection ..." server errors
        logger.warn("Delete md properties after device slot mismatch")
        deactivationOngoing = false
        persistedProperties = null

        // We do not reconnect automatically. After a restart of the app the csp connection will be used
        // which should work. This way we could display an error to the user which will allow to react somehow
        // before the connection is changed. Or there might even be a button "reconnect without md" in the
        // shown dialog
    }

    /**
     * Display a connection error to the user if a reconnect is not allowed.
     */
    private fun displayConnectionError(msg: String) {
        // TODO(ANDR-2604): Show actual dialog to user
        // TODO(ANDR-2604): Use string resources instead of string
        // TODO(ANDR-2604): Only show error if a reconnect ist not allowed (see `D2mCloseCode#isReconnectAllowed()`)
        logger.warn("Reconnect is not allowed: {}", msg)

        val message = ServerMessageModel(msg, ServerMessageModel.TYPE_ERROR)
        serverMessageService.saveIncomingServerMessage(message)
    }

    private fun reconnect() {
        CoroutineScope(Dispatchers.Default).launch {
            reconnectHandle.reconnect()
        }
    }

    // TODO(ANDR-2519): Remove when md allows fs
    @AnyThread
    private suspend fun disableForwardSecurity(
        taskManager: TaskManager,
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor
    ) {
        withContext(Dispatchers.IO) {
            updateFeatureMask(userService, false)
            terminateAllForwardSecuritySessions(
                taskManager,
                contactService,
                fsMessageProcessor
            )
            fsMessageProcessor.setForwardSecurityEnabled(false)
        }
    }

    // TODO(ANDR-2519): Remove when md allows fs
    @AnyThread
    private suspend fun enableForwardSecurity(
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor
    ) {
        withContext(Dispatchers.IO) {
            updateFeatureMask(userService, true)
            fsMessageProcessor.setForwardSecurityEnabled(true)
            // TODO(ANDR-2872): Run FS Refresh Steps
        }
    }

    /**
     * This terminates and deletes all forward security sessions that exist with other contacts.
     */
    // TODO(ANDR-2519): Remove when md allows fs
    @WorkerThread
    private suspend fun terminateAllForwardSecuritySessions(
        taskManager: TaskManager,
        contactService: ContactService,
        fsMessageProcessor: ForwardSecurityMessageProcessor
    ) {
        contactService.all.map {
            taskManager.schedule(
                DeleteAndTerminateFSSessionsTask(
                    fsMessageProcessor, it, Terminate.Cause.DISABLED_BY_LOCAL
                )
            )
        }.awaitAll()
    }

    // TODO(ANDR-2519): Do not update feature mask when md supports fs
    @WorkerThread
    private fun updateFeatureMask(userService: UserService, isFsAllowed: Boolean) {
        userService.setForwardSecurityEnabled(isFsAllowed)
        userService.sendFeatureMask()
    }

    /**
     * Generate the properties required to activate multi device.
     * This includes device ids and md key material.
     */
    private fun generateProperties(deviceLabel: String): PersistedMultiDeviceProperties {
        return PersistedMultiDeviceProperties(
            null,
            deviceLabel,
            DeviceId(generateRandomU64()),
            DeviceId(generateRandomU64()),
            generateRandomBytes(D2mProtocolDefines.DGK_LENGTH_BYTES)
        )
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
                D2mProtocolDefines.D2M_PROTOCOL_VERSION_MAX
            ),
            ::updateServerInfo
        )
    }

    private fun createDeviceInfo(deviceLabel: String): D2dMessage.DeviceInfo {
        val platformDetails = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")

        return D2dMessage.DeviceInfo(
            D2dMessage.DeviceInfo.Platform.ANDROID,
            platformDetails,
            version.version,
            deviceLabel
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
                        logger.trace("Deserialized persisted md properties: {} -> {}", bytes.toHexString(5), it)
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
    private fun storeProperties(properties: PersistedMultiDeviceProperties?) {
        CoroutineScope(Dispatchers.IO).launch {
            if (properties == null) {
                logger.info("Delete md properties")
                preferenceStore.remove(PreferenceStore.PREFS_MD_PROPERTIES, true)
            } else {
                val bytes = properties.serialize()
                logger.trace("Serialize md properties: {} -> {}", properties, bytes.toHexString(5))
                preferenceStore.save(PreferenceStore.PREFS_MD_PROPERTIES, properties.serialize(), true)
            }
        }
    }

    @AnyThread
    private fun updateServerInfo(serverInfo: InboundD2mMessage.ServerInfo) {
        this.serverInfo = serverInfo
        // TODO(ANDR-2580): Trigger whatever is required based on the server info
        CoroutineScope(Dispatchers.Default).launch {
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
