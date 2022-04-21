/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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
import android.text.format.DateUtils;

import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.api.ConnectionResult;
import com.huawei.hms.api.HuaweiMobileServicesUtil;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

import org.slf4j.Logger;

import java.util.Date;
import java.util.Map;

import androidx.annotation.NonNull;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

import static ch.threema.app.push.PushRegistrationWorker.APP_ID_CONFIG_FIELD;
import static ch.threema.app.push.PushRegistrationWorker.TOKEN_SCOPE;

public class PushService extends HmsMessageService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("PushService");

	@Override
	public void onNewToken(@NonNull String token) {
		logger.info("New HMS token received");
		try {
			PushUtil.sendTokenToServer(this, token, ProtocolDefines.PUSHTOKEN_TYPE_HMS);
		} catch (ThreemaException e) {
			logger.error("onNewToken, could not send token to server ", e);
		}
	}

	public static void deleteToken(Context context) {
		String appId = AGConnectServicesConfig.fromContext(context).getString(APP_ID_CONFIG_FIELD);
		try {
			HmsInstanceId.getInstance(ThreemaApplication.getAppContext()).deleteToken(appId, TOKEN_SCOPE);
			PushUtil.sendTokenToServer(context,"", ProtocolDefines.PUSHTOKEN_TYPE_NONE);
		} catch (ApiException | ThreemaException e) {
			logger.error("Could not delete hms token", e);
		}
	}

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		logger.info("Handling incoming HMS message.");

		RuntimeUtil.runInWakelock(getApplicationContext(), DateUtils.MINUTE_IN_MILLIS * 10, "PushService", () -> processHMSMessage(remoteMessage));
	}

	private void processHMSMessage(RemoteMessage remoteMessage) {
		logger.info("Received HMS message: {}", remoteMessage.getMessageId());
		// Log message sent time
		try {
			Date sentDate = new Date(remoteMessage.getSentTime());
			logger.info("*** Message sent     : " + sentDate.toString(), true);
			logger.info("*** Message received : " + new Date().toString(), true);
			logger.info("*** Original priority: " + remoteMessage.getOriginalUrgency());
			logger.info("*** Current priority: " + remoteMessage.getUrgency());
		} catch (Exception ignore) {
		}

		Map<String, String> data = remoteMessage.getDataOfMap();
		PushUtil.processRemoteMessage(data);
	}

	// following services check are handled here and not in ConfigUtils to minimize number of duplicating classes
	/**
	 * check for specific huawei services
	 */
	public static boolean hmsServicesInstalled(Context context) {
		return RuntimeUtil.isInTest() || (HuaweiMobileServicesUtil.isHuaweiMobileServicesAvailable(context) == ConnectionResult.SUCCESS);
	}

	/**
	 * check for specific google services
	 */
	public static boolean playServicesInstalled(Context context) {
		return false;
	}

	/**
	 * check for available push service
	 */
	public static boolean servicesInstalled(Context context) {
		return playServicesInstalled(context) || hmsServicesInstalled(context);
	}
}
