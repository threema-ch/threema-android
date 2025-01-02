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
import ch.threema.app.multidevice.linking.DeviceLinkingCancelledException
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.multidevice.linking.Failed
import ch.threema.app.services.ContactService
import ch.threema.app.services.ServerMessageService
import ch.threema.app.services.UserService
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.TaskCreator
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
import ch.threema.protobuf.csp.e2e.fs.Terminate
import ch.threema.storage.models.ServerMessageModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("MultiDeviceManagerImpl")

/** ONLY SET THIS TO `false` WHEN D2D IS IMPLEMENTED AND DOES NOT SUPPORT PFS!!
 * NOTE: If set to `false` the backup version should be incremented, as
 * `ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED` cannot be restored on older versions.
 */
private const val IS_FS_SUPPORTED_WITH_MD = false // TODO(ANDR-2519): Remove when md supports fs

class MultiDeviceManagerImpl(
    private val preferenceStore: PreferenceStoreInterface,
    private val serverMessageService: ServerMessageService,
    private val version: Version,
    ) : MultiDeviceManager {

    private var reconnectHandle: ReconnectableServerConnection? = null

    private var _persistedProperties: PersistedMultiDeviceProperties? = null
    private var _properties = MutableSharedFlow<MultiDeviceProperties?>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        CoroutineScope(Dispatchers.Default).launch {
            _persistedProperties = loadProperties()
                .also {
                    _properties.emit(it?.let(::mapPersistedProperties)) }
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

    override val latestSocketCloseReason = MutableSharedFlow<D2mSocketCloseReason?>(1, 0, BufferOverflow.DROP_OLDEST)

    private var serverInfo: InboundD2mMessage.ServerInfo? = null

    @AnyThread
    override suspend fun activate(
        deviceLabel: String,
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
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
            disableForwardSecurity(contactService, userService, fsMessageProcessor, taskCreator)
        }
        latestSocketCloseReason.tryEmit(null)
        reconnect()
    }

    @AnyThread
    override suspend fun deactivate(
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
    ) {
        logger.info("Deactivate multi device")

        // 1. Delete device group
        logger.info("Delete device group")
        taskCreator.scheduleDeleteDeviceGroupTask().await()

        // 2. Delete dgk
        logger.info("Delete multi device properties")
        persistedProperties = null

        // TODO(ANDR-2519): Remove when md allows fs by default
        // 3. Enable FS
        if (!IS_FS_SUPPORTED_WITH_MD) {
            enableForwardSecurity(userService, fsMessageProcessor)
        }

        // 4. Cleanup
        serverInfo = null

        // 5. Reconnect
        reconnect()
    }

    override suspend fun setDeviceLabel(deviceLabel: String) {
        persistedProperties = persistedProperties!!.withDeviceLabel(deviceLabel)
    }

    @WorkerThread
    override suspend fun linkDevice(
        deviceJoinOfferUri: String,
        taskCreator: TaskCreator,
    ): Flow<DeviceLinkingStatus> {
        logger.debug("Link device: {}", deviceJoinOfferUri)

        return channelFlow {
            try {
                val linkingCancelledSignal = CompletableDeferred<Unit>()

                val (controller, linkingCompleted) = taskCreator.scheduleDeviceLinkingTask(deviceJoinOfferUri, linkingCancelledSignal)

                launch {
                    controller.linkingStatus.collect { send(it) }
                }

                val result = try {
                    linkingCompleted.await()
                } catch (e: CancellationException) {
                    linkingCancelledSignal.complete(Unit)
                    Result.failure(DeviceLinkingCancelledException())
                }
                if (result.isFailure) {
                    // Cause could for example be a MasterKeyLockedException since the data collector
                    // initialises some dependencies when data is collected (e.g. ContactService)
                    // or any other exception that can occur during device join 😉
                    logger.error("Linking failed due to an exception")
                    send(Failed(result.exceptionOrNull()))
                }
            } catch (e: Exception) {
                send(Failed(e))
            }
        }
    }

    // TODO(ANDR-2717): Remove
    override suspend fun purge(taskCreator: TaskCreator) {
        val myDeviceId = (properties ?: throw MultiDeviceException("Multi device properties are missing")).mediatorDeviceId
        loadLinkedDevicesMediatorIds(taskCreator)
            .filter { it != myDeviceId }
            .forEach {
                taskCreator.scheduleDropDeviceTask(it).await()
            }
    }

    // TODO(ANDR-2717): Use a Proper model (probably `List<DeviceInfo>`) `List<String>` is only used
    //  for the sake of simplicity during development
    @AnyThread
    override suspend fun loadLinkedDevicesInfo(taskCreator: TaskCreator): List<String> {
        if (!isMultiDeviceActive) {
            return listOf()
        }
        val keys = _properties.filterNotNull().first().keys
        return withContext(Dispatchers.Default) {
            val devicesInfo = taskCreator.scheduleGetDevicesInfoTask().await()
            devicesInfo.augmentedDeviceInfo.values.map { augmentedDeviceInfo ->
                val deviceInfo = try {
                    keys.decryptDeviceInfo(augmentedDeviceInfo.encryptedDeviceInfo)
                } catch (e: Exception) {
                    logger.error("Could not decrypt device info", e)
                    // TODO(ANDR-2717): Display as invalid device in devices list
                    D2dMessage.DeviceInfo.INVALID_DEVICE_INFO
                }
                val activityInfo = augmentedDeviceInfo.connectedSince?.let { "Connected since ${Date(it.toLong())}" }
                    ?: augmentedDeviceInfo.lastDisconnectAt?.let { "Last disconnect: ${Date(it.toLong())}" }
                listOfNotNull(
                    deviceInfo.label,
                    deviceInfo.platform,
                    "${deviceInfo.platformDetails} (${deviceInfo.appVersion})",
                    activityInfo
                ).joinToString("\n")
            }
        }
    }

    @AnyThread
    private suspend fun loadLinkedDevicesMediatorIds(taskCreator: TaskCreator): Set<DeviceId> {
        return withContext(Dispatchers.Default) {
            taskCreator.scheduleGetDevicesInfoTask().await().augmentedDeviceInfo.keys
        }
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
        // TODO(ANDR-2604): The dialog should offer the possibility to use threema without server connection
        //  (no messages can be sent or received) or to reset the App (see SE-137)
        displayConnectionError("Device was dropped")
    }

    private fun handleDeviceSlotMismatch() {
        // TODO(ANDR-2604): The dialog should offer the possibility to use threema without server connection
        //  (no messages can be sent or received) or to reset the App (see SE-137)
        displayConnectionError("Device slot mismatch")
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

    private fun reconnect() {
        CoroutineScope(Dispatchers.Default).launch {
            logger.info("Reconnect server connection")
            reconnectHandle?.reconnect() ?: logger.error("Reconnect handle is null")
        }
    }

    // TODO(ANDR-2519): Remove when md allows fs
    @AnyThread
    private suspend fun disableForwardSecurity(
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
    ) {
        withContext(Dispatchers.IO) {
            updateFeatureMask(userService, false)
            terminateAllForwardSecuritySessions(contactService, taskCreator)
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
        contactService: ContactService,
        taskCreator: TaskCreator,
    ) {
        contactService.all.map {
            taskCreator.scheduleDeleteAndTerminateFSSessionsTaskAsync(
                it, Terminate.Cause.DISABLED_BY_LOCAL
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
            version.versionNumber,
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
