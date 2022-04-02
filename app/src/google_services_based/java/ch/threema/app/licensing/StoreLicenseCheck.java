/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.app.licensing;

import android.content.Context;
import android.content.ReceiverCallNotAllowedException;

import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;

import org.slf4j.Logger;

import ch.threema.app.ThreemaLicensePolicy;
import ch.threema.app.routines.CheckLicenseRoutine;
import ch.threema.app.services.UserService;
import ch.threema.base.utils.LoggingUtil;

public class StoreLicenseCheck implements CheckLicenseRoutine.StoreLicenseChecker {
	private static final Logger logger = LoggingUtil.getThreemaLogger("StoreLicenseCheck");

	private static final String LICENSE_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqJArbOQT3Vi2KUEbyk+xq+DSsowwIYoudh3miXC7DmR6SVL6ji7XG8C+hmtR6t+Ytar64z87xgTPiEPiuyyg6/fp8ALRLAjM2FmZadSS4hSpvmJKb2ViFyUmcCJ8MoZ2QPxA+SVGZFdwIwwXdHPx2xUQw6ftyx0EF0hvF4nwHLvq89p03QtiPnIb0A3MOEXsq88xu2xAUge/BTvRWo0gWTtIJhTdZXY2CSib5d/G45xca0DKgOECAaMxVbFhE5jSyS+qZvUN4tABgDKBiEPuuzBBaHVt/m7MQoqoM6kcNrozACmIx6UdwWbkK3Isa9Xo9g3Yy6oc9Mp/9iKXwco4vwIDAQAB";

	private StoreLicenseCheck() {}

	public static void checkLicense(Context context, UserService userService) {
		logger.debug("Checking LVL licence");
		final ThreemaLicensePolicy policy = new ThreemaLicensePolicy();
		LicenseCheckerCallback callback = new LicenseCheckerCallback() {
			@Override
			public void allow(int reason) {
				logger.debug("LVL License OK");
				userService.setPolicyResponse(
					policy.getLastResponseData().responseData,
					policy.getLastResponseData().signature,
					0
				);
			}

			@Override
			public void dontAllow(int reason) {
				// 561 == not licensed
				// 291 == no connection
				logger.debug("LVL License not allowed (code {})", reason);
				userService.setPolicyResponse(
					null,
					null,
					reason
				);
			}

			@Override
			public void applicationError(int errorCode) {
				logger.debug("LVL License check failed errorCode: {}", errorCode);
				userService.setPolicyResponse(
					null,
					null,
					errorCode
				);
			}
		};
		LicenseChecker licenseChecker = new LicenseChecker(context, policy, LICENSE_PUBLIC_KEY);
		try {
			licenseChecker.checkAccess(callback);
		}
		catch (ReceiverCallNotAllowedException x) {
			logger.error("LVL: Receiver call not allowed", x);
		}
	}
}
