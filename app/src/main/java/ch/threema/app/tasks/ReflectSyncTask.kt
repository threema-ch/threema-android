package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TransactionScope.TransactionException
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.TransactionScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class ReflectSyncTask<TransactionResult, TaskResult>(
    private val transactionScope: TransactionScope.Scope,
) : KoinComponent {
    protected val multiDeviceManager: MultiDeviceManager by inject()
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
    protected abstract val runAfterSuccessfulTransaction: suspend (transactionResult: TransactionResult) -> TaskResult

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
            precondition = runPrecondition,
        ).execute {
            runInsideTransaction(handle)
        }

        return runAfterSuccessfulTransaction(transactionResult)
    }
}
