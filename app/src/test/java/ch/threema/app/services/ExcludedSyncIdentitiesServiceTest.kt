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

package ch.threema.app.services

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred

class ExcludedSyncIdentitiesServiceTest {

    private var storedExcludedSyncIdentities: Array<Identity> = emptyArray()
    private val preferenceServiceMock: PreferenceService = mockk {
        every { setList("identity_list_sync_excluded", any()) } answers {
            storedExcludedSyncIdentities = secondArg()
        }

        every { getList("identity_list_sync_excluded") } answers { storedExcludedSyncIdentities }
    }
    private val activeMultiDeviceManagerMock: MultiDeviceManager = mockk {
        every { isMultiDeviceActive } returns true
    }
    private val inactiveMultiDeviceManagerMock: MultiDeviceManager = mockk {
        every { isMultiDeviceActive } returns false
    }
    private val taskCreatorMock: TaskCreator = mockk {
        every { scheduleReflectExcludeFromSyncIdentitiesTask() } returns CompletableDeferred<Unit>().also { it.complete(Unit) }
    }

    @Test
    fun `test adding identities without multi device`() {
        // Arrange
        val excludedSyncIdentitiesService = ExcludedSyncIdentitiesServiceImpl(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = inactiveMultiDeviceManagerMock,
            taskCreator = taskCreatorMock,
        )

        val identitiesToExclude = listOf("01234567", "TESTTEST")

        // Act
        identitiesToExclude.forEach { identity ->
            excludedSyncIdentitiesService.excludeFromSync(identity, TriggerSource.LOCAL)
        }

        // Assert
        identitiesToExclude.forEach { identity ->
            assertTrue { excludedSyncIdentitiesService.isExcluded(identity) }
            assertContains(excludedSyncIdentitiesService.getExcludedIdentities(), identity)
        }
        verify(exactly = 0) { taskCreatorMock.scheduleReflectExcludeFromSyncIdentitiesTask() }
    }

    @Test
    fun `test adding identities with multi device`() {
        // Arrange
        val excludedSyncIdentitiesService = ExcludedSyncIdentitiesServiceImpl(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = activeMultiDeviceManagerMock,
            taskCreator = taskCreatorMock,
        )

        val identitiesToExclude = listOf("01234567", "TESTTEST")

        // Act
        identitiesToExclude.forEach { identity ->
            excludedSyncIdentitiesService.excludeFromSync(identity, TriggerSource.LOCAL)
        }

        // Assert
        identitiesToExclude.forEach { identity ->
            assertTrue { excludedSyncIdentitiesService.isExcluded(identity) }
            assertContains(excludedSyncIdentitiesService.getExcludedIdentities(), identity)
        }
        verify(exactly = identitiesToExclude.size) { taskCreatorMock.scheduleReflectExcludeFromSyncIdentitiesTask() }
    }

    @Test
    fun `test removing identities without multi device`() {
        // Arrange
        val initiallyExcludedIdentities = listOf("01234567", "TESTTEST")
        storedExcludedSyncIdentities = initiallyExcludedIdentities.toTypedArray()

        val excludedSyncIdentitiesService = ExcludedSyncIdentitiesServiceImpl(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = inactiveMultiDeviceManagerMock,
            taskCreator = taskCreatorMock,
        )

        val identityToRemoveFromExcludedIdentities = initiallyExcludedIdentities.first()

        // Act
        excludedSyncIdentitiesService.removeExcludedIdentity(identityToRemoveFromExcludedIdentities, TriggerSource.LOCAL)

        // Assert
        assertFalse { excludedSyncIdentitiesService.isExcluded(identityToRemoveFromExcludedIdentities) }
        assertFalse { excludedSyncIdentitiesService.getExcludedIdentities().contains(identityToRemoveFromExcludedIdentities) }
        assertEquals(1, excludedSyncIdentitiesService.getExcludedIdentities().size)
        assertContains(excludedSyncIdentitiesService.getExcludedIdentities(), initiallyExcludedIdentities.last())
        verify(exactly = 0) { taskCreatorMock.scheduleReflectExcludeFromSyncIdentitiesTask() }
    }

