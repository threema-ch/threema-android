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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

import static java.lang.Math.min;

public class ShortcutServiceImpl implements ShortcutService {
	private static final Logger logger = LoggerFactory.getLogger(ShortcutService.class);

	private final Context context;
	private final ContactService contactService;
	private final GroupService groupService;
	private final DistributionListService distributionListService;

	private static final String DYNAMIC_SHORTCUT_SHARE_TARGET_CATEGORY = "ch.threema.app.category.DYNAMIC_SHORTCUT_SHARE_TARGET";

	public ShortcutServiceImpl(Context context, ContactService contactService, GroupService groupService,
	                           DistributionListService distributionListService) {
		this.context = context;
		this.contactService = contactService;
		this.groupService = groupService;
		this.distributionListService = distributionListService;
	}

	private static class CommonShortcutInfo {
		Intent intent;
		Bitmap bitmap;
		String longLabel;
	}

	@Override
	public void publishRecentChatsAsSharingTargets() {
		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			return;
		}

		ConversationService conversationService = null;

		try {
			conversationService = serviceManager.getConversationService();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
		}

		if (conversationService == null) {
			return;
		}

		final ConversationService.Filter filter = new ConversationService.Filter() {
			@Override
			public boolean onlyUnread() {
				return false;
			}

			@Override
			public boolean noDistributionLists() {
				return true;
			}

			@Override
			public boolean noHiddenChats() { return true; }

			@Override
			public boolean noInvalid() {
				return true;
			}
		};

		final List<ConversationModel> conversations = conversationService.getAll(false, filter);
		int numPublishableConversations = min(conversations.size(), 4);

