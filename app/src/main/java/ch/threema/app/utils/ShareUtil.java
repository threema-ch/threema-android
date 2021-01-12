/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.content.Intent;

import androidx.core.app.ActivityCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.UserService;
import ch.threema.storage.models.ContactModel;

public class ShareUtil {
	public static void shareContact(Context context, ContactModel contact) {
		UserService userService = null;
		try {
			userService = ThreemaApplication.getServiceManager().getUserService();
		} catch (Exception ignored) {}

		if (context != null && userService != null) {
			String contactName = contact != null ? NameUtil.getDisplayName(contact) : context.getString(R.string.title_mythreemaid);
			String identity = contact != null ? contact.getIdentity() : userService.getIdentity();

			Intent shareIntent = new Intent(Intent.ACTION_SEND);
			shareIntent.setType("text/plain");
			shareIntent.putExtra(Intent.EXTRA_TEXT, contactName + ": https://" + context.getString(R.string.contact_action_url) + "/" + identity);

			ActivityCompat.startActivity(context, Intent.createChooser(shareIntent, context.getString(R.string.share_via)), null);
		}
	}

}
