/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.services;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;

import ch.threema.app.R;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.app.utils.TestUtil;

public class RingtoneServiceImpl implements RingtoneService {
	private PreferenceService preferenceService;
	private HashMap<String, String> ringtones;

	public RingtoneServiceImpl(PreferenceService preferenceService) {
		this.preferenceService = preferenceService;

		init();
	}

	@Override
	public void init() {
		ringtones = preferenceService.getRingtones();
	}

	@Override
	public void setRingtone(String uniqueId, Uri ringtoneUri) {
		String ringtone = null;

		if (ringtoneUri != null) {
			ringtone = ringtoneUri.toString();
		}

		if (ringtoneUri != null && RingtoneManager.isDefault(ringtoneUri)) {
			if (ringtones.containsKey(uniqueId)) {
				ringtones.remove(uniqueId);
			}
		} else {
			ringtones.put(uniqueId, ringtone);
		}

		preferenceService.setRingtones(ringtones);
	}

	@Override
	public Uri getRingtoneFromUniqueId(String uniqueId) {
		String ringtone = ringtones.get(uniqueId);
		// check for "null" string (HTC bug)
		if (ringtone != null && !ringtone.equals("null")) {
			return Uri.parse(ringtone);
		} else {
			// silent
			return null;
		}
	}

	@Override
	public boolean hasCustomRingtone(String uniqueId) {
		return ringtones != null && ringtones.containsKey(uniqueId);
	}

	@Override
	public void removeCustomRingtone(String uniqueId) {
		if (ringtones != null && ringtones.containsKey(uniqueId)) {
			ringtones.remove(uniqueId);

			preferenceService.setRingtones(ringtones);
		}
	}

	@Override
	public void resetRingtones(Context context) {
		if (ringtones != null) {
			ringtones.clear();
			preferenceService.setRingtones(ringtones);
		}
		preferenceService.setGroupNotificationSound(Uri.parse(context.getString(R.string.default_notification_sound)));
		preferenceService.setNotificationSound(Uri.parse(context.getString(R.string.default_notification_sound)));
		preferenceService.setVoiceCallSound(RingtoneUtil.THREEMA_CALL_RINGTONE_URI);
		preferenceService.setNotificationPriority(NotificationCompat.PRIORITY_HIGH);
	}

	@Override
	public Uri getContactRingtone(String uniqueId) {
		if (ringtones.containsKey(uniqueId)) {
			return getRingtoneFromUniqueId(uniqueId);
		} else {
			return preferenceService.getNotificationSound();
		}
	}

	@Override
	public Uri getGroupRingtone(String uniqueId) {
		if (ringtones.containsKey(uniqueId)) {
			return getRingtoneFromUniqueId(uniqueId);
		} else {
			return preferenceService.getGroupNotificationSound();
		}
	}

	@Override
	public Uri getVoiceCallRingtone(String uniqueId) {
		return preferenceService.getVoiceCallSound();
	}

	@Override
	public Uri getDefaultContactRingtone() {
		return preferenceService.getNotificationSound();
	}

	@Override
	public Uri getDefaultGroupRingtone() {
		return preferenceService.getGroupNotificationSound();
	}

	private boolean hasNoRingtone(String uniqueId) {
		Uri ringtone = getRingtoneFromUniqueId(uniqueId);
		return (ringtone == null || ringtone.toString() == null || ringtone.toString().equals("null"));
	}

	@Override
	public boolean isSilent(String uniqueId, boolean isGroup) {
		if (!TestUtil.empty(uniqueId)) {
			Uri defaultRingtone, selectedRingtone;

			if (isGroup) {
				defaultRingtone = getDefaultGroupRingtone();
				selectedRingtone = getGroupRingtone(uniqueId);
			} else {
				defaultRingtone = getDefaultContactRingtone();
				selectedRingtone = getContactRingtone(uniqueId);
			}
			return !(defaultRingtone != null && selectedRingtone != null && defaultRingtone.equals(selectedRingtone)) && hasNoRingtone(uniqueId);
		}
		return false;
	}
}
