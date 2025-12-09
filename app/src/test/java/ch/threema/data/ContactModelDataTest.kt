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

package ch.threema.data

import ch.threema.common.now
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactModelDataTest {
    private val exampleContactModelData = ContactModelData(
        identity = "TESTTEST",
        publicKey = Random.nextBytes(32),
        createdAt = now(),
        firstName = "Test",
        lastName = "Contact",
        nickname = null,
        idColor = IdColor(13),
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
        identityType = IdentityType.NORMAL,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 7uL,
        readReceiptPolicy = ReadReceiptPolicy.DONT_SEND,
        typingIndicatorPolicy = TypingIndicatorPolicy.SEND,
        isArchived = false,
        androidContactLookupInfo = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverride = null,
    )

    @Test
    fun `display name contains first and last name`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "First",
            lastName = "Last",
            nickname = "Nickname",
        )

        val displayName = contactModelData.getDisplayName()
        assertEquals("First Last", displayName)
    }

    @Test
    fun `display name contains only first name`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "First",
            lastName = "",
            nickname = "Nickname",
        )

        val displayName = contactModelData.getDisplayName()
        assertEquals("First", displayName)
    }

    @Test
    fun `display name contains only last name`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "",
            lastName = "Last",
            nickname = "Nickname",
        )

        val displayName = contactModelData.getDisplayName()
        assertEquals("Last", displayName)
    }

    @Test
    fun `display name contains only nickname that is equal to the identity`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "",
            lastName = "",
            nickname = "TESTTEST",
        )

        val displayName = contactModelData.getDisplayName()
        assertEquals("TESTTEST", displayName)
    }

    @Test
    fun `display name contains only nickname`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "",
            lastName = "",
            nickname = "Nickname",
        )

        val displayName = contactModelData.getDisplayName()
        assertEquals("~Nickname", displayName)
    }

    @Test
    fun `short name contains only first name`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "First",
            lastName = "Last",
            nickname = "Nickname",
        )

        val displayName = contactModelData.getShortName()
        assertEquals("First", displayName)
    }

    @Test
    fun `short name contains only last name`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "",
            lastName = "Last",
            nickname = "Nickname",
        )

        val displayName = contactModelData.getShortName()
        assertEquals("Last", displayName)
    }

    @Test
    fun `short name contains only nickname that is equal to the identity`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "",
            lastName = "",
            nickname = "TESTTEST",
        )

        val displayName = contactModelData.getShortName()
        assertEquals("TESTTEST", displayName)
    }

    @Test
    fun `short name contains only nickname`() {
        val contactModelData = exampleContactModelData.copy(
            firstName = "",
            lastName = "",
            nickname = "Nickname",
        )

        val displayName = contactModelData.getShortName()
        assertEquals("~Nickname", displayName)
    }

    @Test
    fun `contact is linked to android if lookup key is not null`() {
        val linkedContactModelData = exampleContactModelData.copy(androidContactLookupInfo = AndroidContactLookupInfo("lookupKey", 42))
        assertTrue { linkedContactModelData.isLinkedToAndroidContact() }

        val notLinkedContactModelData = exampleContactModelData.copy(androidContactLookupInfo = null)
        assertFalse { notLinkedContactModelData.isLinkedToAndroidContact() }
    }
}
