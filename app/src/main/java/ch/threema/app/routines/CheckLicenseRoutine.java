/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.routines;

import android.content.Context;
import android.content.ReceiverCallNotAllowedException;

import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ch.threema.app.BuildFlavor;
import ch.threema.app.ThreemaLicensePolicy;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceThreema;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.client.APIConnector;
import ch.threema.client.IdentityStoreInterface;

/**
 * Checking the License of current Threema and send a not allowed broadcast
 */
public class CheckLicenseRoutine implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(CheckLicenseRoutine.class);

	private final Context context;
	private final APIConnector apiConnector;
	private final UserService userService;
	private final DeviceService deviceService;
	private final LicenseService licenseService;
	private final IdentityStoreInterface identityStore;
	private static final String LICENSE_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqJArbOQT3Vi2KUEbyk+xq+DSsowwIYoudh3miXC7DmR6SVL6ji7XG8C+hmtR6t+Ytar64z87xgTPiEPiuyyg6/fp8ALRLAjM2FmZadSS4hSpvmJKb2ViFyUmcCJ8MoZ2QPxA+SVGZFdwIwwXdHPx2xUQw6ftyx0EF0hvF4nwHLvq89p03QtiPnIb0A3MOEXsq88xu2xAUge/BTvRWo0gWTtIJhTdZXY2CSib5d/G45xca0DKgOECAaMxVbFhE5jSyS+qZvUN4tABgDKBiEPuuzBBaHVt/m7MQoqoM6kcNrozACmIx6UdwWbkK3Isa9Xo9g3Yy6oc9Mp/9iKXwco4vwIDAQAB";

	public CheckLicenseRoutine(Context context,
	                           APIConnector apiConnector,
	                           UserService userService,
	                           DeviceService deviceService,
	                           LicenseService licenseService,
	                           IdentityStoreInterface identityStore) {
		this.context = context;
		this.apiConnector = apiConnector;
		this.userService = userService;
		this.deviceService = deviceService;
		this.licenseService = licenseService;
		this.identityStore = identityStore;
	}

	private void invalidLicense(String message) {
		try {
			LocalBroadcastManager.getInstance(this.context).sendBroadcast(IntentDataUtil.createActionIntentLicenseNotAllowed(message));
		}
		catch (ReceiverCallNotAllowedException x) {
			logger.error("Exception", x);
		}
	}
	@Override
	public void run() {
		if(this.deviceService.isOnline()) {
			switch(BuildFlavor.getLicenseType()) {
				case GOOGLE:
					this.checkLVL();
					break;
				case SERIAL:
				case GOOGLE_WORK:
					this.checkSerial();
					break;
			}
		}
	}

	private void checkSerial() {
		logger.debug("check serial");

		String error = licenseService.validate(true);
		if(error != null) {
			invalidLicense(error);
		} else {

			userService.setCredentials(licenseService.loadCredentials());

			if(licenseService instanceof LicenseServiceThreema) {
				LicenseServiceThreema licenseServiceThreema = (LicenseServiceThreema)licenseService;
				if (licenseServiceThreema.getUpdateMessage() != null && !licenseServiceThreema.isUpdateMessageShown()) {
					try {
						LocalBroadcastManager.getInstance(this.context).sendBroadcast(
								IntentDataUtil.createActionIntentUpdateAvailable(
										licenseServiceThreema.getUpdateMessage(),
										licenseServiceThreema.getUpdateUrl()
								)
						);
						licenseServiceThreema.setUpdateMessageShown(true);
					} catch (ReceiverCallNotAllowedException x) {
						logger.error("Exception", x);
					}
				}
			}

			//run update work info route on the work build
			if(ConfigUtils.isWorkBuild()) {
				(new UpdateWorkInfoRoutine(
						this.context,
						this.apiConnector,
						this.identityStore,
						this.deviceService,
						this.licenseService
				)).run();
			}
		}
	}

	private void checkLVL() {
		logger.debug("check lvl");
		if(this.deviceService.isOnline()) {
			final ThreemaLicensePolicy policy = new ThreemaLicensePolicy();
			LicenseCheckerCallback callback = new LicenseCheckerCallback() {
				@Override
				public void allow(int reason) {
					logger.debug("License OK");
					userService.setPolicyResponse(
							policy.getLastResponseData().responseData,
							policy.getLastResponseData().signature
					);
				}

				@Override
				public void dontAllow(int reason) {
					logger.debug("not allowed (code " + reason + ")");
					//if (reason == ThreemaLicensePolicy.NOT_LICENSED) {
					//	invalidLicense("Not licensed (code " + reason + ")");
					//}
				}

				@Override
				public void applicationError(int errorCode) {
					logger.debug("License check failed (code " + errorCode + ")");
					//invalidLicense("License check failed (code " + errorCode + ")");
				}
			};
			LicenseChecker licenseChecker = new LicenseChecker(this.context, policy, LICENSE_PUBLIC_KEY);
			try {
				licenseChecker.checkAccess(callback);
			}
			catch (ReceiverCallNotAllowedException x) {
				logger.error("LVL: Receiver call not allowed", x);
			}
		}
	}
}
