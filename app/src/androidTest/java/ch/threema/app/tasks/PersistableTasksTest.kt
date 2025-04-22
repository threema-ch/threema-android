/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.ThreemaApplication
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModelData
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.models.ContactModel
import com.neilalexander.jnacl.NaCl
import java.util.Date
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.fail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * These tests are useful to detect when a task cannot be created out of a persisted representation
 * of the task. If any of these tests fails, then it is probably because there were some changes in
 * the serialized task data. Tasks that cannot be created due to the changed serialized
 * representation will be dropped.
 */
class PersistableTasksTest {
    private val serviceManager = ThreemaApplication.requireServiceManager()

    @Test
    fun testContactDeliveryReceiptMessageTask() {
        assertValidEncoding(
            OutgoingContactDeliveryReceiptMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingContactDeliveryReceiptMessageTask.OutgoingDeliveryReceiptMessageData",""" +
                """"receiptType":1,"messageIds":["0000000000000000"],"date":"1234567890","toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testFileMessageTask() {
        assertValidEncoding(
            OutgoingFileMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingFileMessageTask.OutgoingFileMessageData","messageModelId":1,"receiverType":0,""" +
                """"recipientIdentities":["01234567"],"thumbnailBlobId":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}""",
        )
    }

    @Test
    fun testGroupDeleteProfilePictureTask() {
        assertValidEncoding(
            OutgoingGroupDeleteProfilePictureTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDeleteProfilePictureTask.OutgoingGroupDeleteProfilePictureData",""" +
                """"groupId":[0,0,0,0,0,0,0,0],"creatorIdentity":"01234567","receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupDeliveryReceiptMessageTask() {
        assertValidEncoding(
            OutgoingGroupDeliveryReceiptMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDeliveryReceiptMessageTask.OutgoingGroupDeliveryReceiptMessageData",""" +
                """"messageModelId":0,"recipientIdentities":["01234567","01234567"],"receiptType":0}""",
        )
    }

    @Test
    fun testGroupLeaveTask() {
        assertValidEncoding(
            OutgoingGroupLeaveTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupLeaveTask.OutgoingGroupLeaveTaskData",""" +
                """"groupIdentity":{"creatorIdentity":"01234567","groupId":42},"memberIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupNameTask() {
        assertValidEncoding(
            OutgoingGroupNameTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupNameTask.OutgoingGroupNameData","groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567","groupName":"groupName","receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupProfilePictureTask() {
        assertValidEncoding(
            OutgoingGroupProfilePictureTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupProfilePictureTask.OutgoingGroupProfilePictureData",""" +
                """"groupId":[0,0,0,0,0,0,0,0],"creatorIdentity":"01234567","receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupSetupTask() {
        assertValidEncoding(
            OutgoingGroupSetupTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupSetupTask.OutgoingGroupSetupData","groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567","memberIdentities":["01234567"],"receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupSyncRequestTask() {
        assertValidEncoding(
            OutgoingGroupSyncRequestTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupSyncRequestTask.OutgoingGroupSyncRequestData",""" +
                """"groupId":[0,0,0,0,0,0,0,0],"creatorIdentity":"01234567","messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupSyncTask() {
        assertValidEncoding(
            OutgoingGroupSyncTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupSyncTask.OutgoingGroupSyncData","groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567","receiverIdentities":["01234567"]}""",
        )
    }

    @Test
    fun testLocationMessageTask() {
        assertValidEncoding(
            OutgoingLocationMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingLocationMessageTask.OutgoingLocationMessageTaskData","messageModelId":0,""" +
                """"recipientIdentities":["01234567","01234567"],"receiverType":0}""",
        )
    }

    @Test
    fun testPollSetupMessageTask() {
        assertValidEncoding(
            OutgoingPollSetupMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingPollSetupMessageTask.OutgoingPollSetupMessageData","messageModelId":0,""" +
                """"recipientIdentities":["01234567","01234567"],"receiverType":0,"ballotId":[-58,11,102,-122,-119,-102,19,-10],""" +
                """"ballotData":"{\"d\":\"description\",\"s\":0,\"a\":0,\"t\":1,\"o\":0,\"u\":0,""" +
                """\"c\":[{\"i\":0,\"n\":\"desc\",\"o\":0,\"r\":[0],\"t\":0}],\"p\":[\"01234567\"]}"}""",
        )
    }

