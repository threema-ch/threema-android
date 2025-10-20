/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.apptaskexecutor

import ch.threema.app.apptaskexecutor.tasks.AppTaskData
import ch.threema.app.apptaskexecutor.tasks.RemoteSecretDeleteStepsTask
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.test.koinTestModuleRule
import ch.threema.localcrypto.MasterKeyManager
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.koin.test.KoinTest

class PersistableAppTasksTest : KoinTest {

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory { mockk<ServiceManagerProvider>() }
        factory { mockk<AppStartupMonitor>() }
        factory { mockk<MasterKeyManager>() }
    }

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
