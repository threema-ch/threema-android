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
import ch.threema.base.crypto.NaCl
import ch.threema.data.datatypes.IdColor
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
import ch.threema.storage.models.ContactModel
import java.util.Date
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

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
            OutgoingContactDeliveryReceiptMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingContactDeliveryReceiptMessageTask.OutgoingDeliveryReceiptMessageData",""" +
                """"receiptType":1,"messageIds":["0000000000000000"],"date":"1234567890","toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testFileMessageTask() {
        assertValidEncoding(
            OutgoingFileMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingFileMessageTask.OutgoingFileMessageData","messageModelId":1,"receiverType":0,""" +
                """"recipientIdentities":["01234567"],"thumbnailBlobId":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}""",
        )
    }

    @Test
    fun testGroupDeleteProfilePictureTask() {
        assertValidEncoding(
            OutgoingGroupDeleteProfilePictureTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDeleteProfilePictureTask.OutgoingGroupDeleteProfilePictureData",""" +
                """"groupId":[0,0,0,0,0,0,0,0],"creatorIdentity":"01234567","receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupDeliveryReceiptMessageTask() {
        assertValidEncoding(
            OutgoingGroupDeliveryReceiptMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDeliveryReceiptMessageTask.OutgoingGroupDeliveryReceiptMessageData",""" +
                """"messageModelId":0,"recipientIdentities":["01234567","01234567"],"receiptType":0}""",
        )
    }

    @Test
    fun testGroupLeaveTask() {
        assertValidEncoding(
            OutgoingGroupLeaveTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupLeaveTask.OutgoingGroupLeaveTaskData",""" +
                """"groupIdentity":{"creatorIdentity":"01234567","groupId":42},"memberIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupNameTask() {
        assertValidEncoding(
            OutgoingGroupNameTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupNameTask.OutgoingGroupNameData","groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567","groupName":"groupName","receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupProfilePictureTask() {
        assertValidEncoding(
            OutgoingGroupProfilePictureTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupProfilePictureTask.OutgoingGroupProfilePictureData",""" +
                """"groupId":[0,0,0,0,0,0,0,0],"creatorIdentity":"01234567","receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupSetupTask() {
        assertValidEncoding(
            OutgoingGroupSetupTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupSetupTask.OutgoingGroupSetupData","groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567","memberIdentities":["01234567"],"receiverIdentities":["01234567"],""" +
                """"messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupSyncRequestTask() {
        assertValidEncoding(
            OutgoingGroupSyncRequestTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupSyncRequestTask.OutgoingGroupSyncRequestData",""" +
                """"groupId":[0,0,0,0,0,0,0,0],"creatorIdentity":"01234567","messageId":[0,0,0,0,0,0,0,0]}""",
        )
    }

    @Test
    fun testGroupSyncTask() {
        assertValidEncoding(
            OutgoingGroupSyncTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupSyncTask.OutgoingGroupSyncData","groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567","receiverIdentities":["01234567"]}""",
        )
    }

    @Test
    fun testLocationMessageTask() {
        assertValidEncoding(
            OutgoingLocationMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingLocationMessageTask.OutgoingLocationMessageTaskData","messageModelId":0,""" +
                """"recipientIdentities":["01234567","01234567"],"receiverType":0}""",
        )
    }

    @Test
    fun testPollSetupMessageTask() {
        assertValidEncoding(
            OutgoingPollSetupMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingPollSetupMessageTask.OutgoingPollSetupMessageData","messageModelId":0,""" +
                """"recipientIdentities":["01234567","01234567"],"receiverType":0,"ballotId":[-58,11,102,-122,-119,-102,19,-10],""" +
                """"ballotData":"{\"d\":\"description\",\"s\":0,\"a\":0,\"t\":1,\"o\":0,\"u\":0,""" +
                """\"c\":[{\"i\":0,\"n\":\"desc\",\"o\":0,\"r\":[0],\"t\":0}],\"p\":[\"01234567\"]}"}""",
        )
    }

    @Test
    fun testPollVoteContactMessageTask() {
        assertValidEncoding(
            OutgoingPollVoteContactMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingPollVoteContactMessageTask.OutgoingPollVoteContactMessageData",""" +
                """"messageId":"0000000000000000","ballotId":[-127,-79,80,-109,-98,62,-3,81],"ballotCreator":"01234567",""" +
                """"ballotVotes":[{"first":0,"second":0}],"toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testPollVoteGroupMessageTask() {
        assertValidEncoding(
            OutgoingPollVoteGroupMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingPollVoteGroupMessageTask.OutgoingPollVoteGroupMessageData",""" +
                """"messageId":"0000000000000000","recipientIdentities":["01234567","01234567"],"ballotId":[52,64,-6,18,2,-71,124,-19],""" +
                """"ballotCreator":"01234567","ballotVotes":[{"first":0,"second":0}],"ballotType":"INTERMEDIATE",""" +
                """"apiGroupId":"0000000000000000","groupCreator":"01234567"}""",
        )
    }

    @Test
    fun testTextMessageTask() {
        assertValidEncoding(
            OutgoingTextMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingTextMessageTask.OutgoingTextMessageData","messageModelId":0,""" +
                """"recipientIdentities":["01234567","01234567"],"receiverType":0}""",
        )
    }

    @Test
    fun testSendProfilePictureTask() {
        assertValidEncoding(
            SendProfilePictureTask::class,
            """{"type":"ch.threema.app.tasks.SendProfilePictureTask.SendProfilePictureData","toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testSendPushTokenTask() {
        assertValidEncoding(
            SendPushTokenTask::class,
            """{"type":"ch.threema.app.tasks.SendPushTokenTask.SendPushTokenData","token":"token","tokenType":0}""",
        )
    }

    @Test
    fun testOutgoingContactRequestProfilePictureTask() {
        assertValidEncoding(
            OutgoingContactRequestProfilePictureTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingContactRequestProfilePictureTask.OutgoingContactRequestProfilePictureData",""" +
                """"toIdentity":"01234567"}""",
        )
    }

    @Test
    fun testDeleteAndTerminateFSSessionsTask() {
        // Add the contact '01234567' so that creating the tasks works
        addTestData()

        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"RESET"}""",
        )
        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"UNKNOWN_SESSION"}""",
        )
        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"DISABLED_BY_LOCAL"}""",
        )
        assertValidEncoding(
            DeleteAndTerminateFSSessionsTask::class,
            """{"type":"ch.threema.app.tasks.DeleteAndTerminateFSSessionsTask.DeleteAndTerminateFSSessionsTaskData",""" +
                """"identity":"01234567","cause":"DISABLED_BY_REMOTE"}""",
        )
    }

    @Test
    fun testApplicationUpdateStepsTask() {
        assertValidEncoding(
            ApplicationUpdateStepsTask::class,
            """{"type":"ch.threema.app.tasks.ApplicationUpdateStepsTask.ApplicationUpdateStepsData"}""",
        )
    }

    @Test
    fun testFSRefreshStepsTask() {
        assertValidEncoding(
            FSRefreshStepsTask::class,
            """{"type":"ch.threema.app.tasks.FSRefreshStepsTask.FSRefreshStepsTaskData","contactIdentities":["01234567"]}""",
        )
    }

    @Test
    fun testOutboundIncomingContactMessageUpdateReadTask() {
        assertValidEncoding(
            OutboundIncomingContactMessageUpdateReadTask::class,
            """{"type":"ch.threema.app.tasks.OutboundIncomingContactMessageUpdateReadTask.OutboundIncomingContactMessageUpdateReadData",""" +
                """"messageIds":[[0,-1,2,3,4,5,6,7]],"timestamp":1704067200000,"recipientIdentity":"01234567"}""",
        )
    }

    @Test
    fun testOutboundIncomingGroupMessageUpdateReadTask() {
        assertValidEncoding(
            OutboundIncomingGroupMessageUpdateReadTask::class,
            """{"type":"ch.threema.app.tasks.OutboundIncomingGroupMessageUpdateReadTask.OutboundIncomingGroupMessageUpdateReadData",""" +
                """"messageIds":[[0,-1,2,3,4,5,6,7]],"timestamp":1704067200000,"groupId":[0,0,0,0,0,0,0,0],""" +
                """"creatorIdentity":"01234567"}""",
        )
    }

    @Test
    fun testOutgoingContactEditMessageTask() {
        assertValidEncoding(
            OutgoingContactEditMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingContactEditMessageTask.OutgoingContactEditMessageData",""" +
                """"toIdentity":"01234567","messageModelId":0, "messageId":[0,0,0,0,0,0,0,0], "editedText":"test", "editedAt":0}""",
        )
    }

    @Test
    fun testOutgoingGroupEditMessageTask() {
        assertValidEncoding(
            OutgoingGroupEditMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupEditMessageTask.OutgoingGroupEditMessageData","messageModelId":0, """ +
                """"messageId":[0,0,0,0,0,0,0,0], "editedText":"test", "editedAt":0,"recipientIdentities":["01234567","01234567"]}""",
        )
    }

    @Test
    fun testOutgoingContactDeleteMessageTask() {
        assertValidEncoding(
            OutgoingContactDeleteMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingContactDeleteMessageTask.OutgoingContactDeleteMessageData",""" +
                """"toIdentity":"01234567","messageModelId":0, "messageId":[0,0,0,0,0,0,0,0], "deletedAt":0}""",
        )
    }

    @Test
    fun testOutgoingGroupDeleteMessageTask() {
        assertValidEncoding(
            OutgoingGroupDeleteMessageTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDeleteMessageTask.OutgoingGroupDeleteMessageData",""" +
                """"messageModelId":0,"messageId":[0,0,0,0,0,0,0,0],"deletedAt":0,"recipientIdentities":["01234567","01234567"]}""",
        )
    }

    @Test
    fun testReflectUserProfileNicknameSyncTask() {
        assertValidEncoding(
            ReflectUserProfileNicknameSyncTask::class,
            """{"type":"ch.threema.app.tasks.ReflectUserProfileNicknameSyncTask.ReflectUserProfileNicknameSyncTaskData",""" +
                """"newNickname":"nick"}""",
        )
    }

    @Test
    fun testReflectUserProfilePictureSyncTask() {
        assertValidEncoding(
            ReflectUserProfilePictureSyncTask::class,
            """{"type":"ch.threema.app.tasks.ReflectUserProfilePictureSyncTask.ReflectUserProfilePictureSyncTaskData"}""",
        )
    }

    @Test
    fun testReflectUserProfileShareWithPolicySyncTask() {
        assertValidEncoding(
            ReflectUserProfileShareWithPolicySyncTask::class,
            """{"type":"ch.threema.app.tasks.ReflectUserProfileShareWithPolicySyncTask.ReflectUserProfileShareWithPolicySyncTaskData",""" +
                """"newPolicy":"NOBODY"}""",
        )
    }

    @Test
    fun testReflectUserProfileShareWithAllowListSyncTask() {
        assertValidEncoding(
            ReflectUserProfileShareWithAllowListSyncTask::class,
            """{"type":""" +
                """"ch.threema.app.tasks.ReflectUserProfileShareWithAllowListSyncTask.ReflectUserProfileShareWithAllowListSyncTaskData",""" +
                """"allowedIdentities":["01234567", "01234568"]}""",
        )
    }

    @Test
    fun testReflectUserProfileIdentityLinksTask() {
        assertValidEncoding(
            expectedTaskClass = ReflectUserProfileIdentityLinksTask::class,
            encodedTask = """{"type":"ch.threema.app.tasks.ReflectUserProfileIdentityLinksTask.ReflectUserProfileIdentityLinksTaskData"}""",
        )
    }

    @Test
    fun testReflectNameUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNameUpdate.ReflectNameUpdateData",""" +
                """"firstName":"A","lastName":"B","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNameUpdate.ReflectNameUpdateData",""" +
                """"firstName":"A","lastName":"","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNameUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNameUpdate.ReflectNameUpdateData",""" +
                """"firstName":"","lastName":"B","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectReadReceiptPolicyUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate.ReflectReadReceiptPolicyUpdateData",""" +
                """"readReceiptPolicy":"DEFAULT","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate.ReflectReadReceiptPolicyUpdateData",""" +
                """"readReceiptPolicy":"SEND","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectReadReceiptPolicyUpdate.ReflectReadReceiptPolicyUpdateData",""" +
                """"readReceiptPolicy":"DONT_SEND","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectTypingIndicatorPolicyUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate.""" +
                """ReflectTypingIndicatorPolicyUpdateData","typingIndicatorPolicy":"DEFAULT","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate.""" +
                """ReflectTypingIndicatorPolicyUpdateData","typingIndicatorPolicy":"SEND","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectTypingIndicatorPolicyUpdate.""" +
                """ReflectTypingIndicatorPolicyUpdateData","typingIndicatorPolicy":"DONT_SEND","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectActivityStateUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectActivityStateUpdate.ReflectActivityStateUpdateData",""" +
                """"identityState":"ACTIVE","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectActivityStateUpdate.ReflectActivityStateUpdateData",""" +
                """"identityState":"INACTIVE","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectActivityStateUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectActivityStateUpdate.ReflectActivityStateUpdateData",""" +
                """"identityState":"INVALID","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectFeatureMaskUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectFeatureMaskUpdate.ReflectFeatureMaskUpdateData",""" +
                """"featureMask":12345,"identity":"01234567"}""",
        )
    }

    @Test
    fun testVerificationLevelUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate.ReflectVerificationLevelUpdateData",""" +
                """"verificationLevel":"UNVERIFIED","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate.ReflectVerificationLevelUpdateData",""" +
                """"verificationLevel":"SERVER_VERIFIED","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectVerificationLevelUpdate.ReflectVerificationLevelUpdateData",""" +
                """"verificationLevel":"FULLY_VERIFIED","identity":"01234567"}""",
        )
    }

    @Test
    fun testWorkVerificationLevelUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate.""" +
                """ReflectWorkVerificationLevelUpdateData","workVerificationLevel":"NONE","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectWorkVerificationLevelUpdate.""" +
                """ReflectWorkVerificationLevelUpdateData","workVerificationLevel":"WORK_SUBSCRIPTION_VERIFIED","identity":"01234567"}""",
        )
    }

    @Test
    fun testIdentityTypeUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate.ReflectIdentityTypeUpdateData",""" +
                """"identityType":"NORMAL","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectIdentityTypeUpdate.ReflectIdentityTypeUpdateData",""" +
                """"identityType":"WORK","identity":"01234567"}""",
        )
    }

    @Test
    fun testAcquaintanceLevelUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate.ReflectAcquaintanceLevelUpdateData",""" +
                """"acquaintanceLevel":"DIRECT","identity":"01234567"}""",
        )
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectAcquaintanceLevelUpdate.ReflectAcquaintanceLevelUpdateData",""" +
                """"acquaintanceLevel":"GROUP","identity":"01234567"}""",
        )
    }

    @Test
    fun testUserDefinedProfilePictureUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectUserDefinedProfilePictureUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectUserDefinedProfilePictureUpdate.""" +
                """ReflectUserDefinedProfilePictureUpdateData","identity":"0BZYE2H9"}""",
        )
    }

    @Test
    fun testOnFSFeatureMaskDowngradedTask() {
        // Add the contact '01234567' so that creating the tasks works
        addTestData()

        assertValidEncoding(
            OnFSFeatureMaskDowngradedTask::class,
            """{"type":"ch.threema.app.tasks.OnFSFeatureMaskDowngradedTask.OnFSFeatureMaskDowngradedData","identity":"01234567"}""",
        )
    }

    @Test
    fun testReflectContactSyncPolicyUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectContactSyncPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectContactSyncPolicySyncUpdate.""" +
                """ReflectContactSyncPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectUnknownContactPolicyUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectUnknownContactPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectUnknownContactPolicySyncUpdate.""" +
                """ReflectUnknownContactPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectReadReceiptPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectReadReceiptPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectReadReceiptPolicySyncUpdate.ReadReceiptPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectTypingIndicatorPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectTypingIndicatorPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectTypingIndicatorPolicySyncUpdate.""" +
                """ReflectTypingIndicatorPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectO2oCallPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectO2oCallPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectO2oCallPolicySyncUpdate.""" +
                """ReflectO2oCallPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectO2oCallConnectionPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectO2oCallConnectionPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectO2oCallConnectionPolicySyncUpdate.""" +
                """ReflectO2oCallConnectionPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectO2oCallVideoPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectO2oCallVideoPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectO2oCallVideoPolicySyncUpdate.""" +
                """ReflectO2oCallVideoPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectGroupCallPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectGroupCallPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectGroupCallPolicySyncUpdate.""" +
                """ReflectGroupCallPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectScreenshotPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectScreenshotPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectScreenshotPolicySyncUpdate.""" +
                """ReflectScreenshotPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectKeyboardDataCollectionPolicySyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectKeyboardDataCollectionPolicySyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectKeyboardDataCollectionPolicySyncUpdate.""" +
                """ReflectKeyboardDataCollectionPolicySyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectBlockedIdentitiesSyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate.ReflectBlockedIdentitiesSyncUpdateData"}""",
        )
    }

    @Test
    fun testReflectExcludeFromSyncIdentitiesSyncUpdate() {
        assertValidEncoding(
            ReflectSettingsSyncTask.ReflectExcludeFromSyncIdentitiesSyncUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectSettingsSyncTask.ReflectExcludeFromSyncIdentitiesSyncUpdate.""" +
                """ReflectExcludeFromSyncIdentitiesSyncUpdateData"}""",
        )
    }

    @Test
    fun testGroupCreateTask() {
        assertValidEncoding(
            GroupCreateTask::class,
            """
                {
                    "type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData",
                    "name":"Name",
                    "serializableExpectedProfilePictureChange":{
                        "type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData.Companion.SerializableExpectedProfilePictureChange.Set",
                        "profilePictureBytes":[-1,-40,-1,-32,0,16,74,70,73,70,0,1,1,0,0,1,0,1,0,0,-1,-30,2,40,73,67,67,95,80,82,79,70,73,76,69,0],
                        "blobId":[-111,-102,108,13,-47,127,-69,77,86,-9,80,-104,-19,52,2,96],
                        "encryptionKey":[-42,-1,-3,3,-1,1,60,66,3,120,82,12,-82,-72,-1,120,-63,91,-119,-40,-112,89,2,-99,120,109,-1,-114,65,14,33,37],
                        "size":1171
                    },
                    "members":["01234567"],
                    "groupIdentity":{"creatorIdentity":"07654321","groupId":-8035334611319732223},
                    "serializablePredefinedMessageIds":{
                        "messageId1":4963550096699540653,
                        "messageId2":3028276473780066008,
                        "messageId3":-4433228351225673089,
                        "messageId4":3269950967196984857
                    }
                }
            """.trimIndent(),
        )

        assertValidEncoding(
            GroupCreateTask::class,
            """
                {
                    "type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData",
                    "name":"Name",
                    "serializableExpectedProfilePictureChange":{
                        "type":"ch.threema.app.tasks.GroupCreateTask.GroupCreateTaskData.Companion.SerializableExpectedProfilePictureChange.SetWithoutUpload",
                        "profilePictureBytes":[-1,-40,-1,-32,0,16,74,70,73,70,0,1,1,0,0,1,0,1,0,0,-1,-30,2,40,73,67,67,95,80,82,79,70,73,76,69,0]
                    },
                    "members":["01234567"],
                    "groupIdentity":{"creatorIdentity":"07654321","groupId":-8035334611319732223},
                    "serializablePredefinedMessageIds":{
                        "messageId1":4963550096699540653,
                        "messageId2":3028276473780066008,
                        "messageId3":-4433228351225673089,
                        "messageId4":3269950967196984857
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun testGroupUpdateTask() {
        assertValidEncoding(
            GroupUpdateTask::class,
            """
                {
                    "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData",
                    "name":null,
                    "serializableExpectedProfilePictureChange":{
                        "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData.Companion.SerializableExpectedProfilePictureChange.Set",
                        "profilePictureBytes":[-1,-40,-1,-32,0,16,74,70,73,70,0,1,1,0,0,1,0,1,0,0,-1,-30,2,40,73,67,67,95,80,82,79,70,73,76,69,0],
                        "blobId":[-112,115,-35,44,75,-71,56,34,-35,-28,-18,-49,108,-6,17,-2],
                        "encryptionKey":[124,-84,89,-29,99,-56,-80,45,4,124,43,99,81,-15,64,-55,25,-54,53,115,27,-6,23,55,39,-5,-50,14,-79,125,24,63],
                        "size":1179
                    },
                    "updatedMembers":["01234567"],
                    "addedMembers":[],
                    "removedMembers":[],
                    "groupIdentity":{"creatorIdentity":"07654321","groupId":5588186647911236286},
                    "serializablePredefinedMessageIds":{
                        "messageId1":139294365944476775,
                        "messageId2":-413801300579649603,
                        "messageId3":525071018028493227,
                        "messageId4":-388653292512991891
                    }
                }
            """.trimIndent(),
        )

        assertValidEncoding(
            GroupUpdateTask::class,
            """
                {
                    "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData",
                    "name":null,
                    "serializableExpectedProfilePictureChange":{
                        "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData.Companion.SerializableExpectedProfilePictureChange.SetWithoutUpload",
                        "profilePictureBytes":[-1,-40,-1,-32,0,16,74,70,73,70,0,1,1,0,0,1,0,1,0,0,-1,-30,2,40,73,67,67,95,80,82,79,70,73,76,69,0]
                    },
                    "updatedMembers":["01234567"],
                    "addedMembers":[],
                    "removedMembers":[],
                    "groupIdentity":{"creatorIdentity":"07654321","groupId":5588186647911236286},
                    "serializablePredefinedMessageIds":{
                        "messageId1":139294365944476775,
                        "messageId2":-413801300579649603,
                        "messageId3":525071018028493227,
                        "messageId4":-388653292512991891
                    }
                }
            """.trimIndent(),
        )

        assertValidEncoding(
            GroupUpdateTask::class,
            """
                {
                    "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData",
                    "name":null,
                    "serializableExpectedProfilePictureChange":{
                        "type":"ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData.Companion.SerializableExpectedProfilePictureChange.NoChange"
                    },
                    "updatedMembers":["01234567"],
                    "addedMembers":[],
                    "removedMembers":[],
                    "groupIdentity":{"creatorIdentity":"07654321","groupId":5588186647911236286},
                    "serializablePredefinedMessageIds":{
                        "messageId1":139294365944476775,
                        "messageId2":-413801300579649603,
                        "messageId3":525071018028493227,
                        "messageId4":-388653292512991891
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun testOutgoingGroupDisbandTask() {
        assertValidEncoding(
            OutgoingGroupDisbandTask::class,
            """{"type":"ch.threema.app.tasks.OutgoingGroupDisbandTask.OutgoingGroupDisbandTaskData","groupIdentity":""" +
                """{"creatorIdentity":"TESTTEST","groupId":42},"members":["01234567"],"messageId":[0,1,2,3,4,5,6,7]}""",
        )
    }

    @Test
    fun testContactNotificationTriggerPolicyOverrideUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.""" +
                """ReflectNotificationTriggerPolicyOverrideUpdateData","notificationTriggerPolicyOverride":1740396679447,""" +
                """"contactIdentity":"01234567"}""",
        )
    }

    @Test
    fun testGroupNotificationTriggerPolicyOverrideUpdate() {
        addTestData()
        assertValidEncoding(
            ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate::class,
            """{"type":"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.""" +
                """ReflectNotificationTriggerPolicyOverrideUpdateData","newNotificationTriggerPolicyOverride":""" +
                """{"type":"ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedUntil","dbValue":1740396953761,""" +
                """"utcMillis":1740396953761},"groupIdentity":{"creatorIdentity":"01234567","groupId":6361180283070237492}}""",
        )
    }

    @Test
    fun testReflectContactConversationCategoryUpdate() {
        assertValidEncoding(
            expectedTaskClass = ReflectContactSyncUpdateTask.ReflectConversationCategoryUpdate::class,
            encodedTask = "{\"type\":\"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectConversationCategoryUpdate" +
                ".ReflectContactConversationCategoryUpdateData\",\"contactIdentity\":\"01234567\",\"isPrivateChat\":true}",
        )
    }

    @Test
    fun testReflectGroupConversationCategoryUpdate() {
        addTestData()
        assertValidEncoding(
            expectedTaskClass = ReflectGroupSyncUpdateTask.ReflectGroupConversationCategoryUpdateTask::class,
            encodedTask = "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectGroupConversationCategoryUpdateTask" +
                ".ReflectGroupConversationCategoryData\",\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":6361180283070237492}" +
                "\"isPrivateChat\":true}",
        )
    }

    @Test
    fun testReflectContactConversationVisibilityArchiveUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectConversationVisibilityArchiveUpdate::class,
            "{\"type\":\"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectConversationVisibilityArchiveUpdate" +
                ".ReflectConversationVisibilityArchiveUpdateData\",\"isArchived\":true,\"contactIdentity\":\"01234567\"}",
        )
    }

    @Test
    fun testReflectGroupConversationVisibilityArchiveUpdate() {
        addTestData()
        assertValidEncoding(
            ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityArchiveUpdate::class,
            "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityArchiveUpdate" +
                ".ReflectGroupConversationVisibilityArchiveUpdateData\",\"isArchived\":true," +
                "\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":6361180283070237492}}",
        )
    }

    @Test
    fun testReflectContactConversationVisibilityPinnedUpdate() {
        assertValidEncoding(
            ReflectContactSyncUpdateTask.ReflectConversationVisibilityPinnedUpdate::class,
            "{\"type\":\"ch.threema.app.tasks.ReflectContactSyncUpdateTask.ReflectConversationVisibilityPinnedUpdate" +
                ".ReflectConversationVisibilityPinnedUpdateData\",\"isPinned\":true,\"contactIdentity\":\"01234567\"}",
        )
    }

    @Test
    fun testReflectGroupConversationVisibilityPinnedUpdate() {
        addTestData()
        assertValidEncoding(
            ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityPinnedUpdate::class,
            "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityPinnedUpdate" +
                ".ReflectGroupConversationVisibilityPinnedUpdateData\",\"isPinned\":true," +
                "\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":6361180283070237492}}",
        )
    }

    @Test
    fun testSyncFormerlyOrphanedGroupsTask() {
        assertValidEncoding(
            SyncFormerlyOrphanedGroupsTask::class,
            """{"type":"ch.threema.app.tasks.SyncFormerlyOrphanedGroupsTask.SyncFormerlyOrphanedGroupsTaskData"}""",
        )
    }

    @Test
    fun testDeactivateMultiDeviceTask() {
        assertValidEncoding(
            expectedTaskClass = DeactivateMultiDeviceTask::class,
            encodedTask = """{"type":"ch.threema.app.tasks.DeactivateMultiDeviceTask.DeactivateMultiDeviceTaskData"}""",
        )
    }

    @Test
    fun testDeactivateMultiDeviceIfAloneTask() {
        assertValidEncoding(
            expectedTaskClass = DeactivateMultiDeviceIfAloneTask::class,
            encodedTask = """{"type":"ch.threema.app.tasks.DeactivateMultiDeviceIfAloneTask.DeactivateMultiDeviceIfAloneTaskData"}""",
        )
    }

    @Test
    fun testConvertGroupProfilePictureTask() {
        assertValidEncoding(
            expectedTaskClass = ConvertGroupProfilePictureTask::class,
            encodedTask = """{"type":"ch.threema.app.tasks.ConvertGroupProfilePictureTask.ConvertGroupProfilePictureTaskData",""" +
                """"groupIdentity":{"creatorIdentity":"01234567","groupId":42}}""",
        )
    }

    private fun addTestData() = runBlocking {
        val identity = "01234567"
        if (serviceManager.modelRepositories.contacts.getByIdentity(identity) != null) {
            // If the contact already exists, we do not add it again
            return@runBlocking
        }

        val keyPair = NaCl.generateKeypair()
        serviceManager.identityStore.storeIdentity(
            identity = identity,
            serverGroup = "",
            privateKey = keyPair.privateKey,
        )

        serviceManager.modelRepositories.contacts.createFromLocal(
            ContactModelData(
                identity = identity,
                publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES),
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
                androidContactLookupInfo = null,
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
                precomputedIdColor = IdColor.invalid(),
                groupDescription = null,
                groupDescriptionChangedAt = null,
                otherMembers = setOf(identity),
                userState = ch.threema.storage.models.GroupModel.UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            ),
        )
    }

    private fun assertValidEncoding(expectedTaskClass: KClass<*>, encodedTask: String) {
        val decodedTask = Json.decodeFromString<SerializableTaskData>(encodedTask).createTask(serviceManager)
        assertNotNull(decodedTask)
        assertEquals(expectedTaskClass, decodedTask::class)
    }
}
