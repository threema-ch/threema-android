/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

package ch.threema.app.voip.receivers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import androidx.core.content.ContextCompat;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.base.ThreemaException;

/**
 * Attempt to reject regular phone call if a Threema Call is running
 */
public class IncomingMobileCallReceiver extends BroadcastReceiver {
	private static final Logger logger = LoggerFactory.getLogger("IncomingMobileCallReceiver");

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!intent.getStringExtra(TelephonyManager.EXTRA_STATE).equals(TelephonyManager.EXTRA_STATE_RINGING)) {
			return;
		}

		logger.info("Incoming mobile call");

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Could not acquire service manager");
			return;
		}

		VoipStateService voipStateService;
		try {
			voipStateService = serviceManager.getVoipStateService();
		} catch (ThreemaException e) {
			logger.error("Could not acquire VoipStateService");
			return;
		}

		if (!voipStateService.getCallState().isIdle()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
					TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
					if (telecomManager != null) {
						logger.info("Trying to end call via TelecomManager");
						telecomManager.endCall();
						logger.info("Mobile call rejected");
					}
				}
			} else {
				if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
					TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
					// Hacky, hacky
					try {
						logger.info("Trying to end call via TelephonyManager");
						@SuppressLint("PrivateApi") Method getTelephony = telephonyManager.getClass().getDeclaredMethod("getITelephony");
						getTelephony.setAccessible(true);
						Object telephonyService = getTelephony.invoke(telephonyManager);
						Method silenceRinger = telephonyService.getClass().getDeclaredMethod("silenceRinger");
						silenceRinger.invoke(telephonyService);
						Method endCall = telephonyService.getClass().getDeclaredMethod("endCall");
						endCall.invoke(telephonyService);
						logger.info("Mobile call rejected");
					} catch (Exception e) {
						logger.error("Exception", e);
					}
				}
			}
		}
	}
}
