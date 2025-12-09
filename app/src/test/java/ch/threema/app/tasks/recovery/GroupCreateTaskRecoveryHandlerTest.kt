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

package ch.threema.app.tasks.recovery

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.GroupCreateTask
import ch.threema.app.tasks.archive.recovery.handlers.GroupCreateTaskRecoveryHandler
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GroupCreateTaskRecoveryHandlerTest {

    // Note that all whitespace characters are removed
    private val oldEncodedTask = """
        {
            "type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData",
            "name":"GroupName",
            "profilePictureChange":{"type":"ch.threema.app.protocol.RemoveProfilePicture"},
            "members":["01234567","TESTTEST"],
            "groupIdentity":{"creatorIdentity":"0CREATOR","groupId":42},
            "predefinedMessageIds":{
                "messageIdBytes1":[118,-115,91,73,-98,58,-61,-31],
                "messageIdBytes2":[4,29,99,50,82,22,93,-90],
                "messageIdBytes3":[46,49,108,-123,84,34,36,-87],
                "messageIdBytes4":[-111,107,11,-35,-125,87,-83,-72]
            }
        }
    """.replace("\\s+".toRegex(), "")

    // Note that all whitespace characters are removed
    private val newEncodedTask = """
        {
            "type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData",
            "name":"GroupName",
            "serializableExpectedProfilePictureChange":{
                "type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData.Companion.SerializableExpectedProfilePictureChange.Remove"
            },
            "members":["01234567","TESTTEST"],
            "groupIdentity":{"creatorIdentity":"0CREATOR","groupId":42},
            "serializablePredefinedMessageIds":{
                "messageId1":-2178833343207207562,
                "messageId2":-6458981748290937596,
                "messageId3":-6258839835727089362,
                "messageId4":-5139355375899022447
            }
        }
    """.replace("\\s+".toRegex(), "")

    @Test
    fun `old task representation can be recovered`() {
        val serviceManagerMock = mockk<ServiceManager>(relaxed = true)

        val task = GroupCreateTaskRecoveryHandler.tryRecovery(
            encodedTask = oldEncodedTask,
            serviceManager = serviceManagerMock,
        )

        assertTrue(task is GroupCreateTask)
        val reEncodedTask = Json.encodeToString(task.serialize())
        assertEquals(newEncodedTask, reEncodedTask)
    }
}
