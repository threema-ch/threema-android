package ch.threema.taskmanager

import ch.threema.taskmanager.task.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

internal class StupidTaskQueue(
    private val queue: ArrayDeque<Task<out Any?>> = ArrayDeque()
) : TaskQueue {
    override val newTaskObservers = mutableListOf<suspend (Task<out Any?>) -> Unit>()

    override suspend fun removeFirstOrNull(): Task<out Any?>? = queue.removeFirstOrNull()
    override suspend fun add(element: Task<out Any?>) {
        queue.addLast(element)
        newTaskObservers.forEach { it(element) }
    }
}

internal open class FakeTask(
    val executedTasks: MutableList<FakeTask> = mutableListOf(),
) : Task<Unit> {
    override suspend fun invoke(scope: CoroutineScope) {
        executedTasks.add(this)
    }
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal class TaskRunnerTest {

    @Test
    fun `dequeue and run existing tasks after initialization`() = runBlockingTest {
        val taskExecution = mutableListOf<FakeTask>()

        val task1 = FakeTask(taskExecution)
        val task2 = FakeTask(taskExecution)
        val queue = StupidTaskQueue(ArrayDeque(listOf(task1, task2)))

        val runner = TaskRunner(queue, this)
        runner.close()

        assertContentEquals(mutableListOf(task1, task2), taskExecution)
    }

    @Test
    fun `run single task after queue observer notification`() = runBlockingTest {
        val task1 = FakeTask()
        val queue = StupidTaskQueue()

        val runner = TaskRunner(queue, this)
        queue.add(task1)
        runner.close()

        assertContentEquals(task1.executedTasks, mutableListOf(task1))
    }

    @Test
    fun `run multiple tasks in order after multiple queue observer notifications`() = runBlockingTest {
        val taskExecution = mutableListOf<FakeTask>()

        val task1 = FakeTask(taskExecution)
        val task2 = FakeTask(taskExecution)
        val queue = StupidTaskQueue()

        val runner = TaskRunner(queue, this)
        queue.add(task1)
        queue.add(task2)
        runner.close()

        assertContentEquals(listOf(task1, task2), taskExecution)
    }

    @Test
    fun `run existing tasks after observer notification`() = runBlockingTest {
        val taskExecution = mutableListOf<FakeTask>()

        val task1 = FakeTask(taskExecution)
        val task2 = FakeTask(taskExecution)
        val queue = StupidTaskQueue(ArrayDeque(listOf(task1)))

        val runner = TaskRunner(queue, this)
        queue.add(task2)
        runner.close()

        assertContentEquals(listOf(task1, task2), taskExecution)
    }

    @Test
    fun `cancelling a task should not cancel the task runner`() = runBlockingTest {
        val taskExecution = mutableListOf<FakeTask>()

        val task1 = object : FakeTask(taskExecution) {

            override suspend fun invoke(scope: CoroutineScope) {
                super.invoke(scope)
                scope.cancel("Randomly cancelled task 1")
            }
        }
        val task2 = FakeTask(taskExecution)
        val queue = StupidTaskQueue(ArrayDeque(listOf(task1, task2)))

        val runner = TaskRunner(queue, this)
        runner.close()

        assertContentEquals(listOf(task1, task2), taskExecution)
    }
}
