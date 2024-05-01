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

import com.huawei.hms.aaid.HmsInstanceId;

import org.slf4j.Logger;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import ch.threema.app.utils.PushUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class PushRegistrationWorker extends Worker {
	private final Logger logger = LoggingUtil.getThreemaLogger("PushRegistrationWorker");

	private final Context appContext;

	/**
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

		try {
			String appId = Objects.requireNonNull(HmsTokenUtil.getHmsAppId(appContext));
			if (clearToken) {
				// Delete the token
				HmsInstanceId.getInstance(appContext).deleteToken(appId, HmsTokenUtil.TOKEN_SCOPE);
				PushUtil.sendTokenToServer("", ProtocolDefines.PUSHTOKEN_TYPE_NONE);
				logger.info("HMS token successfully deleted");
			} else {
				// Note that this will only work in release builds as the app signature is tested by huawei
				String token = HmsInstanceId.getInstance(appContext).getToken(appId, HmsTokenUtil.TOKEN_SCOPE);
				String formattedToken = Objects.requireNonNull(HmsTokenUtil.prependHmsAppId(appId, token));
				logger.info("Received HMS registration token");
				PushUtil.sendTokenToServer(formattedToken, ProtocolDefines.PUSHTOKEN_TYPE_HMS);
			}
		} catch (Exception e) {
			logger.error("Exception", e);
			error = e.getMessage();
		}

		if (withCallback) {
			PushUtil.signalRegistrationFinished(error, clearToken);
		}

		// required by the Worker interface but is not used for any error handling in the push registration process
		return Result.success();
	}
}