    @Test
    fun testPollVoteContactMessageTask() {
        assertValidEncoding(
            OutgoingPollVoteContactMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingPollVoteContactMessageTask.OutgoingPollVoteContactMessageData",""" +
                """"messageId":"0000000000000000","ballotId":[-127,-79,80,-109,-98,62,-3,81],"ballotCreator":"01234567",""" +
                """"ballotVotes":[{"first":0,"second":0}],"toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testPollVoteGroupMessageTask() {
        assertValidEncoding(
            OutgoingPollVoteGroupMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingPollVoteGroupMessageTask.OutgoingPollVoteGroupMessageData",""" +
                """"messageId":"0000000000000000","recipientIdentities":["01234567","01234567"],"ballotId":[52,64,-6,18,2,-71,124,-19],""" +
                """"ballotCreator":"01234567","ballotVotes":[{"first":0,"second":0}],"ballotType":"INTERMEDIATE",""" +
                """"apiGroupId":"0000000000000000","groupCreator":"01234567"}""",
        )
    }

    @Test
    fun testTextMessageTask() {
        assertValidEncoding(
            OutgoingTextMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingTextMessageTask.OutgoingTextMessageData","messageModelId":0,""" +
                """"recipientIdentities":["01234567","01234567"],"receiverType":0}""",
        )
    }

