/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.tasks.TaskCreator;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.ConversationTag;
import ch.threema.storage.models.GroupModel;

public class ConversationTagServiceImpl implements ConversationTagService {
    @NonNull
    private final DatabaseService databaseService;
    @NonNull
    private final TaskCreator taskCreator;
    @NonNull
    private final MultiDeviceManager multiDeviceManager;

    public ConversationTagServiceImpl(
        @NonNull DatabaseService databaseService,
        @NonNull TaskCreator taskCreator,
        @NonNull MultiDeviceManager multiDeviceManager
    ) {
        this.databaseService = databaseService;
        this.taskCreator = taskCreator;
        this.multiDeviceManager = multiDeviceManager;
    }

    @Override
    public void addTagAndNotify(@Nullable ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (conversation == null || this.isTaggedWith(conversation, tag)) {
            return;
        }
        this.databaseService.getConversationTagFactory()
            .create(new ConversationTagModel(conversation.getUid(), tag));
        // Note that we only synchronize the pinned tag and not the unread
        if (tag == ConversationTag.PINNED) {
            reflectConversationCategoryPinnedIfApplicable(conversation, true, triggerSource);
        }
        this.fireOnModifiedConversation(conversation);
    }

    @Override
    public void removeTagAndNotify(@Nullable ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (conversation == null || !this.isTaggedWith(conversation, tag)) {
            return;
        }
        this.databaseService.getConversationTagFactory().deleteByConversationUidAndTag(conversation.getUid(), tag);
        // Note that we only synchronize the pinned tag and not the unread
        if (tag == ConversationTag.PINNED) {
            reflectConversationCategoryPinnedIfApplicable(conversation, false, triggerSource);
        }
        this.fireOnModifiedConversation(conversation);
    }

    @Override
    public void removeTag(@NonNull String conversationUid, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        this.databaseService.getConversationTagFactory().deleteByConversationUidAndTag(conversationUid, tag);
        // Note that we only synchronize the pinned tag and not the unread
        if (tag == ConversationTag.PINNED) {
            reflectConversationCategoryPinnedIfApplicable(conversationUid, false, triggerSource);
        }
    }

    @Override
    public void toggle(@Nullable ConversationModel conversation, @NonNull ConversationTag tag, boolean silent, @NonNull TriggerSource triggerSource) {
        if (conversation == null) {
            return;
        }
        if (this.isTaggedWith(conversation, tag)) {
            // remove
            this.databaseService.getConversationTagFactory()
                .deleteByConversationUidAndTag(conversation.getUid(), tag);
            // Note that we only synchronize the pinned tag and not the unread
            if (tag == ConversationTag.PINNED) {
                reflectConversationCategoryPinnedIfApplicable(conversation, false, triggerSource);
            }
        } else {
            // Add
            this.databaseService.getConversationTagFactory()
                .create(new ConversationTagModel(conversation.getUid(), tag));
            // Note that we only synchronize the pinned tag and not the unread
            if (tag == ConversationTag.PINNED) {
                reflectConversationCategoryPinnedIfApplicable(conversation, true, triggerSource);
            }
        }
        if (!silent) {
            this.fireOnModifiedConversation(conversation);
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
        return this.databaseService.getConversationTagFactory()
            .getByConversationUidAndTag(conversationUid, tag) != null;
    }

    @Override
    public void removeAll(@Nullable ConversationModel conversation, @NonNull TriggerSource triggerSource) {
        if (conversation != null) {
            boolean wasPinTagged = isTaggedWith(conversation, ConversationTag.PINNED);
            this.databaseService.getConversationTagFactory()
                .deleteByConversationUid(conversation.getUid());
            if (wasPinTagged) {
                reflectConversationCategoryPinnedIfApplicable(conversation, false, triggerSource);
            }
        }
    }

    @Override
    public void removeAll(@NonNull String conversationUid, @NonNull TriggerSource triggerSource) {
        boolean wasPinTagged = isTaggedWith(conversationUid, ConversationTag.PINNED);

        this.databaseService.getConversationTagFactory()
            .deleteByConversationUid(conversationUid);

        if (wasPinTagged) {
            reflectConversationCategoryPinnedIfApplicable(conversationUid, false, triggerSource);
        }
    }

    @Override
    public List<ConversationTagModel> getAll() {
        return this.databaseService.getConversationTagFactory().getAll();
    }

    @Override
    @NonNull
    public List<String> getConversationUidsByTag(@NonNull ConversationTag tag) {
        return this.databaseService.getConversationTagFactory().getAllConversationUidsByTag(tag);
    }

    @Override
    public long getCount(@NonNull ConversationTag tag) {
        return this.databaseService.getConversationTagFactory().countByTag(tag);
    }

    private void fireOnModifiedConversation(final ConversationModel conversationModel) {
        ListenerManager.conversationListeners.handle(
            listener -> listener.onModified(conversationModel, conversationModel.getPosition())
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

        GroupModel groupModel = conversationModel.getGroup();
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
