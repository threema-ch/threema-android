/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import androidx.annotation.NonNull;
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
    private final @NonNull Collection<MessageModel> messageModelCache = new HashSet<>();
    private final @NonNull Collection<DistributionListMessageModel> distributionListMessageCache = new HashSet<>();
    private final @NonNull SparseArray<GroupModel> groupModelCache = new SparseArray<>();
    private final @NonNull SparseArray<String[]> groupIdentityCache = new SparseArray<>();
    private final @NonNull Collection<GroupMessageModel> groupMessageModelCache = new HashSet<>();
    private final @NonNull List<ConversationModel> conversationModelCache = new ArrayList<>();
    private final @NonNull Map<String, int[]> colors = new HashMap<>();
    private final @NonNull SparseArray<Map<String, Integer>> groupMemberColorCache = new SparseArray<>();
    private final @NonNull SparseArray<BallotModel> ballotModelCache = new SparseArray<>();
    private final @NonNull SparseArray<LinkBallotModel> linkBallotModelCache = new SparseArray<>();
    private final @NonNull Map<String, ContactModel> contactModelCache = new HashMap<>();

    public interface CreateCachedColorList {
        int[] create();
    }

    public @NonNull List<ConversationModel> getConversationModelCache() {
        return this.conversationModelCache;
    }

    public @NonNull Collection<MessageModel> getMessageModelCache() {
        return this.messageModelCache;
    }

    public @NonNull SparseArray<String[]> getGroupIdentityCache() {
        return this.groupIdentityCache;
    }

    public @NonNull SparseArray<GroupModel> getGroupModelCache() {
        return this.groupModelCache;
    }

    public @NonNull Collection<GroupMessageModel> getGroupMessageModelCache() {
        return this.groupMessageModelCache;
    }

    public @NonNull Collection<DistributionListMessageModel> getDistributionListMessageCache() {
        return this.distributionListMessageCache;
    }

    public @NonNull SparseArray<Map<String, Integer>> getGroupMemberColorCache() {
        return this.groupMemberColorCache;
    }

    public @NonNull Map<String, ContactModel> getContactModelCache() {
        return this.contactModelCache;
    }

    public int[] getDistributionListColors(DistributionListModel distributionListModel, boolean forceRebuild, CreateCachedColorList createCachedColorList) {
        return this.getColor("dl-" + String.valueOf(distributionListModel.getId()), forceRebuild, createCachedColorList);
    }

    public @NonNull SparseArray<BallotModel> getBallotModelCache() {
        return this.ballotModelCache;
    }

    public @NonNull SparseArray<LinkBallotModel> getLinkBallotModelCache() {
        return this.linkBallotModelCache;
    }

    private int[] getColor(String key, boolean forceRebuild, CreateCachedColorList createCachedColorList) {
        if (this.colors.containsKey(key) && !forceRebuild) {
            return this.colors.get(key);
        } else {
            int[] r = createCachedColorList.create();
            this.colors.put(key, r);
            return r;
        }
    }

}
