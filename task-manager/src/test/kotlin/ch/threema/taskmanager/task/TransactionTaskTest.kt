package ch.threema.taskmanager.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal open class FakeTransactionHandler : TransactionHandler {
    var isInitialized = false
    var isFinished = false
    override suspend fun init() {
        isInitialized = true
    }

    override suspend fun finish() {
        isFinished = true
    }
}

internal class TransactionHandlerProviderDelegate(
    override val transactionHandler: FakeTransactionHandler
) : TransactionHandlerProvider

internal open class TestTask(
    transactionHandler: FakeTransactionHandler = FakeTransactionHandler()
) : Task<Unit>, TransactionTask<Unit>,
    TransactionHandlerProvider by TransactionHandlerProviderDelegate(transactionHandler) {
    var wasTaskExecuted = false
    var wasTransactionExecuted = false
    var checkedPreconditionCount = 0
    open var preconditionSatisfied = true

    override suspend fun invoke(scope: CoroutineScope) {
        wasTaskExecuted = true
        transaction {
            wasTransactionExecuted = true
        }
    }

    override fun checkTransactionPrecondition(): Boolean {
        checkedPreconditionCount++
        return preconditionSatisfied
    }
}

@ExperimentalCoroutinesApi
internal class TransactionTaskTest {

    @Test
    fun `transaction executes inner transaction lambda`() = runBlockingTest {
        val task = TestTask()
        task.invoke(this)
        assertTrue(task.wasTaskExecuted)
    }

    @Test()
    fun `transaction throws on failed precondition`() = runBlockingTest {
        val task = TestTask()
        task.preconditionSatisfied = false

        assertThrows<TransactionPreconditionFailedException> {
            this.runBlockingTest {
                task.invoke(this)
            }
        }
        assertTrue(task.wasTaskExecuted)
    }

    @Test
    fun `transaction throws on failed precondition after transaction handling initialization`() = runBlockingTest {
        val transactionHandler = object : FakeTransactionHandler() {
            var task: TestTask? = null
            override suspend fun init() {
                task?.preconditionSatisfied = false
            }
        }

        val task = TestTask(transactionHandler)

        transactionHandler.task = task

        assertThrows<TransactionPreconditionFailedException> {
            this.runBlockingTest {
                task.invoke(this)
            }
        }
        assertTrue(task.wasTaskExecuted)
    }

    @Test
    fun `transaction task initializes transaction handling before lambda execution`() = runBlockingTest {
        val transactionHandler = FakeTransactionHandler()
        val task = object : TestTask(transactionHandler) {
            override suspend fun invoke(scope: CoroutineScope) {
                assertFalse(transactionHandler.isInitialized)
                transaction {
                    assertTrue(transactionHandler.isInitialized)
                }
            }
        }

        task.invoke(this)
    }

    @Test
    fun `transaction task finishes transaction handling after lambda execution`() = runBlockingTest {
        val transactionHandler = FakeTransactionHandler()
        val task = object : TestTask(transactionHandler) {
            override suspend fun invoke(scope: CoroutineScope) {
                transaction {
                    assertFalse(transactionHandler.isFinished)
                }
                assertTrue(transactionHandler.isFinished)
            }
        }

        task.invoke(this)
    }
}
