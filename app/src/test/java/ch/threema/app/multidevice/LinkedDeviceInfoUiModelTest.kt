/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.multidevice

import ch.threema.domain.protocol.connection.data.D2dMessage.DeviceInfo.Platform
import ch.threema.domain.protocol.connection.data.DeviceId
import kotlin.test.Test
import kotlin.test.assertContentEquals

class LinkedDeviceInfoUiModelTest {
    @Test
    fun shouldSortCorrectByConnectedSince() {
        // arrange
        val modelsUnsorted = listOf(
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 100L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 150L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 300L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 100L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
        )

        // act
        val modelSorted = modelsUnsorted.sortedDescending()

        // assert
        assertContentEquals(
            listOf(
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 300L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 150L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 100L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 100L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
            ),
            modelSorted,
        )
    }

    @Test
    fun shouldSortCorrectByLastDisconnected() {
        // arrange
        val modelsUnsorted = listOf(
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 50L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 100L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 150L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 30L,
            ),
        )

        // act
        val modelSorted = modelsUnsorted.sortedDescending()

        // assert
        assertContentEquals(
            listOf(
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 150L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 100L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 50L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 30L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
            ),
            modelSorted,
        )
    }

    @Test
    fun shouldSortCorrectMixed() {
        // arrange
        val modelsUnsorted = listOf(
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 100L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 150L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 300L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 100L,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 50L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = null,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 100L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 150L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = null,
                lastDisconnectAt = 30L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 200L,
                lastDisconnectAt = 200L,
            ),
            LinkedDeviceInfoUiModel(
                DeviceId((0L).toULong()),
                "",
                Platform.ANDROID,
                "",
                "",
                connectedSince = 30L,
                lastDisconnectAt = 250L,
            ),
        )

        // act
        val modelSorted = modelsUnsorted.sortedDescending()

        // assert
        assertContentEquals(
            listOf(
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 300L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 200L,
                    lastDisconnectAt = 200L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 150L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 100L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 100L,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = 30L,
                    lastDisconnectAt = 250L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 150L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 100L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 50L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = 30L,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
                LinkedDeviceInfoUiModel(
                    DeviceId((0L).toULong()),
                    "",
                    Platform.ANDROID,
                    "",
                    "",
                    connectedSince = null,
                    lastDisconnectAt = null,
                ),
            ),
            modelSorted,
        )
    }
}
