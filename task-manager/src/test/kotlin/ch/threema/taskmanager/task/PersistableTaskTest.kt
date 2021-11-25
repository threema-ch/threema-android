package ch.threema.taskmanager.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

internal class InMemoryTaskPersistenceHandler<
    T : PersistableTaskState,
    S : TaskLifecycle<S>
>(private var taskState: T, private var lifecycleState: S?) : TaskPersistenceHandler<T, S> {
    override suspend fun persistTaskState(lifecycleState: S, taskState: T) {
        this.lifecycleState = lifecycleState
        this.taskState = taskState
    }

    override suspend fun getPersistedTaskState(): T = taskState
    override suspend fun getPersistedLifecycleState(): S? = lifecycleState
}

internal data class SampleTaskState(
    val incrementBy: Int = 1,
    val decrementBy: Int = 1,
    var currentValue: Int = 0,
) : PersistableTaskState {
    fun increment() {
        currentValue += incrementBy
    }

    fun decrement() {
        currentValue -= decrementBy
    }
}

internal enum class SampleTaskLifecycle : TaskLifecycle<SampleTaskLifecycle> {
    INCREMENTATION_STEP,
    DECREMENTATION_STEP
}
internal abstract class SampleTask(
    var taskState: SampleTaskState,
    initialState: SampleTaskLifecycle? = null
) : Task<Unit>,
    PersistableTask<Unit, SampleTaskState, SampleTaskLifecycle>,
    TaskPersistenceHandler<SampleTaskState, SampleTaskLifecycle> by
      InMemoryTaskPersistenceHandler(taskState, initialState) {
    override suspend fun getCurrentTaskState(): SampleTaskState = taskState
}

@ExperimentalCoroutinesApi
internal class PersistableTaskTest {

    @Test
    fun `section lambdas should be executed`() = runBlockingTest {
        val executions = mutableListOf<Int>()

        val task = object : SampleTask(SampleTaskState()) {
            override suspend fun invoke(scope: CoroutineScope) {
                persistableSection(SampleTaskLifecycle.INCREMENTATION_STEP) {
                    executions.add(1)
                }
                persistableSection(SampleTaskLifecycle.DECREMENTATION_STEP) {
                    executions.add(2)
                }
            }
        }
        task.invoke(this)

        assertContentEquals(listOf(1, 2), executions)
    }

    @Test
    fun `the current state and parameters should be persisted after entering a new section`() = runBlockingTest {
        val testTaskState = SampleTaskState(2, 1, 42)
        val task = object : SampleTask(testTaskState.copy()) {
            override suspend fun invoke(scope: CoroutineScope) {
                assertNull(getPersistedLifecycleState())
                assertEquals(testTaskState, getPersistedTaskState())
                persistableSection(SampleTaskLifecycle.INCREMENTATION_STEP) {
                    assertEquals(SampleTaskLifecycle.INCREMENTATION_STEP, getPersistedLifecycleState())
                    taskState.increment()
                }

                assertEquals(SampleTaskLifecycle.INCREMENTATION_STEP, getPersistedLifecycleState())
                assertEquals(testTaskState.copy(currentValue = 44), getPersistedTaskState())

                persistableSection(SampleTaskLifecycle.DECREMENTATION_STEP) {
                    assertEquals(SampleTaskLifecycle.DECREMENTATION_STEP, getPersistedLifecycleState())
                    taskState.decrement()
                }
                assertEquals(SampleTaskLifecycle.DECREMENTATION_STEP, getPersistedLifecycleState())
                assertEquals(testTaskState.copy(currentValue = 43), getPersistedTaskState())
            }
        }
        task.invoke(this)
    }

    @Test
    fun `already executed states should be skipped`() = runBlockingTest {
        val testTaskState = SampleTaskState(2, 1, 44)
        val lifecycleState = SampleTaskLifecycle.DECREMENTATION_STEP
        val task = object : SampleTask(testTaskState.copy(), lifecycleState) {
            override suspend fun invoke(scope: CoroutineScope) {
                assertEquals(SampleTaskLifecycle.DECREMENTATION_STEP, getPersistedLifecycleState())
                assertEquals(testTaskState.copy(currentValue = 44), getPersistedTaskState())
                persistableSection(SampleTaskLifecycle.INCREMENTATION_STEP) {
                    fail("Incrementation step should be skipped")
                }

                assertEquals(SampleTaskLifecycle.DECREMENTATION_STEP, getPersistedLifecycleState())
                assertEquals(testTaskState.copy(currentValue = 44), getPersistedTaskState())

                persistableSection(SampleTaskLifecycle.DECREMENTATION_STEP) {
                    assertEquals(SampleTaskLifecycle.DECREMENTATION_STEP, getPersistedLifecycleState())
                    taskState.decrement()
                }
                assertEquals(SampleTaskLifecycle.DECREMENTATION_STEP, getPersistedLifecycleState())
                assertEquals(testTaskState.copy(currentValue = 43), getPersistedTaskState())
            }
        }
        task.invoke(this)
    }
}
