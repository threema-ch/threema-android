/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.app.service

import ch.threema.app.BuildConfig
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.services.group.GroupInviteService
import ch.threema.app.services.group.GroupInviteServiceImpl
import ch.threema.domain.protocol.csp.messages.group.GroupInviteData
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken
import ch.threema.protobuf.url_payloads.GroupInvite.ConfirmationMode
import ch.threema.storage.models.group.GroupInviteModel
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupInviteServiceTest {
    private lateinit var groupService: GroupService
    private lateinit var groupInviteService: GroupInviteService

    @BeforeTest
    fun setUp() {
        val userService = mockk<UserService> {
            every { identity } returns TEST_IDENTITY
        }
        groupService = ThreemaApplication.requireServiceManager().groupService
        val databaseService = ThreemaApplication.requireServiceManager().databaseService
        groupInviteService = GroupInviteServiceImpl(userService, groupService, databaseService)
    }

    @Test
    fun testEncodeDecodeGroupInvite() {
        val encodedGroupInvite = groupInviteService.encodeGroupInviteLink(testInviteModel)

        assertEquals("https", encodedGroupInvite.scheme)
        assertEquals(BuildConfig.groupLinkActionUrl, encodedGroupInvite.authority)
        assertEquals("/join", encodedGroupInvite.path)
        assertEquals(TEST_ENCODED_INVITE, encodedGroupInvite.encodedFragment)
    }

    @Test
    fun testDecodeGroupInvite() {
        val inviteDataFromDecodedUri = groupInviteService.decodeGroupInviteLink(TEST_ENCODED_INVITE)

        assertEquals(testInviteData.adminIdentity, inviteDataFromDecodedUri.adminIdentity)
        assertEquals(testInviteData.token, inviteDataFromDecodedUri.token)
        assertEquals(testInviteData.groupName, inviteDataFromDecodedUri.groupName)
        assertEquals(testInviteData.confirmationMode, inviteDataFromDecodedUri.confirmationMode)
    }

    private companion object {
        const val TEST_GROUP_NAME = "A nice little group"
        const val TEST_INVITE_NAME = "New unnamed link"
        const val TEST_IDENTITY = "ECHOECHO"
        const val TEST_ENCODED_INVITE = "RUNIT0VDSE86MDAwMTAyMDMwNDA1MDYwNzA4MDkwYTBiMGMwZDBlMGY6QSBuaWNlIGxpdHRsZSBncm91cDow"
        val testTokenValid: GroupInviteToken
        val testInviteModel: GroupInviteModel
        val testInviteData: GroupInviteData

        init {
            testTokenValid = GroupInviteToken(ByteArray(16) { it.toByte() })
            testInviteModel = GroupInviteModel.Builder()
                .withGroupName(TEST_GROUP_NAME)
                .withInviteName(TEST_INVITE_NAME)
                .withToken(testTokenValid)
                .withManualConfirmation(false)
                .build()
            testInviteData = GroupInviteData(
                TEST_IDENTITY,
                testTokenValid,
                TEST_GROUP_NAME,
                ConfirmationMode.AUTOMATIC,
            )
        }
    }
}