    @Test
    fun `test removing identities with multi device`() {
        // Arrange
        val initiallyExcludedIdentities = listOf("01234567", "TESTTEST")
        storedExcludedSyncIdentities = initiallyExcludedIdentities.toTypedArray()

        val excludedSyncIdentitiesService = ExcludedSyncIdentitiesServiceImpl(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = activeMultiDeviceManagerMock,
            taskCreator = taskCreatorMock,
        )

        val identityToRemoveFromExcludedIdentities = initiallyExcludedIdentities.first()

        // Act
        excludedSyncIdentitiesService.removeExcludedIdentity(identityToRemoveFromExcludedIdentities, TriggerSource.LOCAL)

        // Assert
        assertFalse { excludedSyncIdentitiesService.isExcluded(identityToRemoveFromExcludedIdentities) }
        assertFalse { excludedSyncIdentitiesService.getExcludedIdentities().contains(identityToRemoveFromExcludedIdentities) }
        assertEquals(1, excludedSyncIdentitiesService.getExcludedIdentities().size)
        assertContains(excludedSyncIdentitiesService.getExcludedIdentities(), initiallyExcludedIdentities.last())
        verify(exactly = 1) { taskCreatorMock.scheduleReflectExcludeFromSyncIdentitiesTask() }
    }

    @Test
    fun `test setting identity set without multi device`() {
        // Arrange
        val excludedSyncIdentitiesService = ExcludedSyncIdentitiesServiceImpl(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = inactiveMultiDeviceManagerMock,
            taskCreator = taskCreatorMock,
        )

        val identitiesToAdd = setOf("01234567", "TESTTEST")

        // Act
        excludedSyncIdentitiesService.setExcludedIdentities(identitiesToAdd, TriggerSource.LOCAL)

        // Assert
        identitiesToAdd.forEach { identity ->
            assertTrue { excludedSyncIdentitiesService.isExcluded(identity) }
        }
        assertEquals(identitiesToAdd.size, excludedSyncIdentitiesService.getExcludedIdentities().size)
        verify(exactly = 0) { taskCreatorMock.scheduleReflectExcludeFromSyncIdentitiesTask() }
    }

    @Test
    fun `test setting identity set with multi device`() {
        // Arrange
        val excludedSyncIdentitiesService = ExcludedSyncIdentitiesServiceImpl(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = activeMultiDeviceManagerMock,
            taskCreator = taskCreatorMock,
        )

        val identitiesToAdd = setOf("01234567", "TESTTEST")

        // Act
        excludedSyncIdentitiesService.setExcludedIdentities(identitiesToAdd, TriggerSource.LOCAL)

        // Assert
        identitiesToAdd.forEach { identity ->
            assertTrue { excludedSyncIdentitiesService.isExcluded(identity) }
        }
        assertEquals(identitiesToAdd.size, excludedSyncIdentitiesService.getExcludedIdentities().size)
        verify(exactly = 1) { taskCreatorMock.scheduleReflectExcludeFromSyncIdentitiesTask() }
    }

    @Test
    fun `test actions from sync do not reflect anything`() {
        // Arrange
        val excludedSyncIdentitiesService = ExcludedSyncIdentitiesServiceImpl(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = activeMultiDeviceManagerMock,
            taskCreator = taskCreatorMock,
        )

        // Act
        with(excludedSyncIdentitiesService) {
            excludeFromSync("01234567", TriggerSource.SYNC)
            removeExcludedIdentity("01234567", TriggerSource.SYNC)
            setExcludedIdentities(setOf("01234567"), TriggerSource.SYNC)
        }

        // Assert
        verify(exactly = 0) { taskCreatorMock.scheduleReflectExcludeFromSyncIdentitiesTask() }
    }
}
