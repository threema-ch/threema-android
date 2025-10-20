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

package ch.threema.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildFlavorTest {
    @Test
    fun `isOnPrem returns correct values`() {
        assertTrue(BuildFlavor.OnPrem.isOnPrem)

        assertFalse(BuildFlavor.None.isOnPrem)
        assertFalse(BuildFlavor.StoreGoogle.isOnPrem)
        assertFalse(BuildFlavor.StoreThreema.isOnPrem)
        assertFalse(BuildFlavor.StoreGoogleWork.isOnPrem)
        assertFalse(BuildFlavor.Green.isOnPrem)
        assertFalse(BuildFlavor.SandboxWork.isOnPrem)
        assertFalse(BuildFlavor.Blue.isOnPrem)
        assertFalse(BuildFlavor.Hms.isOnPrem)
        assertFalse(BuildFlavor.HmsWork.isOnPrem)
        assertFalse(BuildFlavor.Libre.isOnPrem)
    }

    @Test
    fun `isWork returns correct values`() {
        assertTrue(BuildFlavor.OnPrem.isWork)
        assertTrue(BuildFlavor.StoreGoogleWork.isWork)
        assertTrue(BuildFlavor.SandboxWork.isWork)
        assertTrue(BuildFlavor.HmsWork.isWork)
        assertTrue(BuildFlavor.Blue.isWork)

        assertFalse(BuildFlavor.None.isWork)
        assertFalse(BuildFlavor.StoreGoogle.isWork)
        assertFalse(BuildFlavor.StoreThreema.isWork)
        assertFalse(BuildFlavor.Green.isWork)
        assertFalse(BuildFlavor.Hms.isWork)
        assertFalse(BuildFlavor.Libre.isWork)
    }
}
