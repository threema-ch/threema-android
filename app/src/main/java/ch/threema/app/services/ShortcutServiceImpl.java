/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

import android.annotation.TargetApi;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public class ShortcutServiceImpl implements ShortcutService {
	private static final Logger logger = LoggerFactory.getLogger(ShortcutService.class);

	private final Context context;
	private final ContactService contactService;
	private final GroupService groupService;
	private final DistributionListService distributionListService;

	public ShortcutServiceImpl(Context context, ContactService contactService, GroupService groupService, DistributionListService distributionListService) {
		this.context = context;
		this.contactService = contactService;
		this.groupService = groupService;
		this.distributionListService = distributionListService;
	}

	private class CommonShortcutInfo {
		Intent intent;
		Bitmap bitmap;
		String longLabel;
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public void updateShortcut(ContactModel contactModel) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
			String uniqueId = contactService.getUniqueIdString(contactModel);

			if (!TestUtil.empty(uniqueId)) {
				List<ShortcutInfo> matchingShortcuts = new ArrayList<>();

				for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
					if (shortcutInfo.getId().equals(TYPE_CHAT + uniqueId)) {
						matchingShortcuts.add(getShortcutInfo(contactModel, TYPE_CHAT));
					} else if (shortcutInfo.getId().equals(TYPE_CALL + uniqueId)) {
						matchingShortcuts.add(getShortcutInfo(contactModel, TYPE_CALL));
					}
				}

				if (matchingShortcuts.size() > 0) {
					shortcutManager.updateShortcuts(matchingShortcuts);
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public void updateShortcut(GroupModel groupModel) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
			String uniqueId = groupService.getUniqueIdString(groupModel);

			if (!TestUtil.empty(uniqueId)) {
				List<ShortcutInfo> matchingShortcuts = new ArrayList<>();

				for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
					if (shortcutInfo.getId().equals(TYPE_CHAT + uniqueId)) {
						matchingShortcuts.add(getShortcutInfo(groupModel));
					}
				}

				if (matchingShortcuts.size() > 0) {
					shortcutManager.updateShortcuts(matchingShortcuts);
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public void updateShortcut(DistributionListModel distributionListModel) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
			String uniqueId = distributionListService.getUniqueIdString(distributionListModel);

			if (!TestUtil.empty(uniqueId)) {
				List<ShortcutInfo> matchingShortcuts = new ArrayList<>();

				for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
					if (shortcutInfo.getId().equals(TYPE_CHAT + uniqueId)) {
						matchingShortcuts.add(getShortcutInfo(distributionListModel));
					}
				}

				if (matchingShortcuts.size() > 0) {
					shortcutManager.updateShortcuts(matchingShortcuts);
				}
			}
		}
	}

	@Override
	public void createShortcut(ContactModel contactModel, int type) {
		ShortcutInfoCompat shortcutInfoCompat = getShortcutInfoCompat(contactModel, type);

		if (shortcutInfoCompat != null) {
			ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, null);
		}
	}

	@Override
	public void createShortcut(GroupModel groupModel) {
		ShortcutInfoCompat shortcutInfoCompat = getShortcutInfoCompat(groupModel);

		if (shortcutInfoCompat != null) {
			ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, null);
		}
	}

	@Override
	public void createShortcut(DistributionListModel distributionListModel) {
		ShortcutInfoCompat shortcutInfoCompat = getShortcutInfoCompat(distributionListModel);

		if (shortcutInfoCompat != null) {
			ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, null);
		}
	}

	@NonNull
	private CommonShortcutInfo getCommonShortcutInfo(ContactModel contactModel, int type) {
		CommonShortcutInfo commonShortcutInfo = new CommonShortcutInfo();
		if (type == TYPE_CALL) {
			commonShortcutInfo.intent = getCallShortcutIntent();
			commonShortcutInfo.intent.putExtra(VoipCallService.EXTRA_CONTACT_IDENTITY, contactModel.getIdentity());
			commonShortcutInfo.longLabel = String.format(context.getString(R.string.threema_call_with), NameUtil.getDisplayNameOrNickname(contactModel, true));
			VectorDrawableCompat phoneDrawable = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_phone_locked, null);
			Bitmap phoneBitmap = AvatarConverterUtil.getAvatarBitmap(phoneDrawable, Color.BLACK, context.getResources().getDimensionPixelSize(R.dimen.shortcut_overlay_size));
			commonShortcutInfo.bitmap = BitmapUtil.addOverlay(getRoundBitmap(contactService.getAvatar(contactModel, false)), phoneBitmap, context.getResources().getDimensionPixelSize(R.dimen.call_shortcut_shadow_offset));
		} else {
			commonShortcutInfo.intent = getChatShortcutIntent();
			commonShortcutInfo.intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, contactModel.getIdentity());
			commonShortcutInfo.longLabel = String.format(context.getString(R.string.chat_with), NameUtil.getDisplayNameOrNickname(contactModel, true));
			commonShortcutInfo.bitmap = getRoundBitmap(contactService.getAvatar(contactModel, false));
		}
		return commonShortcutInfo;
	}

	@Nullable
	private ShortcutInfoCompat getShortcutInfoCompat(ContactModel contactModel, int type) {
		CommonShortcutInfo commonShortcutInfo = getCommonShortcutInfo(contactModel, type);
		try {
			return new ShortcutInfoCompat.Builder(context, type + contactService.getUniqueIdString(contactModel))
				.setIcon(IconCompat.createWithBitmap(commonShortcutInfo.bitmap))
				.setShortLabel(NameUtil.getDisplayNameOrNickname(contactModel, true))
				.setLongLabel(commonShortcutInfo.longLabel)
				.setIntent(commonShortcutInfo.intent)
				.build();
		} catch (IllegalArgumentException e) {
			logger.error("Exception", e);
		}
		return null;
	}

	@Nullable
	private ShortcutInfoCompat getShortcutInfoCompat(GroupModel groupModel) {
		Intent intent = getChatShortcutIntent();
		intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupModel.getId());

		Bitmap avatarBitmap = getRoundBitmap(groupService.getAvatar(groupModel, false));

		if (avatarBitmap != null) {
			return new ShortcutInfoCompat.Builder(context, TYPE_CHAT + groupService.getUniqueIdString(groupModel))
				.setIcon(IconCompat.createWithBitmap(avatarBitmap))
				.setShortLabel(groupModel.getName())
				.setLongLabel(String.format(context.getString(R.string.chat_with), groupModel.getName()))
				.setIntent(intent)
				.build();
		}
		return null;
	}

	@Nullable
	private ShortcutInfoCompat getShortcutInfoCompat(DistributionListModel distributionListModel) {
		Intent intent = getChatShortcutIntent();
		intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, distributionListModel.getId());

		Bitmap avatarBitmap = getRoundBitmap(distributionListService.getAvatar(distributionListModel, false));

		if (avatarBitmap != null) {
			return new ShortcutInfoCompat.Builder(context, TYPE_CHAT + distributionListService.getUniqueIdString(distributionListModel))
				.setIcon(IconCompat.createWithBitmap(avatarBitmap))
				.setShortLabel(distributionListModel.getName())
				.setLongLabel(String.format(context.getString(R.string.chat_with), distributionListModel.getName()))
				.setIntent(intent)
				.build();
		}
		return null;
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public ShortcutInfo getShortcutInfo(ContactModel contactModel, int type) {
		CommonShortcutInfo commonShortcutInfo = getCommonShortcutInfo(contactModel, type);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
			return new ShortcutInfo.Builder(context, type + contactService.getUniqueIdString(contactModel))
				.setIcon(Icon.createWithAdaptiveBitmap(commonShortcutInfo.bitmap))
				.setShortLabel(NameUtil.getDisplayNameOrNickname(contactModel, true))
				.setLongLabel(commonShortcutInfo.longLabel)
				.setLongLived(true)
				.setPerson(
					new Person.Builder()
						.setName(contactModel.getIdentity())
						.setIcon(Icon.createWithBitmap((commonShortcutInfo.bitmap)))
						.build()
				)
				.setIntent(commonShortcutInfo.intent)
				.build();
		} else {
			return new ShortcutInfo.Builder(context, type + contactService.getUniqueIdString(contactModel))
				.setIcon(Icon.createWithAdaptiveBitmap(commonShortcutInfo.bitmap))
				.setShortLabel(NameUtil.getDisplayNameOrNickname(contactModel, true))
				.setLongLabel(commonShortcutInfo.longLabel)
				.setIntent(commonShortcutInfo.intent)
				.build();
		}
	}

	@Nullable
	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public ShortcutInfo getShortcutInfo(GroupModel groupModel) {
		Intent intent = getChatShortcutIntent();
		intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupModel.getId());

		Bitmap avatarBitmap = getRoundBitmap(groupService.getAvatar(groupModel, false));
		if (avatarBitmap != null){
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				return new ShortcutInfo.Builder(context, TYPE_CHAT + groupService.getUniqueIdString(groupModel))
					.setIcon(Icon.createWithAdaptiveBitmap(avatarBitmap))
					.setShortLabel(groupModel.getName())
					.setLongLived(true)
					.setLongLabel(String.format(context.getString(R.string.chat_with), groupModel.getName()))
					.setIntent(intent)
					.build();
			} else {
				return new ShortcutInfo.Builder(context, TYPE_CHAT + groupService.getUniqueIdString(groupModel))
					.setIcon(Icon.createWithAdaptiveBitmap(avatarBitmap))
					.setShortLabel(groupModel.getName())
					.setLongLabel(String.format(context.getString(R.string.chat_with), groupModel.getName()))
					.setIntent(intent)
					.build();
			}
		}

		return null;
	}

	@Nullable
	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public ShortcutInfo getShortcutInfo(DistributionListModel distributionListModel) {
		Intent intent = getChatShortcutIntent();
		intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, distributionListModel.getId());

		Bitmap avatarBitmap = getRoundBitmap(distributionListService.getAvatar(distributionListModel, false));

		if (avatarBitmap != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ){
				return new ShortcutInfo.Builder(context, TYPE_CHAT + distributionListService.getUniqueIdString(distributionListModel))
					.setIcon(Icon.createWithAdaptiveBitmap(avatarBitmap))
					.setShortLabel(distributionListModel.getName())
					.setLongLived(true)
					.setLongLabel(String.format(context.getString(R.string.chat_with), distributionListModel.getName()))
					.setIntent(intent)
					.build();
			} else {
				return new ShortcutInfo.Builder(context, TYPE_CHAT + distributionListService.getUniqueIdString(distributionListModel))
					.setIcon(Icon.createWithAdaptiveBitmap(avatarBitmap))
					.setShortLabel(distributionListModel.getName())
					.setLongLabel(String.format(context.getString(R.string.chat_with), distributionListModel.getName()))
					.setIntent(intent)
					.build();
			}
		}
		return null;
	}

	private Intent getChatShortcutIntent() {
		Intent intent = new Intent(context, ComposeMessageActivity.class);
		intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.setAction(Intent.ACTION_MAIN);

		return intent;
	}

	private Intent getCallShortcutIntent() {
		Intent intent = new Intent(context, CallActivity.class);
		intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
		intent.setAction(Intent.ACTION_MAIN);
		intent.putExtra(CallActivity.EXTRA_CALL_FROM_SHORTCUT, true);
		intent.putExtra(VoipCallService.EXTRA_IS_INITIATOR, true);
		intent.putExtra(VoipCallService.EXTRA_CALL_ID, -1L);

		return intent;
	}

	private @Nullable Bitmap getRoundBitmap(@Nullable Bitmap src) {
		if (src != null) {
			return AvatarConverterUtil.convertToRound(context.getResources(), BitmapUtil.replaceTransparency(src, Color.WHITE), Color.WHITE, null, src.getWidth());
		}
		return null;
	}
}
