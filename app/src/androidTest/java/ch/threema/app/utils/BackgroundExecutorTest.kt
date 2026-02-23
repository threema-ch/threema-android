package ch.threema.app.utils

import android.os.Looper
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.app.utils.executor.BackgroundTask
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.Timeout

class BackgroundExecutorTest {
    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(10)

    private val executor = BackgroundExecutor()

    @Test
    fun testCorrectThreads() {
        val initialThread = Thread.currentThread()

        executor.execute(object : BackgroundTask<Unit> {
            override fun runBefore() {
                assertEquals(initialThread.id, Thread.currentThread().id)
            }

            override fun runInBackground() {
                val currentThreadId = Thread.currentThread().id
                assertNotEquals(initialThread.id, currentThreadId)
                assertNotEquals(Looper.getMainLooper().thread.id, currentThreadId)
            }

            override fun runAfter(result: Unit) {
                assertEquals(Looper.getMainLooper().thread.id, Thread.currentThread().id)
            }
        })
    }

    @Test
    fun testReturnValue() {
        executor.execute(object : BackgroundTask<Int> {
            override fun runInBackground() = 42
            override fun runAfter(result: Int) = assertEquals(42, result)
        })
    }

    @Test
    fun testOrder() = runBlocking {
        val methodExecutionList = mutableListOf<Int>()
        val expected = arrayOf(0, 1, 2, 3, 4, 5)

        executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runBefore() {
                methodExecutionList.add(0)
                Thread.sleep(500)
                methodExecutionList.add(1)
            }

            override fun runInBackground() {
                methodExecutionList.add(2)
                Thread.sleep(300)
                methodExecutionList.add(3)
            }

            override fun runAfter(result: Unit) {
                methodExecutionList.add(4)
                Thread.sleep(200)
                methodExecutionList.add(5)

                // Sleep again to test that the completable is completed after the runAfter method
                // is run.
                Thread.sleep(500)
            }
        }).await()

        assertContentEquals(
            expected,
            methodExecutionList.toTypedArray(),
        )
    }

    @Test
    fun testFailingBefore() {
        val deferred = executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runBefore() {
                throw IllegalStateException()
            }

            override fun runInBackground() {
                // This should never be executed as run before failed
                fail()
            }

            override fun runAfter(result: Unit) {
                // This should never be executed as run before failed
                fail()
            }
        })

        assertFailsWith<IllegalStateException> {
            runBlocking {
                deferred.await()
            }
        }
    }

    @Test
    fun testFailingBackground() {
        val deferred = executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runInBackground() {
                throw IllegalStateException()
            }

            override fun runAfter(result: Unit) {
                // This should never be executed as run in background failed
                fail()
            }
        })

        assertFailsWith<IllegalStateException> {
            runBlocking {
                deferred.await()
            }
        }
    }

    @Test
    fun testFailingAfter() {
        val deferred = executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runInBackground() {
                // Nothing to do
            }

            override fun runAfter(result: Unit) {
                throw IllegalStateException()
            }
        })

        assertFailsWith<IllegalStateException> {
            runBlocking {
                deferred.await()
            }
        }
    }
}
