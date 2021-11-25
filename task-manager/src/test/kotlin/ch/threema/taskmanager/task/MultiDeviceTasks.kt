package ch.threema.taskmanager.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
internal class MultiDeviceTasks {

    @Test
    fun executionMultiDeviceOnly() = runBlockingTest {
        for (isMultiDeviceEnabled in listOf(true, false)) {
            var taskExecuted = false
            val task = object : MultiDeviceOnlyTask<Unit> {
                override suspend fun executionMultiDeviceOnly(scope: CoroutineScope) {
                    taskExecuted = true
                }

                override val isMultiDeviceEnabled = isMultiDeviceEnabled
            }

            task.invoke(this)

            assertEquals(isMultiDeviceEnabled, taskExecuted)
        }
    }

    @Test
    fun executionMultiDeviceExcepted() = runBlockingTest {
        for (isMultiDeviceDisabled in listOf(true, false)) {
            var taskExecuted = false
            val task = object : MultiDeviceExcludedTask<Unit> {
                override suspend fun executionMultiDeviceExcluded(scope: CoroutineScope) {
                    taskExecuted = true
                }
                override val isMultiDeviceEnabled = !isMultiDeviceDisabled
            }

            task.invoke(this)

            assertEquals(isMultiDeviceDisabled, taskExecuted)
        }
    }
}
