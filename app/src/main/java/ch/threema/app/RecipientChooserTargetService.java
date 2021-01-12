/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.Nullable;
import ch.threema.app.activities.RecipientListActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ConversationModel;

@TargetApi(Build.VERSION_CODES.M)
public class RecipientChooserTargetService extends ChooserTargetService {
	private static final Logger logger = LoggerFactory.getLogger(RecipientChooserTargetService.class);

	private static final int MAX_CONVERSATIONS = 8;
	private PreferenceService preferenceService;

	@Override
	public @Nullable List<ChooserTarget> onGetChooserTargets(
		ComponentName targetActivityName,
		IntentFilter matchedFilter
	) {
		logger.debug("onGetChooserTargets");

		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			return null;
		}

		ConversationService conversationService = null;
		GroupService groupService = null;
		ContactService contactService = null;
		preferenceService = null;

		try {
			conversationService = serviceManager.getConversationService();
			groupService = serviceManager.getGroupService();
			contactService = serviceManager.getContactService();
			preferenceService = serviceManager.getPreferenceService();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
		}

		if (conversationService == null || groupService == null || contactService == null || preferenceService == null) {
			return null;
		}

		if (!preferenceService.isDirectShare()) {
			// only enable this feature if sync contacts is enabled (privacy risk)
			return null;
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
			public boolean noHiddenChats() { return preferenceService.isPrivateChatsHidden(); }

			@Override
			public boolean noInvalid() {
				return true;
			}
		};

		final List<ConversationModel> conversations = conversationService.getAll(false, filter);
		int length = Math.min(conversations.size(), MAX_CONVERSATIONS);

		final ComponentName componentName = new ComponentName(
			getPackageName(),
			Objects.requireNonNull(RecipientListActivity.class.getCanonicalName())
		);

		final List<ChooserTarget> targets = new ArrayList<>();
		for (int i = 0; i < length; i++) {
			final String title;
			final Bitmap avatar;
			final Bundle extras = new Bundle();
			final ConversationModel conversationModel = conversations.get(i);

			if (conversationModel.isGroupConversation()) {
				title = NameUtil.getDisplayName(conversationModel.getGroup(), groupService);
				avatar = groupService.getAvatar(conversationModel.getGroup(), false);
				extras.putInt(IntentDataUtil.INTENT_DATA_GROUP_ID, conversationModel.getGroup().getId());
			} else {
				title = NameUtil.getDisplayNameOrNickname(conversationModel.getContact(), true);
				avatar = contactService.getAvatar(conversationModel.getContact(), false);
				extras.putString(IntentDataUtil.INTENT_DATA_IDENTITY, conversationModel.getContact().getIdentity());
			}

			final Icon icon = Icon.createWithBitmap(avatar);
			final float score = ((float) MAX_CONVERSATIONS - ((float) i / 2)) / (float) MAX_CONVERSATIONS;

			targets.add(new ChooserTarget(title, icon, score, componentName, extras));
		}
		return targets;
	}
}
