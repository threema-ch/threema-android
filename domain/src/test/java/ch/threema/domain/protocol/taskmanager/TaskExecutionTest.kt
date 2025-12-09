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

package ch.threema.domain.protocol.taskmanager

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.layer.Layer5Codec
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.taskmanager.TaskExecutionTest.UnexpectedExceptionTask.UnexpectedException
import ch.threema.domain.taskmanager.ConnectionStoppedException
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.PassiveTask
import ch.threema.domain.taskmanager.PassiveTaskCodec
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.taskmanager.SingleThreadedTaskManagerDispatcher
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskManagerImpl
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.Timeout

private val logger = getThreemaLogger("TaskExecutionTest")

class TaskExecutionTest {
    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(300)

    /**
     * This is the number of retries the task manager allows per (local) task.
     */
    private val maximumNumberOfAttempts = 5

    /**
     * The task manager that is tested.
     */
    private val taskManager: TaskManagerImpl = TaskManagerImpl(
        { mockk<TaskArchiver>(relaxed = true) },
        mockk<DeviceCookieManager>(),
        TaskManagerImpl.TaskManagerDispatchers(
            SingleThreadedTaskManagerDispatcher(
                true,
                "ExecutorDispatcher",
            ),
            SingleThreadedTaskManagerDispatcher(
                true,
                "ScheduleDispatcher",
            ),
        ),
    )

    /**
     * Provides some (pointless) work to perform.
     */
    private open class TaskWork(protected val number: Int) {
        protected suspend fun doWork() {
            // Just do something to simulate a task that does something
            repeat(number) { i ->
                logger.debug("Logging number $i of $number")
            }
            // Suspend the work a bit, so that the task manager is tempted to run the next task
            // already (which would be faulty!!)
            when (number) {
                // Delay a little longer for task with number 5 (not a specific reason for number 5)
                5 -> delay(1000)
                // Delay a little shorter for other tasks (to reduce total test execution time)
                else -> delay(10)
            }
        }
    }

    /**
     * Executes some work and finally adds its number to the result list and returns the number
     * with the result list.
     */
    private class SuccessfulTask(
        number: Int,
        private val resultList: MutableList<Int>,
    ) : TaskWork(number), PassiveTask<Int> {
        override val type = "SuccessfulTask"
        override suspend fun invoke(handle: PassiveTaskCodec): Int {
            doWork()
            resultList.add(number)
            return number
        }
    }

    /**
     * An exception task that runs [expectedFailedAttempts] times with a certain exception, and then
     * completes successfully returning the number of times it has been tried to be executed.
     */
    private abstract inner class ExceptionTask(private val expectedFailedAttempts: Int) :
        PassiveTask<Int> {
        protected var actualFailedAttempts = 0

        override suspend fun invoke(handle: PassiveTaskCodec): Int {
            val succeed = expectedFailedAttempts <= actualFailedAttempts
            if (succeed) {
                return actualFailedAttempts
            }

            // As we are still running now, this is another failed attempt to run this task
            actualFailedAttempts++

            // Fail
            throwException()
        }

        protected abstract fun throwException(): Nothing
    }

    /**
     * This task throws [expectedFailedAttempts] times an [UnexpectedException].
     */
    private inner class UnexpectedExceptionTask(expectedFailedAttempts: Int) :
        ExceptionTask(expectedFailedAttempts) {
        inner class UnexpectedException(val actualFailedAttempts: Int) : Exception()

        override val type = "UnexpectedExceptionTask"

        override fun throwException() = throw UnexpectedException(actualFailedAttempts)
    }

    /**
     * This task throws [expectedFailedAttempts] times a [ProtocolException].
     */
    private inner class ProtocolExceptionTask : ExceptionTask(1) {
        override val type = "ProtocolExceptionTask"

        override fun throwException() = throw ProtocolException("Test")
    }

    /**
     * This task throws [expectedFailedAttempts] times a [ConnectionStoppedException]. Note that
     * [ConnectionStoppedException]s should only be thrown by the task manager or for testing
     * purposes.
     */
    private inner class ConnectionStoppedExceptionTask : ExceptionTask(1) {
        override val type = "ConnectionStoppedExceptionTask"

        override fun throwException() = throw ConnectionStoppedException()
    }

    @BeforeTest
    fun setup() {
        runBlocking {
            startConnection()
        }
    }

    @Test
    fun testExecutionOfSuccessfulTask(): Unit = runBlocking {
        // Test ascending list
        assertSuccessfulTaskExecution(List(50) { it })

        // Test descending list
        assertSuccessfulTaskExecution(List(50) { 50 - it })

        // Test random numbers
        assertSuccessfulTaskExecution(List(50) { Random.nextInt(1000) })

        // Test all the same
        assertSuccessfulTaskExecution(List(50) { 10 })
    }

