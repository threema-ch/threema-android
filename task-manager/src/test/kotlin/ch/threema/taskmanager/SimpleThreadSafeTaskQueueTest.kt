package ch.threema.taskmanager

import ch.threema.taskmanager.task.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
internal class SimpleThreadSafeTaskQueueTest {

    private fun getNoopTask() = object : Task<Unit> {
        override suspend fun invoke(scope: CoroutineScope) { /* no-op */ }
    }

    @Test
    fun newTaskObservers() = runBlockingTest {
        val queue = SimpleThreadSafeTaskQueue()

        var executed = false

        queue.newTaskObservers.add { executed = true }
        queue.add(getNoopTask())

        assertTrue(executed)
    }

    @Test
    fun add() = runBlockingTest {
        val queue = SimpleThreadSafeTaskQueue()
        val task = getNoopTask()
        queue.add(task)

        assertEquals(task, queue.removeFirstOrNull())
    }

    @Test
    fun removeFirstOrNullEmpty() = runBlockingTest {
        val queue = SimpleThreadSafeTaskQueue()
        assertNull(queue.removeFirstOrNull())
    }

    @Test
    fun removeFirstOrNullSingle() = runBlockingTest {
        val queue = SimpleThreadSafeTaskQueue()
        val task = getNoopTask()
        queue.add(task)

        assertEquals(task, queue.removeFirstOrNull())
        assertNull(queue.removeFirstOrNull())
    }
}
