/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.services

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestNonceStore
import ch.threema.app.TestTaskManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.helpers.ServerAckTaskCodec
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockedIdentitiesServiceTest {

    private val multiDeviceManager = TestMultiDeviceManager(
        isMultiDeviceActive = false
    )

    private val taskManager = TestTaskManager(ServerAckTaskCodec())

    private val preferenceService = PreferenceServiceImpl(
        ThreemaApplication.getAppContext(),
        PreferenceStore(
            ThreemaApplication.getAppContext(),
            ThreemaApplication.getMasterKey()
        ),
        taskManager,
        multiDeviceManager,
        NonceFactory(TestNonceStore()),
    )

    private val blockedIdentitiesService: BlockedIdentitiesService = BlockedIdentitiesServiceImpl(
        preferenceService,
        multiDeviceManager,
        ThreemaApplication.requireServiceManager().taskCreator,
    )

    private val onModified = ArrayDeque<String>()

    init {
        ListenerManager.contactListeners.add(object : ContactListener {
            override fun onModified(identity: String) {
                onModified.addLast(identity)
            }
        })
    }

    @BeforeTest
    fun initListener() {
        blockedIdentitiesService.persistBlockedIdentities(emptySet())
        onModified.clear()
        // Assert that initially no identities are blocked
        assertTrue { blockedIdentitiesService.getAllBlockedIdentities().isEmpty() }
    }

    @Test
    fun testBlockIdentity() {
        blockedIdentitiesService.blockIdentity("ABCDEFGH")
        blockedIdentitiesService.blockIdentity("TESTTEST")

        assertTrue { blockedIdentitiesService.isBlocked("ABCDEFGH") }
        assertTrue { blockedIdentitiesService.isBlocked("TESTTEST") }

        assertTrue { onModified.removeFirst() == "ABCDEFGH" }
        assertTrue { onModified.removeFirst() == "TESTTEST" }
    }

    @Test
    fun testUnblockIdentity() {
        blockedIdentitiesService.blockIdentity("ABCDEFGH")
        blockedIdentitiesService.blockIdentity("TESTTEST")
        blockedIdentitiesService.unblockIdentity("ABCDEFGH")

        assertTrue { !blockedIdentitiesService.isBlocked("ABCDEFGH") }
        assertTrue { blockedIdentitiesService.isBlocked("TESTTEST") }

        assertTrue { onModified.removeFirst() == "ABCDEFGH" }
        assertTrue { onModified.removeFirst() == "TESTTEST" }
        assertTrue { onModified.removeFirst() == "ABCDEFGH" }
    }

    @Test
    fun testPersistIdentities() {
        blockedIdentitiesService.persistBlockedIdentities(setOf("ABCDEFGH", "12345678"))

        assertEquals(
            setOf("ABCDEFGH", "12345678"),
            setOf(onModified.removeFirst(), onModified.removeFirst())
        )

        blockedIdentitiesService.persistBlockedIdentities(setOf("ABCDEFGH", "TESTTEST"))

        assertEquals(
            setOf("12345678", "TESTTEST"),
            setOf(onModified.removeFirst(), onModified.removeFirst())
        )
        assertTrue { onModified.isEmpty() }
    }

    @Test
    fun testToggle() {
        blockedIdentitiesService.toggleBlocked("12345678")

        assertTrue { blockedIdentitiesService.isBlocked("12345678") }
        assertEquals("12345678", onModified.removeFirst())

        blockedIdentitiesService.toggleBlocked("12345678")

        assertFalse { blockedIdentitiesService.isBlocked("12345678") }
        assertEquals("12345678", onModified.removeFirst())

        assertTrue { onModified.isEmpty() }
    }

}
