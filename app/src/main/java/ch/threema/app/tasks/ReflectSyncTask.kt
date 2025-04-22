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

package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TransactionScope.TransactionException
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D.TransactionScope.Scope

abstract class ReflectSyncTask<TransactionResult, TaskResult>(
    protected val multiDeviceManager: MultiDeviceManager,
    private val transactionScope: Scope,
) {
    protected val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    /**
     * This is run as the precondition of the transaction that is used when reflecting the sync with
     * [reflectSync].
     */
    protected abstract val runPrecondition: () -> Boolean

    /**
     * This is run inside the transaction of the sync reflection in [reflectSync].
     */
    protected abstract val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> TransactionResult

    /**
     * This is run after the transaction has been successfully executed.
     */
    protected abstract val runAfterSuccessfulTransaction: (transactionResult: TransactionResult) -> TaskResult

    /**
     * The transaction ttl that is used for the transaction in [reflectSync].
     */
    protected open val transactionTTL: UInt = TRANSACTION_TTL_MAX

    /**
     * Reflect the sync. Note that this creates a transaction with [transactionTTL] and runs
     * [runPrecondition] as precondition of it. Inside the transaction [runInsideTransaction] is
     * run.
     *
     * @throws IllegalStateException if multi device is not active
     * @throws TransactionException if the precondition fails
     */
    protected suspend fun reflectSync(handle: ActiveTaskCodec): TaskResult {
        check(multiDeviceManager.isMultiDeviceActive) {
            "Multi device is not active and a sync must not be reflected"
        }

        val transactionResult = handle.createTransaction(
            keys = mdProperties.keys,
            scope = transactionScope,
            ttl = transactionTTL,
        ) {
            runPrecondition()
        }.execute {
            runInsideTransaction(handle)
        }

        return runAfterSuccessfulTransaction(transactionResult)
    }
}
