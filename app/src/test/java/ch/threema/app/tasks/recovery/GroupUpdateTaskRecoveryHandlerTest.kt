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
import ch.threema.app.tasks.GroupUpdateTask
import ch.threema.app.tasks.archive.recovery.handlers.GroupUpdateTaskRecoveryHandler
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GroupUpdateTaskRecoveryHandlerTest {

    // Note that all whitespace characters are removed
    private val oldEncodedTask = """
         {
             "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData",
             "name":"GroupName",
             "profilePictureChange":{"type":"ch.threema.app.protocol.RemoveProfilePicture"},
             "updatedMembers":["01234567","TESTTEST"],
             "addedMembers":["01234567"],
             "removedMembers":["07654321"],
             "groupIdentity":{"creatorIdentity":"0CREATOR","groupId":42},
             "predefinedMessageIds":{
                "messageIdBytes1":[14,-37,81,71,-85,113,-127,96],
                "messageIdBytes2":[81,-97,-79,81,-122,101,-3,-40],
                "messageIdBytes3":[7,-35,-58,-57,-73,14,-55,-63],
                "messageIdBytes4":[127,41,3,-83,107,-95,34,49]
             }
         }
    """.replace("\\s+".toRegex(), "")

    private val newEncodedTask = """
        {
            "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData",
            "name":"GroupName",
            "serializableExpectedProfilePictureChange":{
                "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData.Companion.SerializableExpectedProfilePictureChange.Remove"
            },
            "updatedMembers":["01234567","TESTTEST"],
            "addedMembers":["01234567"],
            "removedMembers":["07654321"],
            "groupIdentity":{"creatorIdentity":"0CREATOR","groupId":42},
            "serializablePredefinedMessageIds":{
                "messageId1":6953964280086649614,
                "messageId2":-2810978964838703279,
                "messageId3":-4483035771577115385,
                "messageId4":3540569740902869375
            }
        }
    """.replace("\\s+".toRegex(), "")

    @Test
    fun `old task representation can be recovered`() {
        val serviceManagerMock = mockk<ServiceManager>(relaxed = true)

        val task = GroupUpdateTaskRecoveryHandler.tryRecovery(
            encodedTask = oldEncodedTask,
            serviceManager = serviceManagerMock,
        )

        assertTrue(task is GroupUpdateTask)
        val reEncodedTask = Json.encodeToString(task.serialize())
        println(reEncodedTask)
        assertEquals(newEncodedTask, reEncodedTask)
    }
}
