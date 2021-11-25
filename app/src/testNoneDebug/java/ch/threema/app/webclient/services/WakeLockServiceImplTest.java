/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.webclient.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import ch.threema.app.services.LifetimeService;
import ch.threema.app.utils.LogUtil;
import ch.threema.storage.models.WebClientSessionModel;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({LogUtil.class, Build.class})
public class WakeLockServiceImplTest {
	private Context mockContext;
	private PowerManager mockPowerManager;
	private LifetimeService mockLifetimeService;

	private WakeLockServiceImpl service;

	@Before
	public void setUp() {
		Whitebox.setInternalState(Build.class, "MANUFACTURER", "Google");

		// mock context
		this.mockContext = PowerMockito.mock(Context.class);
		this.mockPowerManager = PowerMockito.mock(PowerManager.class);
		this.mockLifetimeService = PowerMockito.mock(LifetimeService.class);

		when(this.mockContext.getSystemService(Context.POWER_SERVICE))
				.thenReturn(mockPowerManager);

		// mock LogUtil
		PowerMockito.mockStatic(LogUtil.class);

		// instantiate with context mock
		this.service = new WakeLockServiceImpl(this.mockContext, this.mockLifetimeService);
	}

	@SuppressLint("WakelockTimeout")
	@Test
	public void acquire() {
		PowerManager.WakeLock wakeLockMock = PowerMockito.mock(PowerManager.WakeLock.class);

		when(this.mockPowerManager.newWakeLock(anyInt(), anyString()))
				.thenReturn(wakeLockMock);

		when(wakeLockMock.isHeld()).thenReturn(true);

		WebClientSessionModel sessionModel1 = PowerMockito.mock(WebClientSessionModel.class);
		when(sessionModel1.getId()).thenReturn(1);

		WebClientSessionModel sessionModel2 = PowerMockito.mock(WebClientSessionModel.class);
		when(sessionModel2.getId()).thenReturn(2);

		when(wakeLockMock.isHeld()).thenReturn(false);
		Assert.assertTrue(this.service.acquire(sessionModel1));

		when(wakeLockMock.isHeld()).thenReturn(true);
		Assert.assertTrue(this.service.acquire(sessionModel1));
		Assert.assertTrue(this.service.acquire(sessionModel2));

		// verify method calls
		verify(this.mockPowerManager, times(1))
				.newWakeLock(anyInt(), anyString());

		verify(wakeLockMock, times(1)).acquire();
	}

	@Test
	public void release() throws Exception {
		Assert.assertTrue(true);
	}

	@Test
	public void releaseAll() throws Exception {
		Assert.assertTrue(true);
	}

}
