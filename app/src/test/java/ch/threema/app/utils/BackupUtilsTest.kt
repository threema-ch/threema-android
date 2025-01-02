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

package ch.threema.app.utils

import org.junit.Test
import kotlin.test.assertEquals

class BackupUtilsTest {
    @Test
    fun testCalcRemainingNoncesProgress() {
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 50, 0))
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 50, 50))
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 50, 1000))
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 50, 1100))
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 50, 2100))

        assertEquals(1, BackupUtils.calcRemainingNoncesProgress(1000, 50, 1))
        assertEquals(49, BackupUtils.calcRemainingNoncesProgress(1000, 50, 49))
        assertEquals(1, BackupUtils.calcRemainingNoncesProgress(1000, 50, 1001))
        assertEquals(23, BackupUtils.calcRemainingNoncesProgress(1000, 50, 11723))

        // when noncesPerChunk is not a multiple of noncesPerStep
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 30, 0))
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 30, 30))
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 30, 210))
        assertEquals(0, BackupUtils.calcRemainingNoncesProgress(1000, 30, 990))

        assertEquals(1, BackupUtils.calcRemainingNoncesProgress(1000, 30, 1))
        assertEquals(1, BackupUtils.calcRemainingNoncesProgress(1000, 30, 211))
        assertEquals(8, BackupUtils.calcRemainingNoncesProgress(1000, 30, 728))
        assertEquals(3, BackupUtils.calcRemainingNoncesProgress(1000, 30, 993))

        assertEquals(10, BackupUtils.calcRemainingNoncesProgress(1000, 30, 1000))
        assertEquals(35, BackupUtils.calcRemainingNoncesProgress(1000, 30, 1025))
        assertEquals(37, BackupUtils.calcRemainingNoncesProgress(1000, 30, 3157))
    }
}