    @Test
    fun testExecutionOfFailingTask(): Unit = runBlocking {
        // Test number of failed attempts below the maximum amount of attempts
        repeat(maximumNumberOfAttempts) { assertNumberOfFailedAttempts(it) }
    }

    @Test
    fun testMaxAttempts(): Unit = runBlocking {
        // Test for the exact number of attempts
        assertNumberOfFailedAttempts(maximumNumberOfAttempts)
    }

    @Test
    fun testMoreThanMaxAttempts(): Unit = runBlocking {
        // Test for one more execution attempts
        assertNumberOfFailedAttempts(maximumNumberOfAttempts + 1)
        // Test for many more execution attempts
        assertNumberOfFailedAttempts(Int.MAX_VALUE)
    }

    @Test
    fun testProtocolException() {
        val done = taskManager.schedule(ProtocolExceptionTask())
        // Assert that the deferred won't be completed immediately, as there is a minimum delay to
        // wait until the connection will be restarted.
        assertFailsWith<TimeoutCancellationException> {
            runBlocking {
                withTimeout(1000) {
                    done.await()
                }
            }
        }

        // The server connection should be restarted soon, and the current task should be executed
        // again. It will complete successfully in its second execution and therefore the deferred
        // will complete as soon as the new connection could be established.
        runBlocking {
            assertEquals(1, done.await())
        }
    }

    @Test
    fun testConnectionStoppedException() {
        val done = taskManager.schedule(ConnectionStoppedExceptionTask())
        // Assert that awaiting the deferred is pointless, as the task will be retried after the
        // connection has been started again. Note that we wait for 5 seconds, which is longer than
        // the minimum delay in the task runner. Therefore, this additionally tests that the task
        // manager does not restart the connection when a ConnectionStoppedException has been
        // thrown.
        assertFailsWith<TimeoutCancellationException> {
            runBlocking {
                withTimeout(5000) {
                    done.await()
                }
            }
        }

        restartConnection()

        // Now, as the server connection has been restarted, the current task should be executed
        // again and as a cancellation exception task completes successfully in its second
        // execution, the deferred should complete.
        runBlocking {
            assertEquals(1, done.await())
        }
    }

    private suspend fun assertSuccessfulTaskExecution(expectedResults: List<Int>) {
        // The result list will contain the task numbers in the actually executed order
        val resultList = mutableListOf<Int>()
        // The return list contains the numbers in the order the task have been scheduled
        val returnList = expectedResults.map { SuccessfulTask(it, resultList) }
            .map { taskManager.schedule(it) }
            .awaitAll()

        // Assert that the actual execution order matches the expected order
        assertEquals(expectedResults, resultList)
        // Assert that the scheduling order matches the expected order
        assertEquals(expectedResults, returnList)
    }

    private suspend fun assertNumberOfFailedAttempts(numFailedAttempts: Int) {
        if (numFailedAttempts < maximumNumberOfAttempts) {
            // Expect the exact number of failed attempts as the number of failed attempts is lower
            // than the maximum number of attempts which allows the task to complete in the last
            // attempt.
            val failedAttempts =
                taskManager.schedule(UnexpectedExceptionTask(numFailedAttempts)).await()
            assertEquals(numFailedAttempts, failedAttempts)
        } else {
            // If the number of failed attempts is the maximum number of attempts or higher, the
            // task will not complete successfully and therefore the task will complete
            // exceptionally.
            // Note that scheduling the task must not trigger any exceptions!
            val done = taskManager.schedule(UnexpectedExceptionTask(numFailedAttempts))
            // Only when awaiting the completable deferred results in an exception
            val exception = assertFailsWith<UnexpectedException> {
                runBlocking {
                    done.await()
                }
            }
            // Expect that the task has been executed as often as possible
            assertEquals(maximumNumberOfAttempts, exception.actualFailedAttempts)
        }
    }

    private fun restartConnection() {
        runBlocking {
            stopConnection()
            startConnection()
        }
    }

    private suspend fun startConnection() {
        val layer5Codec = mockk<Layer5Codec>()
        every { layer5Codec.restartConnection(any()) } answers {
            // Wait until reconnection (connection is mocked)
            val delay = firstArg<Long>()
            Thread.sleep(delay)
            // Launch new coroutine to restart the task manager
            CoroutineScope(Dispatchers.Default).launch {
                taskManager.pauseRunningTasks(ServerSocketCloseReason("Test"))
                taskManager.startRunningTasks(
                    layer5Codec,
                    mockk<IncomingMessageProcessor>(),
                )
            }
        }
        taskManager.startRunningTasks(layer5Codec, mockk<IncomingMessageProcessor>())
    }

    private suspend fun stopConnection() {
        taskManager.pauseRunningTasks(ServerSocketCloseReason("Test"))
    }
}
