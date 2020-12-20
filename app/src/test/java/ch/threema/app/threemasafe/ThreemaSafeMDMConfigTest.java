/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2020 Threema GmbH
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

package ch.threema.app.threemasafe;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import ch.threema.app.utils.AppRestrictionUtil;

import static junit.framework.TestCase.assertTrue;
import static org.powermock.api.mockito.PowerMockito.doCallRealMethod;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ThreemaSafeMDMConfig.class, AppRestrictionUtil.class})
public class ThreemaSafeMDMConfigTest {

	@Test
	public void isBackupDisabled() {
/*		ThreemaSafeMDMConfig instance = PowerMockito.mock(ThreemaSafeMDMConfig.class);
		Whitebox.setInternalState(ThreemaSafeMDMConfig.class, "sInstance", instance);
*/
		PowerMockito.mockStatic(AppRestrictionUtil.class);
		when(AppRestrictionUtil.getBooleanRestriction("th_safe_enable")).thenReturn(true);

		ThreemaSafeMDMConfig mdmConfig = PowerMockito.mock(ThreemaSafeMDMConfig.class);
		doCallRealMethod().when(mdmConfig).isBackupDisabled();
		assertTrue(mdmConfig.isBackupDisabled());
//		verify(instance, times(1)).getBooleanRestriction("th_safe_enable");
	}
}
