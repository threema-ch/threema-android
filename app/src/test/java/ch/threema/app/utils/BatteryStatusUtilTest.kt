/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import android.content.Intent
import android.os.BatteryManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatteryStatusUtilTest {

    @Test
    fun isChargingBadIntent() {
        val intent = mockkIntent(batteryStatus = -1)
        assertNull(BatteryStatusUtil.isCharging(intent))
    }

    @Test
    fun isChargingYesCharging() {
        val intent = mockkIntent(batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING)
        assertEquals(true, BatteryStatusUtil.isCharging(intent))
    }

    @Test
    fun isChargingYesFull() {
        val intent = mockkIntent(batteryStatus = BatteryManager.BATTERY_STATUS_FULL)
        assertEquals(true, BatteryStatusUtil.isCharging(intent))
    }

    @Test
    fun isChargingNo() {
        val intent = mockkIntent(batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING)
        assertEquals(false, BatteryStatusUtil.isCharging(intent))
    }

    @Test
    fun percentNull() {
        val intent = mockkIntent(batteryLevel = 60, batteryScale = -1)
        assertNull(BatteryStatusUtil.getPercent(intent))
    }

    @Test
    fun percentZero() {
        val intent = mockkIntent(batteryLevel = 0, batteryScale = 200)
        assertEquals(0, BatteryStatusUtil.getPercent(intent))
    }

    @Test
    fun percentFifty() {
        val intent = mockkIntent(batteryLevel = 100, batteryScale = 200)
        assertEquals(50, BatteryStatusUtil.getPercent(intent))
    }

    private fun mockkIntent(batteryStatus: Int = -1, batteryLevel: Int = -1, batteryScale: Int = -1) =
        mockk<Intent> {
            every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns batteryStatus
            every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns batteryLevel
            every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns batteryScale
        }
}
