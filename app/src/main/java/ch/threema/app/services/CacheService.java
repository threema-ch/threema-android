/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;

public class CacheService {
	private final Collection<MessageModel> messageModelCache = new HashSet<>();
	private final Collection<DistributionListMessageModel> distributionListMessageCache = new HashSet<>();
	private final SparseArray<GroupModel> groupModelCache = new SparseArray<>();
	private final SparseArray<String[]> groupIdentityCache = new SparseArray<>();
	private final Collection<GroupMessageModel> groupMessageModelCache = new HashSet<>();
	private final List<ConversationModel> conversationModelCache = new ArrayList<>();
	private final Map<String, int[]> colors = new HashMap<>();
	private final SparseArray<Map<String, Integer>> groupMemberColorCache = new SparseArray<>();
	private final SparseArray<BallotModel> ballotModelCache = new SparseArray<>();
	private final SparseArray<LinkBallotModel> linkBallotModelCache = new SparseArray<>();
	private final Map<String, ContactModel> contactModelCache = new HashMap<>();

	public interface CreateCachedColorList{
		int[] create();
	}

	public List<ConversationModel> getConversationModelCache() {
		return this.conversationModelCache;
	}

	public Collection<MessageModel> getMessageModelCache() {
		return this.messageModelCache;
	}

	public SparseArray<String[]> getGroupIdentityCache() {
		return this.groupIdentityCache;
	}

	public SparseArray<GroupModel> getGroupModelCache() {
		return this.groupModelCache;
	}

	public Collection<GroupMessageModel> getGroupMessageModelCache() {
		return this.groupMessageModelCache;
	}

	public Collection<DistributionListMessageModel> getDistributionListMessageCache() {
		return this.distributionListMessageCache;
	}

	public SparseArray<Map<String, Integer>> getGroupMemberColorCache() {
		return this.groupMemberColorCache;
	}

	public Map<String, ContactModel> getContactModelCache() {
		return this.contactModelCache;
	}

	public int[] getDistributionListColors(DistributionListModel distributionListModel, boolean forceRebuild, CreateCachedColorList createCachedColorList) {
		return this.getColor("dl-" + String.valueOf(distributionListModel.getId()), forceRebuild, createCachedColorList);
	}

	public SparseArray<BallotModel> getBallotModelCache() {
		return this.ballotModelCache;
	}
	public SparseArray<LinkBallotModel> getLinkBallotModelCache() {
		return this.linkBallotModelCache;
	}

	private int[] getColor(String key, boolean forceRebuild, CreateCachedColorList createCachedColorList) {
		if(this.colors.containsKey(key) && !forceRebuild) {
			return this.colors.get(key);
		}
		else {
			int[] r = createCachedColorList.create();
			this.colors.put(key, r);
			return r;
		}
	}

}
