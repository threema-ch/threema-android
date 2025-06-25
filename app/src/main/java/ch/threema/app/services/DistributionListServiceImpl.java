/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import org.slf4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListServiceImpl implements DistributionListService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DistributionListServiceImpl");
    private static final String DISTRIBUTION_LIST_UID_PREFIX = "d-";

    private final Context context;
    private final AvatarCacheService avatarCacheService;
    private final DatabaseService databaseService;
    private final ContactService contactService;
    private final @NonNull ConversationTagService conversationTagService;

    public DistributionListServiceImpl(
        Context context,
        AvatarCacheService avatarCacheService,
        DatabaseService databaseService,
        ContactService contactService,
        @NonNull ConversationTagService conversationTagService
    ) {
        this.context = context;
        this.avatarCacheService = avatarCacheService;
        this.databaseService = databaseService;
        this.contactService = contactService;
        this.conversationTagService = conversationTagService;
    }

    @Override
    public DistributionListModel getById(long id) {
        return this.databaseService.getDistributionListModelFactory().getById(
            id
        );
    }

    @Override
    public DistributionListModel createDistributionList(
        @Nullable String name,
        @NonNull String[] memberIdentities
    ) {
        return createDistributionList(name, memberIdentities, false);
    }

    @Override
    public DistributionListModel createDistributionList(
        @Nullable String name,
        @NonNull String[] memberIdentities,
        boolean isAdHocDistributionList
    ) {
        // Create group model in database
        final Date now = new Date();
        final DistributionListModel distributionListModel = new DistributionListModel()
            .setName(name)
            .setCreatedAt(now)
            .setLastUpdate(now)
            .setAdHocDistributionList(isAdHocDistributionList);
        this.databaseService.getDistributionListModelFactory().create(
            distributionListModel
        );

        // Add members to distribution list
        for (String identity : memberIdentities) {
            this.addMemberToDistributionList(distributionListModel, identity);
        }

        // Notify listeners
        ListenerManager.distributionListListeners.handle(listener -> listener.onCreate(distributionListModel));

        return distributionListModel;
    }

    @Override
    public DistributionListModel updateDistributionList(final DistributionListModel distributionListModel, String name, String[] memberIdentities) {
        distributionListModel.setName(name);

        //create
        this.databaseService.getDistributionListModelFactory().update(
            distributionListModel
        );

        if (this.removeMembers(distributionListModel)) {
            for (String identity : memberIdentities) {
                this.addMemberToDistributionList(distributionListModel, identity);
            }
        }

        ListenerManager.distributionListListeners.handle(new ListenerManager.HandleListener<DistributionListListener>() {
            @Override
            public void handle(DistributionListListener listener) {
                listener.onModify(distributionListModel);
            }
        });
        return distributionListModel;
    }

    @Nullable
    @Override
    public Bitmap getAvatar(@Nullable DistributionListModel model, @Nullable AvatarOptions options) {
        return avatarCacheService.getDistributionListAvatarLow(model);
    }

    @Override
    public void loadAvatarIntoImage(
        @NonNull DistributionListModel model,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        avatarCacheService.loadDistributionListAvatarIntoImage(model, imageView, options, requestManager);
    }

    @Override
    public @ColorInt int getAvatarColor(@Nullable DistributionListModel distributionList) {
        if (distributionList != null) {
            return distributionList.getThemedColor(context);
        }
        return ColorUtil.getInstance().getCurrentThemeGray(context);
    }

    @Override
    public boolean addMemberToDistributionList(DistributionListModel distributionListModel, String identity) {
        DistributionListMemberModel distributionListMemberModel = this.databaseService.getDistributionListMemberModelFactory().getByDistributionListIdAndIdentity(
            distributionListModel.getId(),
            identity
        );
        if (distributionListMemberModel == null) {
            distributionListMemberModel = new DistributionListMemberModel();
        }
        distributionListMemberModel
            .setDistributionListId(distributionListModel.getId())
            .setIdentity(identity)
            .setActive(true);

        if (distributionListMemberModel.getId() > 0) {
            this.databaseService.getDistributionListMemberModelFactory().update(
                distributionListMemberModel
            );
        } else {
            this.databaseService.getDistributionListMemberModelFactory().create(
                distributionListMemberModel
            );
        }
        return true;
    }

    @Override
    public boolean remove(final DistributionListModel distributionListModel) {
        // Obtain some services through service manager
        //
        // Note: We cannot put these services in the constructor due to circular dependencies.
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("Missing serviceManager, cannot remove distribution list");
            return false;
        }
        final ConversationService conversationService;
        try {
            conversationService = serviceManager.getConversationService();
        } catch (ThreemaException e) {
            logger.error("Could not obtain services when removing distribution list", e);
            return false;
        }

        // Remove distribution list members
        if (!this.removeMembers(distributionListModel)) {
            return false;
        }

        // Delete shortcuts
        ShortcutUtil.deleteShareTargetShortcut(getUniqueIdString(distributionListModel));
        ShortcutUtil.deletePinnedShortcut(getUniqueIdString(distributionListModel));

        // Remove conversation
        conversationService.removeFromCache(distributionListModel);

        // Remove conversation tags
        conversationTagService.removeAll(
            ConversationUtil.getDistributionListConversationUid(distributionListModel.getId()),
            TriggerSource.LOCAL
        );

        // Delete distribution list fully from database
        this.databaseService.getDistributionListModelFactory().delete(distributionListModel);

        // Notify listeners
        ListenerManager.distributionListListeners.handle(listener -> listener.onRemove(distributionListModel));

        return true;
    }

    private boolean removeMembers(DistributionListModel distributionListModel) {
        //remove all members first
        this.databaseService.getDistributionListMemberModelFactory().deleteByDistributionListId(
            distributionListModel.getId());

        return true;
    }

    @Override
    public boolean removeAll() {
        //remove all members first
        this.databaseService.getDistributionListMemberModelFactory().deleteAll();

        //...  messages
        this.databaseService.getDistributionListMessageModelFactory().deleteAll();

        //.. remove lists
        this.databaseService.getDistributionListModelFactory().deleteAll();

        return true;
    }

    @Override
    public String[] getDistributionListIdentities(DistributionListModel distributionListModel) {
        List<DistributionListMemberModel> memberModels = this.getDistributionListMembers(distributionListModel);
        if (memberModels != null) {
            String[] res = new String[memberModels.size()];
            for (int n = 0; n < res.length; n++) {
                res[n] = memberModels.get(n).getIdentity();
            }
            return res;
        }

        return null;
    }


    @Override
    public List<DistributionListMemberModel> getDistributionListMembers(DistributionListModel distributionListModel) {
        return this.databaseService.getDistributionListMemberModelFactory().getByDistributionListId(
            distributionListModel.getId()
        );
    }

    @Override
    public List<DistributionListModel> getAll() {
        return this.getAll(null);
    }

    @Override
    public List<DistributionListModel> getAll(DistributionListFilter filter) {
        return this.databaseService.getDistributionListModelFactory().filter(
            filter
        );
    }

    @Override
    public List<ContactModel> getMembers(@Nullable DistributionListModel distributionListModel) {
        List<ContactModel> contactModels = new ArrayList<>();
        if (distributionListModel != null) {
            for (DistributionListMemberModel distributionListMemberModel : this.getDistributionListMembers(distributionListModel)) {
                ContactModel contactModel = this.contactService.getByIdentity(distributionListMemberModel.getIdentity());
                if (contactModel != null) {
                    contactModels.add(contactModel);
                }
            }
        }
        return contactModels;
    }

    @Override
    public String getMembersString(DistributionListModel distributionListModel) {
        StringBuilder builder = new StringBuilder();
        for (ContactModel contactModel : this.getMembers(distributionListModel)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(NameUtil.getDisplayNameOrNickname(contactModel, true));
        }
        return builder.toString();
    }

    @Override
    public DistributionListMessageReceiver createReceiver(DistributionListModel distributionListModel) {
        return new DistributionListMessageReceiver(
            this.databaseService,
            this.contactService,
            distributionListModel,
            this);
    }

    @Override
    public String getUniqueIdString(DistributionListModel distributionListModel) {
        if (distributionListModel != null) {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                messageDigest.update((DISTRIBUTION_LIST_UID_PREFIX + distributionListModel.getId()).getBytes());
                return Base32.encode(messageDigest.digest());
            } catch (NoSuchAlgorithmException e) {
                logger.error("getUniqueIdString failed", e);
            }
        }
        return "";
    }

    @Override
    public void setIsArchived(DistributionListModel distributionListModel, boolean archived) {
        if (distributionListModel != null && distributionListModel.isArchived() != archived) {
            distributionListModel.setArchived(archived);
            save(distributionListModel);

            ListenerManager.distributionListListeners.handle(new ListenerManager.HandleListener<DistributionListListener>() {
                @Override
                public void handle(DistributionListListener listener) {
                    listener.onModify(distributionListModel);
                }
            });
        }
    }

    @Override
    public void bumpLastUpdate(@NonNull DistributionListModel distributionListModel) {
        distributionListModel.setLastUpdate(new Date());
        save(distributionListModel);
        ListenerManager.distributionListListeners.handle(listener -> listener.onModify(distributionListModel));
    }

    private void save(DistributionListModel distributionListModel) {
        this.databaseService.getDistributionListModelFactory().createOrUpdate(
            distributionListModel
        );
    }
}
