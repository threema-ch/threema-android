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

package ch.threema.app.push;

import android.content.Context;

import com.google.firebase.iid.FirebaseInstanceId;

import org.slf4j.Logger;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import ch.threema.app.utils.PushUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class PushRegistrationWorker extends Worker {
	private final Logger logger = LoggingUtil.getThreemaLogger("PushRegistrationWorker");

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
		final boolean clearToken = workerFlags.getBoolean(PushService.EXTRA_CLEAR_TOKEN, false);
		final boolean withCallback = workerFlags.getBoolean(PushService.EXTRA_WITH_CALLBACK, false);
		logger.debug("doWork FCM registration clear {} withCallback {}", clearToken, withCallback);

		if (clearToken) {
			String error = null;
			try {
				FirebaseInstanceId.getInstance().deleteInstanceId();
				PushUtil.sendTokenToServer(appContext, "", ProtocolDefines.PUSHTOKEN_TYPE_NONE);
			} catch (IOException | ThreemaException e) {
				logger.error("Exception", e);
				error = e.getMessage();
			}

			if (withCallback) {
				PushUtil.signalRegistrationFinished(error, true);
			}
		} else {
			FirebaseInstanceId.getInstance().getInstanceId()
				.addOnSuccessListener(instanceIdResult -> {
					String token = instanceIdResult.getToken();
					logger.info("Received FCM registration token");
					String error = null;
					try {
						PushUtil.sendTokenToServer(appContext, token, ProtocolDefines.PUSHTOKEN_TYPE_GCM);
					} catch (ThreemaException e) {
						logger.error("Exception", e);
						error = e.getMessage();
					}
					if (withCallback) {
						PushUtil.signalRegistrationFinished(error, clearToken);
					}
				}).addOnFailureListener(e -> {
				if (withCallback) {
					PushUtil.signalRegistrationFinished(e.getMessage(), clearToken);
				}
			});
		}
		// required by the Worker interface but is not used for any error handling in the push registration process
		return Result.success();
	}
}
