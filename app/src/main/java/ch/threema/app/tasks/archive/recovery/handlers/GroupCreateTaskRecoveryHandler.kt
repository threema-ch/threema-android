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

package ch.threema.app.tasks.archive.recovery.handlers

import ch.threema.app.managers.ServiceManager
import ch.threema.app.protocol.ExpectedProfilePictureChange
import ch.threema.app.protocol.PredefinedMessageIds
import ch.threema.app.tasks.GroupCreateTask
import ch.threema.app.tasks.archive.recovery.TaskRecoveryHandler
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.common.decodeArray
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import org.json.JSONException
import org.json.JSONObject

object GroupCreateTaskRecoveryHandler : TaskRecoveryHandler {
    override fun tryRecovery(encodedTask: String, serviceManager: ServiceManager): Task<*, TaskCodec>? {
        try {
            val jsonObject = JSONObject(encodedTask)
            val type = jsonObject.getString("type")
            if (type != "ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData") {
                return null
            }

            val name = jsonObject.getString("name")
            val members = jsonObject.decodeArray<Set<String>>("members")
            val groupIdentityJson = jsonObject.getJSONObject("groupIdentity")
            val creatorIdentity = groupIdentityJson.getString("creatorIdentity")
            val groupId = groupIdentityJson.getLong("groupId")
            val predefinedMessageIdsJson = jsonObject.getJSONObject("predefinedMessageIds")
            val predefinedMessageIds = PredefinedMessageIds(
                messageId1 = MessageId(predefinedMessageIdsJson.decodeArray<ByteArray>("messageIdBytes1")),
                messageId2 = MessageId(predefinedMessageIdsJson.decodeArray<ByteArray>("messageIdBytes2")),
                messageId3 = MessageId(predefinedMessageIdsJson.decodeArray<ByteArray>("messageIdBytes3")),
                messageId4 = MessageId(predefinedMessageIdsJson.decodeArray<ByteArray>("messageIdBytes4")),
            )

            return GroupCreateTask(
                name = name,
                // Note that we assume that the profile picture isn't expected. This just leads to a potentially wrong logged warning.
                expectedProfilePictureChange = ExpectedProfilePictureChange.Remove,
                members = members,
                groupIdentity = GroupIdentity(
                    creatorIdentity = creatorIdentity,
                    groupId = groupId,
                ),
                predefinedMessageIds = predefinedMessageIds,
                outgoingCspMessageServices = serviceManager.getOutgoingCspMessageServices(),
                groupCallManager = serviceManager.groupCallManager,
                fileService = serviceManager.fileService,
                groupProfilePictureUploader = serviceManager.groupProfilePictureUploader,
                groupModelRepository = serviceManager.modelRepositories.groups,
            )
        } catch (_: JSONException) {
            return null
        }
    }
}
