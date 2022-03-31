/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Intent;
import android.os.BatteryManager;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class BatteryStatusUtilTest {

	@Mock
	private Intent intent;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		try {
			PowerMockito.whenNew(Intent.class)
					.withArguments(String.class).thenReturn(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void isChargingBadIntent() {
		when(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
				.thenReturn(-1);
		Assert.assertNull(BatteryStatusUtil.isCharging(intent));
	}

	@Test
	public void isChargingYesCharging() {
		when(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
				.thenReturn(BatteryManager.BATTERY_STATUS_CHARGING);
		Assert.assertEquals(BatteryStatusUtil.isCharging(intent), Boolean.TRUE);
	}

	@Test
	public void isChargingYesFull() {
		when(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
				.thenReturn(BatteryManager.BATTERY_STATUS_FULL);
		Assert.assertEquals(BatteryStatusUtil.isCharging(intent), Boolean.TRUE);
	}

	@Test
	public void isChargingNo() {
		when(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
				.thenReturn(BatteryManager.BATTERY_STATUS_DISCHARGING);
		Assert.assertEquals(BatteryStatusUtil.isCharging(intent), Boolean.FALSE);
	}

	@Test
	public void getPercentNull() {
		when(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1))
				.thenReturn(60);
		when(intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
				.thenReturn(-1);
		Assert.assertNull(BatteryStatusUtil.getPercent(intent));
	}

	@Test
	public void getPercentZero() {
		when(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1))
				.thenReturn(0);
		when(intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
				.thenReturn(200);
		Assert.assertEquals(BatteryStatusUtil.getPercent(intent), Integer.valueOf(0));
	}

	@Test
	public void getPercentFifty() {
		when(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1))
				.thenReturn(100);
		when(intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
				.thenReturn(200);
		Assert.assertEquals(BatteryStatusUtil.getPercent(intent), Integer.valueOf(50));
	}

}
