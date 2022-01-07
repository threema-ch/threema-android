/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

import android.app.Activity;
import android.content.Context;
import android.content.ReceiverCallNotAllowedException;

import com.DrmSDK.Drm;
import com.DrmSDK.DrmCheckCallback;
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
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.stores.IdentityStoreInterface;

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

	private static final String HMS_ID = "5190041000024384032";
	private static final String HMS_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA26ccdC7mLHomHTnKvSRGg7Vuex19xD3qv8CEOUj5lcT5Z81ARby5CVhM/ZM9zKCQcrKmenn1aih6X+uZoNsvBziDUySkrzXPTX/NfoFDQlHgyXan/xsoIPlE1v0D9dLV7fgPOllHxmN8wiwF+woACo3ao/ra2VY38PCZTmfMX/V+hOLHsdRakgWVshzeYTtzMjlLrnYOp5AFXEjFhF0dB92ozAmLzjFJtwyMdpbVD+yRVr+fnLJ6ADhBpoKLjvpn8A7PhpT5wsvogovdr16u/uKhPy5an4DXE0bjWc76bE2SEse/bQTvPoGRw5TjHVWi7uDMFSz3OOGUqLSygucPdwIDAQAB";

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
		switch(BuildFlavor.getLicenseType()) {
			case GOOGLE:
				this.checkLVL();
				break;
			case SERIAL:
			case GOOGLE_WORK:
			case HMS_WORK:
			case ONPREM:
				this.checkSerial();
				break;
			case HMS:
				this.checkDRM();
				break;
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

	private void checkDRM() {
		logger.debug("Check HMS license");

		if (this.deviceService.isOnline() && !userService.hasIdentity()) {
			DrmCheckCallback callback = new DrmCheckCallback() {
				@Override
				public void onCheckSuccess(String signData, String signature) {
					logger.info("HMS License OK");
					userService.setPolicyResponse(
						signData,
						signature,
						0
					);
				}

				@Override
				public void onCheckFailed(int errorCode) {
					logger.debug("HMS License failed errorCode: {}", errorCode);
					userService.setPolicyResponse(
						null,
						null,
						errorCode
					);
				}
			};
			Drm.check((Activity) context, context.getPackageName(), HMS_ID, HMS_PUBLIC_KEY, callback);
		}
	}

	private void checkLVL() {
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
		LicenseChecker licenseChecker = new LicenseChecker(this.context, policy, LICENSE_PUBLIC_KEY);
		try {
			licenseChecker.checkAccess(callback);
		}
		catch (ReceiverCallNotAllowedException x) {
			logger.error("LVL: Receiver call not allowed", x);
		}
	}
}
