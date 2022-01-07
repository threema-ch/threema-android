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

package ch.threema.app.services;

import android.content.Context;
import android.net.Uri;

public interface RingtoneService {

	void init();
	void setRingtone(String uniqueId, Uri ringtoneUri);

	Uri getRingtoneFromUniqueId(String uniqueId);
	Uri getContactRingtone(String uniqueId);
	Uri getGroupRingtone(String uniqueId);
	Uri getVoiceCallRingtone(String uniqueId);

	Uri getDefaultContactRingtone();
	Uri getDefaultGroupRingtone();

	boolean isSilent(String uniqueId, boolean isGroup);

	boolean hasCustomRingtone(String uniqueId);
	void removeCustomRingtone(String uniqueId);

	void resetRingtones(Context context);
}
