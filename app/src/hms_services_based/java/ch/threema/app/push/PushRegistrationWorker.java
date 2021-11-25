/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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

package ch.threema.app.push;

import android.content.Context;

import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import ch.threema.app.utils.PushUtil;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class PushRegistrationWorker extends Worker {
	private final Logger logger = LoggerFactory.getLogger(PushRegistrationWorker.class);

	public static String TOKEN_SCOPE = "HCM";
	public static String APP_ID_CONFIG_FIELD = "client/app_id";

	private final Context appContext;

	/**
	 * Constructor for the PushRegistrationWorker.
	 *
	 * Note: This constructor is called by the WorkManager, so don't add additional parameters!
	 */
	public PushRegistrationWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
		super(appContext, workerParams);
		this.appContext = appContext;
	}

	@NonNull
	@Override
	public Result doWork() {
		Data workerFlags = getInputData();
		final boolean clearToken = workerFlags.getBoolean(PushUtil.EXTRA_CLEAR_TOKEN, false);
		final boolean withCallback = workerFlags.getBoolean(PushUtil.EXTRA_WITH_CALLBACK, false);
		logger.debug("doWork HMS token registration clear {} withCallback {}", clearToken, withCallback);

		if (clearToken) {
			String error = null;
			try {
				// Obtain the app ID from the agconnect-service.json file.
				String appId = AGConnectServicesConfig.fromContext(appContext).getString(APP_ID_CONFIG_FIELD);

				// Delete the token.
				HmsInstanceId.getInstance(appContext).deleteToken(appId, TOKEN_SCOPE);
				PushUtil.sendTokenToServer(appContext,"", ProtocolDefines.PUSHTOKEN_TYPE_NONE);
				logger.info("HMS token successfully deleted");
			} catch (ApiException | ThreemaException e) {
				logger.error("Exception", e);
				error = e.getMessage();
			}

			if (withCallback) {
				PushUtil.signalRegistrationFinished(error, clearToken);
			}
		}
        else {
			String appId = AGConnectServicesConfig.fromContext(appContext).getString(APP_ID_CONFIG_FIELD);
			String error = null;
			try {
				String token = HmsInstanceId.getInstance(appContext).getToken(appId, TOKEN_SCOPE);
				logger.info("Received HMS registration token");
				PushUtil.sendTokenToServer(appContext, appId + '|' +token, ProtocolDefines.PUSHTOKEN_TYPE_HMS);
			} catch (ThreemaException | ApiException e) {
				logger.error("Exception", e);
				error = e.getMessage();
			}
			if (withCallback) {
				PushUtil.signalRegistrationFinished(error, clearToken);
			}
		}
		// required by the Worker interface but is not used for any error handling in the push registration process
		return Result.success();
	}

}
