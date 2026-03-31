package ch.threema.storage

import ch.threema.storage.factories.TaskArchiveFactory
import junit.framework.TestCase.assertEquals
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class TaskArchiveFactoryTest : KoinComponent {
    private lateinit var taskArchiveFactory: TaskArchiveFactory

    @BeforeTest
    fun setup() {
        taskArchiveFactory = TaskArchiveFactory(get())
        taskArchiveFactory.deleteAll()
    }

    @AfterTest
    fun tearDown() {
        taskArchiveFactory.deleteAll()
    }

    @Test
    fun testAdd() {
        val encodedTasks = listOf(
            "encoded task 1",
            "encoded task 2",
            "encoded task 3",
        )
        encodedTasks.forEach { taskArchiveFactory.insert(it) }
        assertEquals(encodedTasks, taskArchiveFactory.getAll())
    }

    @Test
    fun testRemove() {
        val encodedTasks = listOf(
            "oldestTask",
            "firstRemoved",
            "task1",
            "task",
            "task2",
            "task",
        )
        encodedTasks.forEach { taskArchiveFactory.insert(it) }
        assertEquals(encodedTasks, taskArchiveFactory.getAll())

        taskArchiveFactory.remove("does not exist, so it should not have an effect")
        assertEquals(encodedTasks, taskArchiveFactory.getAll())

        taskArchiveFactory.remove("firstRemoved")
        assertEquals(listOf("task1", "task", "task2", "task"), taskArchiveFactory.getAll())

        taskArchiveFactory.removeOne("task")
        assertEquals(listOf("task1", "task2", "task"), taskArchiveFactory.getAll())

        taskArchiveFactory.removeOne("task2")
        assertEquals(listOf("task1", "task"), taskArchiveFactory.getAll())

        taskArchiveFactory.remove("task1")
        assertEquals(listOf("task"), taskArchiveFactory.getAll())

        taskArchiveFactory.remove("task")
        assertEquals(emptyList<String>(), taskArchiveFactory.getAll())

        taskArchiveFactory.remove("does not exist anymore, so it should not have an effect")
        assertEquals(emptyList<String>(), taskArchiveFactory.getAll())
    }

    @Test
    fun testTrim() {
        val encodedTasks = listOf(
            "encoded task 1",
            "encoded task 2",
            "encoded task 3",
        )
        encodedTasks.forEach { taskArchiveFactory.insert(" $it\n\n") }
        assertEquals(encodedTasks, taskArchiveFactory.getAll())
    }
}
