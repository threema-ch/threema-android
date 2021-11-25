package ch.threema.taskmanager.task

/**
 * Handler that controls a transaction's initialization and finishing.
 */
interface TransactionHandler {
    suspend fun init()
    suspend fun finish()
}

/**
 * Provide a Transaction Handler suited for the current environment (e.g. a
 * {@see NullTransactionHandler} if MultiDevice is disabled.)
 */
interface TransactionHandlerProvider {
    val transactionHandler: TransactionHandler
}

/**
 * Transaction handler that does not initiate any transaction at all.
 */
class NullTransactionHandler : TransactionHandler {
    override suspend fun init() { /* no-op */ }
    override suspend fun finish() { /* no-op */ }
}

/**
 * Denotes a {Task} that requires to be run inside a Mediator-Transaction.
 */
interface TransactionTask<R> : TransactionHandlerProvider, Task<R> {
    @Throws(TransactionPreconditionFailedException::class)
    fun checkTransactionPrecondition(): Boolean
}

class TransactionPreconditionFailedException : Exception()

/**
 * Executes a transaction block with the help of a specific TransactionHelper.
 *
 * @throws TransactionPreconditionFailedException if - you guessed it - the precondition failed.
 */
suspend fun <T, R> TransactionTask<R>.transaction(block: suspend () -> T): T {
    if (!checkTransactionPrecondition()) {
        throw TransactionPreconditionFailedException()
    }
    try {
        transactionHandler.init()
        if (!checkTransactionPrecondition()) throw TransactionPreconditionFailedException()
        return block.invoke()
    } finally {
        transactionHandler.finish()
    }
}