		final List<ShortcutInfoCompat> sharingTargetShortcuts = new ArrayList<>();
		for (int i = 0; i < numPublishableConversations; i++) {
			final ConversationModel conversationModel = conversations.get(i);

			if (conversationModel.isContactConversation()) {
				sharingTargetShortcuts.add(getSharingTargetShortcutInfoCompat(conversationModel.getContact()));
			} else if (conversationModel.isGroupConversation()){
				sharingTargetShortcuts.add(getSharingTargetShortcutInfoCompat(conversationModel.getGroup()));
			} else {
				sharingTargetShortcuts.add(getSharingTargetShortcutInfoCompat(conversationModel.getDistributionList()));
			}
		}
		if (sharingTargetShortcuts.isEmpty()) {
			logger.info("No recent chats to publish sharing targets for");
			return;
		}
		publishDynamicShortcuts(sharingTargetShortcuts);
		logger.info("Published most recent conversations as sharing target shortcuts");
	}

	@RequiresApi(api = Build.VERSION_CODES.N_MR1)
	@Override
	public void publishPinnedShortcutsAsSharingTargets() {
		ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
		final List<ShortcutInfoCompat> sharingTargetShortcuts = new ArrayList<>();

		for (ShortcutInfo shortcut : shortcutManager.getPinnedShortcuts()) {

			Intent shortcutIntent = shortcut.getIntent();

			if (!shortcutIntent.hasExtra(CallActivity.EXTRA_CALL_FROM_SHORTCUT)) {
				if (shortcutIntent.hasExtra(ThreemaApplication.INTENT_DATA_GROUP)) {
					GroupModel groupModel = groupService.getById(
						shortcutIntent.getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0)
					);
					sharingTargetShortcuts.add(getSharingTargetShortcutInfoCompat(groupModel));
				} else if (shortcutIntent.hasExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST)) {
					DistributionListModel distributionList = distributionListService.getById(
						shortcutIntent.getIntExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0)
					);
					sharingTargetShortcuts.add(getSharingTargetShortcutInfoCompat(distributionList));
				} else {
					ContactModel contact = contactService.getByIdentity(
						shortcutIntent.getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT)
					);
					sharingTargetShortcuts.add(getSharingTargetShortcutInfoCompat(contact));
				}
			}
		}

		if (sharingTargetShortcuts.isEmpty()) {
			logger.info("No pinned shortcuts to publish as sharing targets");
			return;
		}
		publishDynamicShortcuts(sharingTargetShortcuts);
		logger.info("Published pinned shortcuts as sharing target shortcuts");
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

	@Override
	public void createShareTargetShortcut(ContactModel contactModel) {
		publishDynamicShortcuts(Collections.singletonList(getSharingTargetShortcutInfoCompat(contactModel)));
	}

	@Override
	public void createShareTargetShortcut(GroupModel groupModel) {
		publishDynamicShortcuts(Collections.singletonList(getSharingTargetShortcutInfoCompat(groupModel)));
	}

	@Override
	public void createShareTargetShortcut(DistributionListModel distributionListModel) {
		publishDynamicShortcuts(Collections.singletonList(getSharingTargetShortcutInfoCompat(distributionListModel)));
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
	public void deleteShortcut(ContactModel contactModel) {
		String uniqueId = contactModel.getIdentity();

		deleteShortcutByID(uniqueId);
	}

	@Override
	public void deleteShortcut(GroupModel groupModel) {
		String uniqueId = String.valueOf(groupModel.getId());

		deleteShortcutByID(uniqueId);
	}

	@Override
	public void deleteShortcut(DistributionListModel distributionListModel) {
		String uniqueId = String.valueOf(distributionListModel.getId());

		deleteShortcutByID(uniqueId);
	}

	@Override
	public void deleteDynamicShortcuts() {
		ShortcutManagerCompat.removeAllDynamicShortcuts(context);
	}

	private void deleteShortcutByID(String id) {
		if (!TestUtil.empty(id)) {
			for (ShortcutInfoCompat shortcutInfo : ShortcutManagerCompat.getDynamicShortcuts(context)) {
				// ignore first character which represents the type indicator
				if (shortcutInfo.getId().substring(1).equals(id)) {
					ShortcutManagerCompat.removeDynamicShortcuts(context, Collections.singletonList(shortcutInfo.getId()));
				}
			}
		}
	}
	/**
	 * uses addDynamicShortcuts(List<ShortcutInfoCompat> shortcuts) to add a list of shortcuts to existing shortcuts
	 * @param shortcuts to be added to already existing dynamic shortcuts
	 */
	private void tryAddingDynamicShortcuts(List<ShortcutInfoCompat> shortcuts) {
		logger.info("tryAddingDynamicShortcuts {}", shortcuts.size());
		// try to catch the max shortcuts exceeded error for some devices that always return 0 active shortcuts
		try {
			ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts);
		} catch (Exception e) {
			logger.error("Exception, failed adding dynamic shortcuts list ", e);
		}
	}
	/**
	 * uses setDynamicShortcuts(List<ShortcutInfoCompat> shortcuts) which equals to
	 * removeAllDynamicShortcuts() + addDynamicShortcuts(List<ShortcutInfoCompat> shortcuts)
	 * @param shortcuts to be set as a fresh list of dynamic shortcuts, all previous dynamic shortcuts not included in this list will be removed.
	 */
	private void trySettingDynamicShortcuts(List<ShortcutInfoCompat> shortcuts) {
		logger.info("trying to reset shortcut list {}", shortcuts.size());
		// try to catch the max shortcuts exceeded error for some devices that always return 0 active shortcuts
		try {
			ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
		} catch (IllegalArgumentException e) {
			logger.error("Exception, failed setting dynamic shortcuts list ", e);
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
	public ShortcutInfoCompat getShortcutInfoCompat(ContactModel contactModel, int type) {
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
	public ShortcutInfoCompat getShortcutInfoCompat(GroupModel groupModel) {
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
	public ShortcutInfoCompat getShortcutInfoCompat(DistributionListModel distributionListModel) {
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

	@Nullable
	private ShortcutInfoCompat getSharingTargetShortcutInfoCompat(ContactModel contactModel) {
		Intent intent = new Intent(Intent.ACTION_DEFAULT);

		Set<String> shortcutCategories = new HashSet<>();
		shortcutCategories.add(DYNAMIC_SHORTCUT_SHARE_TARGET_CATEGORY);

		Bitmap avatarBitmap = getRoundBitmap(contactService.getAvatar(contactModel, false));

		if (avatarBitmap != null) {
			// workaround because intent extras are lost in dynamic shortcut
			// receiver identity is passed as the shortcut id with a specific type
			return new ShortcutInfoCompat.Builder(context, TYPE_SHARE_SHORTCUT_CONTACT + contactModel.getIdentity())
				.setIcon(IconCompat.createWithBitmap(avatarBitmap))
				.setShortLabel(NameUtil.getDisplayNameOrNickname(contactModel, true))
				.setIntent(intent)
				.setLongLived(true)
				.setCategories(shortcutCategories)
				.build();
		}
		return null;
	}

	@Nullable
	private ShortcutInfoCompat getSharingTargetShortcutInfoCompat(GroupModel groupModel) {
		Intent intent = new Intent(Intent.ACTION_DEFAULT);

		Set<String> shortcutCategories = new HashSet<>();
		shortcutCategories.add(DYNAMIC_SHORTCUT_SHARE_TARGET_CATEGORY);

		Bitmap avatarBitmap = getRoundBitmap(groupService.getAvatar(groupModel, false));
		String groupName = groupModel.getName();
		if (groupName == null) {
			groupName = groupService.getMembersString(groupModel);
		}

		if (avatarBitmap != null) {
			// workaround because intent extras are lost in dynamic shortcut
			// receiver identity is passed as the shortcut id and a specific type
			return new ShortcutInfoCompat.Builder(context,TYPE_SHARE_SHORTCUT_GROUP + "" + groupModel.getId())
				.setIcon(IconCompat.createWithBitmap(avatarBitmap))
				.setShortLabel(groupName)
				.setIntent(intent)
				.setLongLived(true)
				.setCategories(shortcutCategories)
				.build();
		}
		return null;
	}

	@Nullable
	private ShortcutInfoCompat getSharingTargetShortcutInfoCompat(DistributionListModel distributionListModel) {
		Intent intent = new Intent(Intent.ACTION_DEFAULT);

		Set<String> shortcutCategories = new HashSet<>();
		shortcutCategories.add(DYNAMIC_SHORTCUT_SHARE_TARGET_CATEGORY);

		Bitmap avatarBitmap = getRoundBitmap(distributionListService.getAvatar(distributionListModel, false));
		String distributionListName = distributionListModel.getName();
		if (distributionListName == null) {
			distributionListName = distributionListService.getMembersString(distributionListModel);
		}

		if (avatarBitmap != null) {
			// workaround because intent extras are lost in dynamic shortcut
			// receiver identity is passed as the shortcut id and a specific type
			return new ShortcutInfoCompat.Builder(context, TYPE_SHARE_SHORTCUT_DISTRIBUTION_LIST + "" + distributionListModel.getId())
				.setIcon(IconCompat.createWithBitmap(avatarBitmap))
				.setShortLabel(distributionListName)
				.setIntent(intent)
				.setLongLived(true)
				.setCategories(shortcutCategories)
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

	private void publishDynamicShortcuts(List<ShortcutInfoCompat> shortcuts) {
		List<ShortcutInfoCompat> activeShortcuts = ShortcutManagerCompat.getDynamicShortcuts(context);
		int shortcutsSurplusCount = activeShortcuts.size() + shortcuts.size() - 4; //  4 as there is anyway max 4 slots of share target options in the OS share sheet
		logger.info("publish dynamic shortcuts list compat: to publish {} active {} max limit {} surplus {}",
			shortcuts.size(), activeShortcuts.size(), 4, shortcutsSurplusCount);
		if (shortcutsSurplusCount > 0) {
			while (shortcutsSurplusCount > 0) {
				activeShortcuts.remove(0);
				shortcutsSurplusCount -= 1;
			}
			activeShortcuts.addAll(shortcuts);
			// clear all and publish the modified list
			trySettingDynamicShortcuts(activeShortcuts);
		}
		else {
			tryAddingDynamicShortcuts(shortcuts);
		}
	}
}
