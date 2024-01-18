/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import com.huawei.agconnect.AGConnectOptionsBuilder;
import com.huawei.hms.aaid.HmsInstanceId;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import ch.threema.app.utils.PushUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class PushRegistrationWorker extends Worker {
	private final Logger logger = LoggingUtil.getThreemaLogger("PushRegistrationWorker");

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

		String error = null;
		if (clearToken) {
			try {

				String appId = getAppId(appContext);

				// Delete the token.
				HmsInstanceId.getInstance(appContext).deleteToken(appId, TOKEN_SCOPE);
				PushUtil.sendTokenToServer(appContext,"", ProtocolDefines.PUSHTOKEN_TYPE_NONE);
				logger.info("HMS token successfully deleted");
			} catch (Exception e) {
				logger.error("Exception", e);
				error = e.getMessage();
			}
		}
        else {
			try {
				String appId = getAppId(appContext);

				// Note that this will only work in release builds as the app signature is tested by huawei
				String token = HmsInstanceId.getInstance(appContext).getToken(appId, TOKEN_SCOPE);
				logger.info("Received HMS registration token");
				PushUtil.sendTokenToServer(appContext, appId + '|' +token, ProtocolDefines.PUSHTOKEN_TYPE_HMS);
			} catch (Exception e) {
				logger.error("Exception", e);
				error = e.getMessage();
			}

		}

		if (withCallback) {
			PushUtil.signalRegistrationFinished(error, clearToken);
		}

		// required by the Worker interface but is not used for any error handling in the push registration process
		return Result.success();
	}

	/**
	 * Obtain the app ID from the agconnect-service.json file.
	 */
	private String getAppId(Context context) {
		return new AGConnectOptionsBuilder().build(context).getString(APP_ID_CONFIG_FIELD);
	}

}
