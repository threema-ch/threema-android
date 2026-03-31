package ch.threema.app.tasks.archive.recovery.handlers

import ch.threema.app.protocolsteps.ExpectedProfilePictureChange
import ch.threema.app.protocolsteps.PredefinedMessageIds
import ch.threema.app.tasks.GroupCreateTask
import ch.threema.app.tasks.archive.recovery.TaskRecoveryHandler
import ch.threema.common.decodeArray
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import org.json.JSONException
import org.json.JSONObject

object GroupCreateTaskRecoveryHandler : TaskRecoveryHandler {
    override fun tryRecovery(encodedTask: String): Task<*, TaskCodec>? {
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
            )
        } catch (_: JSONException) {
            return null
        }
    }
}
