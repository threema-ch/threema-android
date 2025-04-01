/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app

import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.stores.IdentityStore
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.TaskCreator
import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import ch.threema.domain.helpers.TransactionAckTaskCodec
import ch.threema.domain.models.AppVersion
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseReason
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.D2mProtocolVersion
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseServiceNew
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TestCoreServiceManager(
    override val version: AppVersion,
    override val databaseService: DatabaseServiceNew,
    override val preferenceStore: PreferenceStoreInterface,
    override val taskArchiver: TaskArchiver = TestTaskArchiver(),
    override val deviceCookieManager: DeviceCookieManager = TestDeviceCookieManager(),
    override val taskManager: TaskManager = TestTaskManager(TransactionAckTaskCodec()),
    override val multiDeviceManager: MultiDeviceManager = TestMultiDeviceManager(),
    override val identityStore: IdentityStore = IdentityStore(preferenceStore),
    override val nonceFactory: NonceFactory = NonceFactory(TestNonceStore()),
) : CoreServiceManager

class TestTaskArchiver(initialTasks: List<Task<*, TaskCodec>> = emptyList()) : TaskArchiver {
    private val archivedTasks: MutableList<Task<*, TaskCodec>> = initialTasks.toMutableList()

    override fun addTask(task: Task<*, TaskCodec>) {
        archivedTasks.add(task)
    }

    override fun removeTask(task: Task<*, TaskCodec>) {
        val index = archivedTasks.indexOf(task)
        if (index < 0) {
            return
        }

        if (index == 0) {
            archivedTasks.removeAt(index)
        } else {
            throw AssertionError("Task $index is removed, but it is not the oldest task")
        }
    }

    override fun loadAllTasks(): List<Task<*, TaskCodec>> {
        return archivedTasks
    }
}

class TestDeviceCookieManager : DeviceCookieManager {
    override fun obtainDeviceCookie() = ByteArray(16)
    override fun changeIndicationReceived() {
        // Nothing to do
    }

    override fun deleteDeviceCookie() {
        // Nothing to do
    }
}

class TestTaskManager(
    val taskCodec: TaskCodec
) : TaskManager {
    private val taskQueue = Channel<QueueElement<Any>>()

    private data class QueueElement<T>(
        val task: Task<T, TaskCodec>,
        val deferred: CompletableDeferred<T>,
    )

    init {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val (task, deferred) = taskQueue.receive()
                try {
                    deferred.complete(task.invoke(taskCodec))
                } catch (e: Throwable) {
                    deferred.completeExceptionally(e)
                }
            }
        }
    }

    override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
        val deferred = CompletableDeferred<R>()
        runBlocking {
            @Suppress("UNCHECKED_CAST")
            taskQueue.send(QueueElement(task, deferred) as QueueElement<Any>)
        }
        return deferred
    }

    override fun hasPendingTasks() = false

    override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        // Nothing to do
    }

    override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        // Nothing to do
    }
}

class TestMultiDeviceManager(
    override val isMdDisabledOrSupportsFs: Boolean = true,
    override val isMultiDeviceActive: Boolean = false,
    override val propertiesProvider: MultiDevicePropertyProvider = TestMultiDevicePropertyProvider,
    override val socketCloseListener: D2mSocketCloseListener = D2mSocketCloseListener { },
    override val latestSocketCloseReason: Flow<D2mSocketCloseReason?> = flowOf(),
) : MultiDeviceManager {
    override suspend fun activate(
        deviceLabel: String,
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
    ) {
        throw AssertionError("Not supported")
    }

    override suspend fun deactivate(
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
    ) {
        throw AssertionError("Not supported")
    }

    override suspend fun setDeviceLabel(deviceLabel: String) {
        throw AssertionError("Not supported")
    }

    override suspend fun linkDevice(
        deviceJoinOfferUri: String,
        taskCreator: TaskCreator,
    ): Flow<DeviceLinkingStatus> {
        throw AssertionError("Not supported")
    }

    override suspend fun purge(taskCreator: TaskCreator) {
        throw AssertionError("Not supported")
    }

    override suspend fun loadLinkedDevicesInfo(taskCreator: TaskCreator): List<String> {
        throw AssertionError("Not supported")
    }
}

class TestNonceStore : NonceStore {
    override fun exists(scope: NonceScope, nonce: Nonce) = false

    override fun store(scope: NonceScope, nonce: Nonce) = true

    override fun getCount(scope: NonceScope) = 0L

    override fun getAllHashedNonces(scope: NonceScope): List<HashedNonce> = emptyList()

    override fun addHashedNoncesChunk(
        scope: NonceScope,
        chunkSize: Int,
        offset: Int,
        nonces: MutableList<HashedNonce>,
    ) {
        // Nothing to do
    }

    override fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>) = true

}

object TestMultiDevicePropertyProvider : MultiDevicePropertyProvider {
    override fun get() =
        MultiDeviceProperties(
            0u,
            DeviceId(0u),
            DeviceId(0u),
            MultiDeviceKeys(ByteArray(D2mProtocolDefines.DGK_LENGTH_BYTES)),
            D2dMessage.DeviceInfo(
                D2dMessage.DeviceInfo.Platform.ANDROID,
                "",
                "",
                ""
            ),
            D2mProtocolVersion(UInt.MIN_VALUE, UInt.MAX_VALUE)
        ) { }
}
