package ch.threema.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.tasks.TaskCreator;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.factories.ConversationTagFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.ConversationTag;
import ch.threema.storage.models.group.GroupModelOld;

public class ConversationTagServiceImpl implements ConversationTagService {
    @NonNull
    private final ConversationTagFactory conversationTagFactory;
    @NonNull
    private final TaskCreator taskCreator;
    @NonNull
    private final MultiDeviceManager multiDeviceManager;

    public ConversationTagServiceImpl(
        @NonNull ConversationTagFactory conversationTagFactory,
        @NonNull TaskCreator taskCreator,
        @NonNull MultiDeviceManager multiDeviceManager
    ) {
        this.conversationTagFactory = conversationTagFactory;
        this.taskCreator = taskCreator;
        this.multiDeviceManager = multiDeviceManager;
    }

    @Override
    public void addTagAndNotify(@Nullable ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (conversation == null || this.isTaggedWith(conversation, tag)) {
            return;
        }
        conversationTagFactory
            .create(new ConversationTagModel(conversation.getUid(), tag));
        if (tag == ConversationTag.PINNED) {
            // Note that we only synchronize the pinned tag and not the unread
            reflectConversationCategoryPinnedIfApplicable(conversation, true, triggerSource);
        }
        this.fireOnModifiedConversation(conversation);
    }

    @Override
    public void removeTagAndNotify(@Nullable ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (conversation == null || !this.isTaggedWith(conversation, tag)) {
            return;
        }
        conversationTagFactory.deleteByConversationUidAndTag(conversation.getUid(), tag);
        if (tag == ConversationTag.PINNED) {
            // Note that we only synchronize the pinned tag and not the unread
            reflectConversationCategoryPinnedIfApplicable(conversation, false, triggerSource);
        }
        this.fireOnModifiedConversation(conversation);
    }

    @Override
    public boolean removeTag(@NonNull String conversationUid, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (!isTaggedWith(conversationUid, tag)) {
            return false;
        }
        conversationTagFactory.deleteByConversationUidAndTag(conversationUid, tag);
        // Note that we only synchronize the pinned tag and not the unread
        if (tag == ConversationTag.PINNED) {
            reflectConversationCategoryPinnedIfApplicable(conversationUid, false, triggerSource);
        }
        return true;
    }

    @Override
    public boolean addTag(@NonNull String conversationUid, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (isTaggedWith(conversationUid, tag)) {
            return false;
        }
        conversationTagFactory.create(
            new ConversationTagModel(conversationUid, tag)
        );
        // Note that we only synchronize the pinned tag and not the unread
        if (tag == ConversationTag.PINNED) {
            reflectConversationCategoryPinnedIfApplicable(conversationUid, false, triggerSource);
        }
        return true;
    }

    @Override
    public boolean toggle(@NonNull ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (this.isTaggedWith(conversation, tag)) {
            // remove
            conversationTagFactory
                .deleteByConversationUidAndTag(conversation.getUid(), tag);
            // Note that we only synchronize the pinned tag and not the unread
            if (tag == ConversationTag.PINNED) {
                reflectConversationCategoryPinnedIfApplicable(conversation, false, triggerSource);
            }
            return false;
        } else {
            // Add
            conversationTagFactory
                .create(new ConversationTagModel(conversation.getUid(), tag));
            // Note that we only synchronize the pinned tag and not the unread
            if (tag == ConversationTag.PINNED) {
                reflectConversationCategoryPinnedIfApplicable(conversation, true, triggerSource);
            }
            return true;
        }
    }

    @Override
    public boolean isTaggedWith(@Nullable ConversationModel conversation, @NonNull ConversationTag tag) {
        if (conversation == null) {
            return false;
        }

        return isTaggedWith(conversation.getUid(), tag);
    }

    @Override
    public boolean isTaggedWith(@NonNull String conversationUid, @NonNull ConversationTag tag) {
        return conversationTagFactory
            .getByConversationUidAndTag(conversationUid, tag) != null;
    }