    @Test
    fun testSendProfilePictureTask() {
        assertValidEncoding(
            SendProfilePictureTask::class.java,
            """{"type":"ch.threema.app.tasks.SendProfilePictureTask.SendProfilePictureData","toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testSendPushTokenTask() {
        assertValidEncoding(
            SendPushTokenTask::class.java,
            """{"type":"ch.threema.app.tasks.SendPushTokenTask.SendPushTokenData","token":"token","tokenType":0}""",
        )
    }

    @Test
    fun testOutgoingContactRequestProfilePictureTask() {
        assertValidEncoding(
            OutgoingContactRequestProfilePictureTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingContactRequestProfilePictureTask.OutgoingContactRequestProfilePictureData",""" +
                """"toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testDeleteAndTerminateFSSessionsTask() {
        // Add the contact '01234567' so that creating the tasks works
        addTestData()

        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class.java,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"RESET"}""",
        )
        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class.java,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"UNKNOWN_SESSION"}""",
        )
        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class.java,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"DISABLED_BY_LOCAL"}""",
        )
        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class.java,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"DISABLED_BY_REMOTE"}""",
        )
    }

    @Test
    fun testApplicationUpdateStepsTask() {
        assertValidEncoding(
            ApplicationUpdateStepsTask::class.java,
            """{"type":"ch.threema.app.tasks.ApplicationUpdateStepsTask.ApplicationUpdateStepsData"}""",
        )
    }

    @Test
    fun testFSRefreshStepsTask() {
        assertValidEncoding(
            FSRefreshStepsTask::class.java,
            """{"type":"ch.threema.app.tasks.FSRefreshStepsTask.FSRefreshStepsTaskData","contactIdentities":["01234567"]}""",
        )
    }

    @Test
    fun testOutboundIncomingContactMessageUpdateReadTask() {
        assertValidEncoding(
            OutboundIncomingContactMessageUpdateReadTask::class.java,
            """{"type":"ch.threema.app.tasks.OutboundIncomingContactMessageUpdateReadTask.OutboundIncomingContactMessageUpdateReadData",""" +
                """"messageIds":[[0,-1,2,3,4,5,6,7]],"timestamp":1704067200000,"recipientIdentity":"01234567"}""",
        )
    }

    @Test
    fun testOutboundIncomingGroupMessageUpdateReadTask() {
        assertValidEncoding(
            OutboundIncomingGroupMessageUpdateReadTask::class.java,
            """{"type":"ch.threema.app.tasks.OutboundIncomingGroupMessageUpdateReadTask.OutboundIncomingGroupMessageUpdateReadData",""" +
                """"messageIds":[[0,-1,2,3,4,5,6,7]],"timestamp":1704067200000,"groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567"}""",
        )
    }

    @Test
    fun testOutgoingContactEditMessageTask() {
        assertValidEncoding(
            OutgoingContactEditMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingContactEditMessageTask.OutgoingContactEditMessageData",""" +
                """"toIdentity":"01234567","messageModelId":0, "messageId":[0,0,0,0,0,0,0,0], "editedText":"test", "editedAt":0}""",
        )
    }

    @Test
    fun testOutgoingGroupEditMessageTask() {
        assertValidEncoding(
            OutgoingGroupEditMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupEditMessageTask.OutgoingGroupEditMessageData","messageModelId":0, """ +
                """"messageId":[0,0,0,0,0,0,0,0], "editedText":"test", "editedAt":0,"recipientIdentities":["01234567","01234567"]}""",
        )
    }

    @Test
    fun testOutgoingContactDeleteMessageTask() {
        assertValidEncoding(
            OutgoingContactDeleteMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingContactDeleteMessageTask.OutgoingContactDeleteMessageData",""" +
                """"toIdentity":"01234567","messageModelId":0, "messageId":[0,0,0,0,0,0,0,0], "deletedAt":0}""",
        )
    }

    @Test
    fun testOutgoingGroupDeleteMessageTask() {
        assertValidEncoding(
            OutgoingGroupDeleteMessageTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDeleteMessageTask.OutgoingGroupDeleteMessageData",""" +
                """"messageModelId":0,"messageId":[0,0,0,0,0,0,0,0],"deletedAt":0,"recipientIdentities":["01234567","01234567"]}""",
        )
    }

    @Test
    fun testReflectUserProfileNicknameSyncTask() {
        assertValidEncoding(
            ReflectUserProfileNicknameSyncTask::class.java,
            """{"type":"ch.threema.app.tasks.ReflectUserProfileNicknameSyncTask.ReflectUserProfileNicknameSyncTaskData",""" +
                """"newNickname":"nick"}""",
        )
    }

    @Test
    fun testReflectUserProfilePictureSyncTask() {
        assertValidEncoding(
            ReflectUserProfilePictureSyncTask::class.java,
            """{"type":"ch.threema.app.tasks.ReflectUserProfilePictureSyncTask.ReflectUserProfilePictureSyncTaskData"}""",
        )
    }

    @Test
    fun testReflectUserProfileShareWithPolicySyncTask() {
        assertValidEncoding(
            ReflectUserProfileShareWithPolicySyncTask::class.java,
            """{"type":"ch.threema.app.tasks.ReflectUserProfileShareWithPolicySyncTask.ReflectUserProfileShareWithPolicySyncTaskData",""" +
                """"newPolicy":"NOBODY"}""",
        )
    }

    @Test
    fun testReflectUserProfileShareWithAllowListSyncTask() {
        assertValidEncoding(
            ReflectUserProfileShareWithAllowListSyncTask::class.java,
            """{"type":""" +
                """"ch.threema.app.tasks.ReflectUserProfileShareWithAllowListSyncTask.ReflectUserProfileShareWithAllowListSyncTaskData",""" +
                """"allowedIdentities":["01234567", "01234568"]}""",
        )
    }

    @Test
    fun testReflectUserProfileIdentityLinksTask() {
        assertValidEncoding(
            expectedTaskClass = ReflectUserProfileIdentityLinksTask::class.java,
            encodedTask = """{"type":"ch.threema.app.tasks.ReflectUserProfileIdentityLinksTask.ReflectUserProfileIdentityLinksTaskData"}""",
        )
    }

    @Test
    fun testReflectNameUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNameUpdate.ReflectNameUpdateData",""" +
                """"firstName":"A","lastName":"B","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNameUpdate.ReflectNameUpdateData",""" +
                """"firstName":"A","lastName":"","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNameUpdate.ReflectNameUpdateData",""" +
                """"firstName":"","lastName":"B","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectReadReceiptPolicyUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate.ReflectReadReceiptPolicyUpdateData",""" +
                """"readReceiptPolicy":"DEFAULT","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate.ReflectReadReceiptPolicyUpdateData",""" +
                """"readReceiptPolicy":"SEND","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate.ReflectReadReceiptPolicyUpdateData",""" +
                """"readReceiptPolicy":"DONT_SEND","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectTypingIndicatorPolicyUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate.""" +
                """ReflectTypingIndicatorPolicyUpdateData","typingIndicatorPolicy":"DEFAULT","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate.""" +
                """ReflectTypingIndicatorPolicyUpdateData","typingIndicatorPolicy":"SEND","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate.""" +
                """ReflectTypingIndicatorPolicyUpdateData","typingIndicatorPolicy":"DONT_SEND","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectActivityStateUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectActivityStateUpdate.ReflectActivityStateUpdateData",""" +
                """"identityState":"ACTIVE","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectActivityStateUpdate.ReflectActivityStateUpdateData",""" +
                """"identityState":"INACTIVE","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectActivityStateUpdate.ReflectActivityStateUpdateData",""" +
                """"identityState":"INVALID","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectFeatureMaskUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate.ReflectFeatureMaskUpdateData",""" +
                """"featureMask":12345,"identity":"01234567"}""",
        )
    }

    @Test
    fun testVerificationLevelUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate.ReflectVerificationLevelUpdateData",""" +
                """"verificationLevel":"UNVERIFIED","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate.ReflectVerificationLevelUpdateData",""" +
                """"verificationLevel":"SERVER_VERIFIED","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate.ReflectVerificationLevelUpdateData",""" +
                """"verificationLevel":"FULLY_VERIFIED","identity":"01234567"}""",
        )
    }

    @Test
    fun testWorkVerificationLevelUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate.""" +
                """ReflectWorkVerificationLevelUpdateData","workVerificationLevel":"NONE","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate.""" +
                """ReflectWorkVerificationLevelUpdateData","workVerificationLevel":"WORK_SUBSCRIPTION_VERIFIED","identity":"01234567"}""",
        )
    }

    @Test
    fun testIdentityTypeUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate.ReflectIdentityTypeUpdateData",""" +
                """"identityType":"NORMAL","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate.ReflectIdentityTypeUpdateData",""" +
                """"identityType":"WORK","identity":"01234567"}""",
        )
    }

    @Test
    fun testAcquaintanceLevelUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate.ReflectAcquaintanceLevelUpdateData",""" +
                """"acquaintanceLevel":"DIRECT","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate.ReflectAcquaintanceLevelUpdateData",""" +
                """"acquaintanceLevel":"GROUP","identity":"01234567"}""",
        )
    }

    @Test
    fun testUserDefinedProfilePictureUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectUserDefinedProfilePictureUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectUserDefinedProfilePictureUpdate.""" +
                """ReflectUserDefinedProfilePictureUpdateData","identity":"0BZYE2H9"}""",
        )
    }

    @Test
    fun testOnFSFeatureMaskDowngradedTask() {
        // Add the contact '01234567' so that creating the tasks works
        addTestData()

        assertValidEncoding(
            OnFSFeatureMaskDowngradedTask::class.java,
            """{"type":"ch.threema.app.tasks.OnFSFeatureMaskDowngradedTask.OnFSFeatureMaskDowngradedData","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectUnknownContactPolicyUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectUnknownContactPolicySyncUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectUnknownContactPolicySyncUpdate.""" +
                """ReflectUnknownContactPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectReadReceiptPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectReadReceiptPolicySyncUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectReadReceiptPolicySyncUpdate.ReadReceiptPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectTypingIndicatorPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectTypingIndicatorPolicySyncUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectTypingIndicatorPolicySyncUpdate.""" +
                """ReflectTypingIndicatorPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectBlockedIdentitiesSyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate.ReflectBlockedIdentitiesSyncUpdateData"}""",
        )
    }

    @Test
    fun testGroupCreateTask() {
        assertValidEncoding(
            GroupCreateTask::class.java,
            """{"type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData","name":"Name","profilePictureChange":""" +
                """{"type":"ch.threema.app.protocol.RemoveProfilePicture"},"members":["TESTTEST","01234567"],"groupIdentity":""" +
                """{"creatorIdentity":"01234567","groupId":42},"predefinedMessageIds":{"messageIdBytes1":[-121,-57,86,-82,-126,8,80,89],""" +
                """"messageIdBytes2":[54,-9,56,45,19,79,-33,80],"messageIdBytes3":[-62,57,-64,-73,-95,78,59,82],""" +
                """"messageIdBytes4":[-59,-117,93,109,46,-10,-119,118]}}""",
        )
    }

    @Test
    fun testGroupUpdateTask() {
        assertValidEncoding(
            GroupUpdateTask::class.java,
            """{"type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData","name":"Name","profilePictureChange":""" +
                """{"type":"ch.threema.app.protocol.RemoveProfilePicture"},"updatedMembers":["01234567"],"addedMembers":["TESTTEST",""" +
                """"01234567"],"removedMembers":["01234567"],"groupIdentity":{"creatorIdentity":"01234567","groupId":42},""" +
                """"predefinedMessageIds":{"messageIdBytes1":[5,-23,34,43,-15,49,22,-42],"messageIdBytes2":[-62,55,62,-15,-110,-56,58,-103],""" +
                """"messageIdBytes3":[-128,28,-10,110,14,-39,105,-105],"messageIdBytes4":[-9,115,118,38,-118,-73,99,89]}}""",
        )
    }

    @Test
    fun testOutgoingGroupDisbandTask() {
        assertValidEncoding(
            OutgoingGroupDisbandTask::class.java,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDisbandTask.OutgoingGroupDisbandTaskData","groupIdentity":""" +
                """{"creatorIdentity":"TESTTEST","groupId":42},"members":["01234567"],"messageId":[0,1,2,3,4,5,6,7]}""",
        )
    }

    @Test
    fun testContactNotificationTriggerPolicyOverrideUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.""" +
                """ReflectNotificationTriggerPolicyOverrideUpdateData","notificationTriggerPolicyOverride":1740396679447,""" +
                """"contactIdentity":"01234567"}""",
        )
    }

    @Test
    fun testGroupNotificationTriggerPolicyOverrideUpdate() {
        addTestData()
        assertValidEncoding(
            ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate::class.java,
            """{"type":"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.""" +
                """ReflectNotificationTriggerPolicyOverrideUpdateData","newNotificationTriggerPolicyOverride":""" +
                """{"type":"ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedUntil","dbValue":1740396953761,""" +
                """"utcMillis":1740396953761},"groupIdentity":{"creatorIdentity":"01234567","groupId":6361180283070237492}}""",
        )
    }

    @Test
    fun testReflectContactConversationCategoryUpdate() {
        assertValidEncoding(
            expectedTaskClass = ReflectContactSyncUpdateTask.ReflectConversationCategoryUpdate::class.java,
            encodedTask = "{\"type\":\"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectConversationCategoryUpdate" +
                ".ReflectContactConversationCategoryUpdateData\",\"contactIdentity\":\"01234567\",\"isPrivateChat\":true}",
        )
    }

    @Test
    fun testReflectGroupConversationCategoryUpdate() {
        addTestData()
        assertValidEncoding(
            expectedTaskClass = ReflectGroupSyncUpdateTask.ReflectGroupConversationCategoryUpdateTask::class.java,
            encodedTask = "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectGroupConversationCategoryUpdateTask" +
                ".ReflectGroupConversationCategoryData\",\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":6361180283070237492}" +
                "\"isPrivateChat\":true}",
        )
    }

    @Test
    fun testReflectContactConversationVisibilityArchiveUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectConversationVisibilityArchiveUpdate::class.java,
            "{\"type\":\"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectConversationVisibilityArchiveUpdate" +
                ".ReflectConversationVisibilityArchiveUpdateData\",\"isArchived\":true,\"contactIdentity\":\"01234567\"}",
        )
    }

    @Test
    fun testReflectGroupConversationVisibilityArchiveUpdate() {
        addTestData()
        assertValidEncoding(
            ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityArchiveUpdate::class.java,
            "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityArchiveUpdate" +
                ".ReflectGroupConversationVisibilityArchiveUpdateData\",\"isArchived\":true," +
                "\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":6361180283070237492}}",
        )
    }

    @Test
    fun testReflectContactConversationVisibilityPinnedUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectConversationVisibilityPinnedUpdate::class.java,
            "{\"type\":\"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectConversationVisibilityPinnedUpdate" +
                ".ReflectConversationVisibilityPinnedUpdateData\",\"isPinned\":true,\"contactIdentity\":\"01234567\"}",
        )
    }

    @Test
    fun testReflectGroupConversationVisibilityPinnedUpdate() {
        addTestData()
        assertValidEncoding(
            ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityPinnedUpdate::class.java,
            "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityPinnedUpdate" +
                ".ReflectGroupConversationVisibilityPinnedUpdateData\",\"isPinned\":true," +
                "\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":6361180283070237492}}",
        )
    }

    @Test
    fun testDeactivateMultiDeviceTask() {
        assertValidEncoding(
            expectedTaskClass = DeactivateMultiDeviceTask::class.java,
            encodedTask = """{"type":"ch.threema.app.tasks.DeactivateMultiDeviceTask.DeactivateMultiDeviceTaskData"}""",
        )
    }

    @Test
    fun testDeactivateMultiDeviceIfAloneTask() {
        assertValidEncoding(
            expectedTaskClass = DeactivateMultiDeviceIfAloneTask::class.java,
            encodedTask = """{"type":"ch.threema.app.tasks.DeactivateMultiDeviceIfAloneTask.DeactivateMultiDeviceIfAloneTaskData"}""",
        )
    }

    private fun addTestData() = runBlocking {
        val identity = "01234567"
        if (serviceManager.modelRepositories.contacts.getByIdentity(identity) != null) {
            // If the contact already exists, we do not add it again
            return@runBlocking
        }

        serviceManager.identityStore.storeIdentity(identity, "", byteArrayOf(), byteArrayOf())

        serviceManager.modelRepositories.contacts.createFromLocal(
            ContactModelData(
                identity = identity,
                publicKey = ByteArray(NaCl.PUBLICKEYBYTES),
                createdAt = Date(42),
                firstName = "0123",
                lastName = "4567",
                nickname = "01",
                verificationLevel = VerificationLevel.SERVER_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 0u,
                typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                isArchived = false,
                androidContactLookupKey = null,
                localAvatarExpires = null,
                isRestored = false,
                profilePictureBlobId = null,
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
        )

        serviceManager.modelRepositories.groups.persistNewGroup(
            GroupModelData(
                groupIdentity = GroupIdentity(
                    creatorIdentity = identity,
                    groupId = 6361180283070237492,
                ),
                name = null,
                createdAt = Date(),
                synchronizedAt = null,
                lastUpdate = null,
                isArchived = false,
                precomputedColorIndex = null,
                groupDescription = null,
                groupDescriptionChangedAt = null,
                otherMembers = setOf(identity),
                userState = ch.threema.storage.models.GroupModel.UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            ),
        )
    }

    private fun <T> assertValidEncoding(expectedTaskClass: Class<T>, encodedTask: String) {
        val decodedTask = encodedTask.decodeToTask()
        assertNotNull(decodedTask)
        assertEquals(expectedTaskClass, decodedTask!!::class.java)
    }

    private fun String.decodeToTask(): Task<*, TaskCodec>? {
        return try {
            Json.decodeFromString<SerializableTaskData>(this).createTask(serviceManager)
        } catch (e: Exception) {
            fail("Task data decoding error for task '$this'. Error: $e")
            null
        }
    }
}
