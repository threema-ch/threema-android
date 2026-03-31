package ch.threema.app.services;

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.collection.SparseArrayCompat;
import ch.threema.data.datatypes.IdColor;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.group.GroupMessageModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;

public class CacheService {
    private final @NonNull Collection<MessageModel> messageModelCache = new HashSet<>();
    private final @NonNull Collection<DistributionListMessageModel> distributionListMessageCache = new HashSet<>();
    private final @NonNull SparseArrayCompat<GroupModelOld> groupModelCache = new SparseArrayCompat<>();
    private final @NonNull SparseArrayCompat<String[]> groupIdentityCache = new SparseArrayCompat<>();
    private final @NonNull Collection<GroupMessageModel> groupMessageModelCache = new HashSet<>();
    private final @NonNull List<ConversationModel> conversationModelCache = new ArrayList<>();
    private final @NonNull SparseArrayCompat<Map<String, IdColor>> groupMemberColorCache = new SparseArrayCompat<>();
    private final @NonNull SparseArray<BallotModel> ballotModelCache = new SparseArray<>();
    private final @NonNull SparseArray<LinkBallotModel> linkBallotModelCache = new SparseArray<>();
    private final @NonNull Map<String, ContactModel> contactModelCache = new HashMap<>();

    public @NonNull List<ConversationModel> getConversationModelCache() {
        return this.conversationModelCache;
    }

    public @NonNull Collection<MessageModel> getMessageModelCache() {
        return this.messageModelCache;
    }

    public @NonNull SparseArrayCompat<String[]> getGroupIdentityCache() {
        return this.groupIdentityCache;
    }

    public @NonNull SparseArrayCompat<GroupModelOld> getGroupModelCache() {
        return this.groupModelCache;
    }

    public @NonNull Collection<GroupMessageModel> getGroupMessageModelCache() {
        return this.groupMessageModelCache;
    }

    public @NonNull Collection<DistributionListMessageModel> getDistributionListMessageCache() {
        return this.distributionListMessageCache;
    }

    public @NonNull SparseArrayCompat<Map<String, IdColor>> getGroupMemberColorCache() {
        return this.groupMemberColorCache;
    }

    public @NonNull Map<String, ContactModel> getContactModelCache() {
        return this.contactModelCache;
    }

    public @NonNull SparseArray<BallotModel> getBallotModelCache() {
        return this.ballotModelCache;
    }

    public @NonNull SparseArray<LinkBallotModel> getLinkBallotModelCache() {
        return this.linkBallotModelCache;
    }
}