    @Override
    public void removeAll(@Nullable ConversationModel conversation, @NonNull TriggerSource triggerSource) {
        if (conversation != null) {
            boolean wasPinTagged = isTaggedWith(conversation, ConversationTag.PINNED);
            conversationTagFactory
                .deleteByConversationUid(conversation.getUid());
            if (wasPinTagged) {
                reflectConversationCategoryPinnedIfApplicable(conversation, false, triggerSource);
            }
        }
    }

    @Override
    public void removeAll(@NonNull String conversationUid, @NonNull TriggerSource triggerSource) {
        boolean wasPinTagged = isTaggedWith(conversationUid, ConversationTag.PINNED);

        conversationTagFactory
            .deleteByConversationUid(conversationUid);

        if (wasPinTagged) {
            reflectConversationCategoryPinnedIfApplicable(conversationUid, false, triggerSource);
        }
    }

    @Override
    public List<ConversationTagModel> getAll() {
        return conversationTagFactory.getAll();
    }

    @Override
    @NonNull
    public List<String> getConversationUidsByTag(@NonNull ConversationTag tag) {
        return conversationTagFactory.getAllConversationUidsByTag(tag);
    }

    @Override
    public long getCount(@NonNull ConversationTag tag) {
        return conversationTagFactory.countByTag(tag);
    }

    private void fireOnModifiedConversation(final ConversationModel conversationModel) {
        ListenerManager.conversationListeners.handle(
            listener -> listener.onModified(conversationModel)
        );
    }

    private void reflectConversationCategoryPinnedIfApplicable(
        @NonNull ConversationModel conversationModel,
        boolean isPinned,
        @NonNull TriggerSource triggerSource
    ) {
        if (triggerSource == TriggerSource.SYNC || conversationModel.isDistributionListConversation()
            || !multiDeviceManager.isMultiDeviceActive()) {
            return;
        }
        ContactModel contactModel = conversationModel.getContact();
        if (contactModel != null) {
            reflectContactPinnedIfApplicable(contactModel.getIdentity(), isPinned, triggerSource);
            return;
        }

        GroupModelOld groupModel = conversationModel.getGroup();
        if (groupModel != null) {
            reflectGroupPinnedIfApplicable(groupModel.getId(), isPinned, triggerSource);
        }
    }

    private void reflectConversationCategoryPinnedIfApplicable(@NonNull String conversationUid, boolean isPinned, @NonNull TriggerSource triggerSource) {
        if (triggerSource == TriggerSource.SYNC || !multiDeviceManager.isMultiDeviceActive()) {
            return;
        }
        String identity = ConversationUtil.getContactIdentityFromUid(conversationUid);
        if (identity != null) {
            reflectContactPinnedIfApplicable(identity, isPinned, triggerSource);
            return;
        }

        Long groupDatabaseId = ConversationUtil.getGroupDatabaseIdFromUid(conversationUid);
        if (groupDatabaseId != null) {
            reflectGroupPinnedIfApplicable(groupDatabaseId, isPinned, triggerSource);
        }
    }

    private void reflectContactPinnedIfApplicable(@NonNull String identity, boolean isPinned, @NonNull TriggerSource triggerSource) {
        if (triggerSource == TriggerSource.SYNC || !multiDeviceManager.isMultiDeviceActive()) {
            return;
        }
        taskCreator.scheduleReflectConversationVisibilityPinned(identity, isPinned);
    }

    private void reflectGroupPinnedIfApplicable(long groupDatabaseId, boolean isPinned, @NonNull TriggerSource triggerSource) {
        if (triggerSource == TriggerSource.SYNC || !multiDeviceManager.isMultiDeviceActive()) {
            return;
        }
        taskCreator.scheduleReflectGroupConversationVisibilityPinned(groupDatabaseId, isPinned);
    }
}
