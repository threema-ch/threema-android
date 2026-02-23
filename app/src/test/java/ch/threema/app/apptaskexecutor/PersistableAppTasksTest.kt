package ch.threema.app.apptaskexecutor

import ch.threema.app.apptaskexecutor.tasks.AppTaskData
import ch.threema.app.apptaskexecutor.tasks.RemoteSecretDeleteStepsTask
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlinx.serialization.json.Json
import org.koin.test.KoinTest

class PersistableAppTasksTest : KoinTest {

    @Test
    fun testRemoteSecretDeleteStepsTask() {
        assertValidEncoding(
            expectedTaskClass = RemoteSecretDeleteStepsTask::class,
            encodedTask = "{\"type\":\"ch.threema.app.apptaskexecutor.tasks.RemoteSecretDeleteStepsTask.RemoteSecretDeleteStepsTaskData\"," +
                "\"authenticationToken\":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31]}",
        )
    }

    private fun assertValidEncoding(expectedTaskClass: KClass<*>, encodedTask: String) {
        val decodedTask = Json.decodeFromString<AppTaskData>(encodedTask).createTask()
        assertNotNull(decodedTask)
        assertEquals(expectedTaskClass, decodedTask::class)
    }
}
