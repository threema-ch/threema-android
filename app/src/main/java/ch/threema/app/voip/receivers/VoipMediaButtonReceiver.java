/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.voip.CallStateSnapshot;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.voip.util.VoipUtil;

public class VoipMediaButtonReceiver extends BroadcastReceiver {
	private static final Logger logger = LoggerFactory.getLogger(VoipMediaButtonReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {

		VoipStateService stateService;
		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				stateService = serviceManager.getVoipStateService();
			} else {
				return;
			}
		} catch (Exception e) {
			logger.error("Could not initialize services", e);
			return;
		}

		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			KeyEvent mediaButtonEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

			logger.info("MediaButtonReceiver: mediaAction={}, keyCode={}",
				intent.getAction(), mediaButtonEvent.getKeyCode());

			// If this is a valid accept button, handle it
			if (isAcceptButton(mediaButtonEvent.getKeyCode())) {
				// Only consider the up action
				if (mediaButtonEvent.getAction() == KeyEvent.ACTION_UP) {
					final CallStateSnapshot callState = stateService.getCallState();
					if (callState.isRinging()) {
						logger.info("Accepting call via media button");
						stateService.acceptIncomingCall();
					} else if (callState.isCalling()) {
						logger.info("Hanging up call via media button");
						VoipUtil.sendVoipCommand(
							ThreemaApplication.getAppContext(),
							VoipCallService.class,
							VoipCallService.ACTION_HANGUP
						);
					}
				}
			}
		}
	}

	/**
	 * Return whether this is a media button that we want to use to accept a call.
	 */
	private static boolean isAcceptButton(int keyCode) {
		return
			keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
				keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
				keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
				keyCode == KeyEvent.KEYCODE_HEADSETHOOK;
	}
}
