package ch.threema.app.ui;

import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.services.AvatarService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.NameUtil;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.group.GroupModelOld;

public class AvatarListItemUtil {

    public static void loadAvatar(
        @NonNull final ConversationModel conversationModel,
        @NonNull final ContactService contactService,
        @NonNull final GroupService groupService,
        @NonNull final DistributionListService distributionListService,
        @NonNull final ContactNameFormat contactNameFormat,
        @NonNull AvatarListItemHolder holder,
        @NonNull RequestManager requestManager
    ) {

        // load avatars asynchronously
        ImageView avatarView = holder.avatarView.getAvatarView();
        if (conversationModel.isContactConversation()) {
            holder.avatarView.setContentDescription(
                ThreemaApplication.getAppContext().getString(
                    R.string.edit_type_content_description,
                    ThreemaApplication.getAppContext().getString(R.string.mime_contact),
                    NameUtil.getContactDisplayNameOrNickname(conversationModel.getContact(), true, contactNameFormat)
                )
            );
            ContactModel contact = conversationModel.getContact();
            String identity = contact != null
                ? contact.getIdentity()
                : null;
            if (identity != null) {
                contactService.loadAvatarIntoImage(
                    identity,
                    avatarView,
                    AvatarOptions.PRESET_DEFAULT_FALLBACK,
                    requestManager
                );
            }
        } else if (conversationModel.isGroupConversation()) {
            holder.avatarView.setContentDescription(
                ThreemaApplication.getAppContext().getString(
                    R.string.edit_type_content_description,
                    ThreemaApplication.getAppContext().getString(R.string.group),
                    NameUtil.getGroupDisplayName(conversationModel.getGroup(), groupService, contactNameFormat)
                )
            );
            groupService.loadAvatarIntoImage(
                conversationModel.getGroup(),
                avatarView,
                AvatarOptions.PRESET_DEFAULT_FALLBACK,
                requestManager
            );
        } else if (conversationModel.isDistributionListConversation()) {
            holder.avatarView.setContentDescription(
                ThreemaApplication.getAppContext().getString(
                    R.string.edit_type_content_description,
                    ThreemaApplication.getAppContext().getString(R.string.distribution_list),
                    NameUtil.getDistributionListDisplayName(conversationModel.getDistributionList(), distributionListService, contactNameFormat)
                )
            );
            distributionListService.loadAvatarIntoImage(
                conversationModel.getDistributionList().getId(),
                avatarView,
                AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE,
                requestManager
            );
        }

        // Set work badge
        boolean isWork = contactService.showBadge(conversationModel.getContact());
        holder.avatarView.setBadgeVisible(isWork);
    }

    public static <S> void loadAvatar(
        final S subject,
        final AvatarService<S> avatarService,
        AvatarListItemHolder holder,
        @NonNull RequestManager requestManager
    ) {

        // do nothing
        if (subject == null || avatarService == null || holder == null || holder.avatarView == null) {
            return;
        }

        if (subject instanceof String) {
            holder.avatarView.setBadgeVisible(((ContactService) avatarService).showBadge((String) subject));
        } else {
            holder.avatarView.setBadgeVisible(false);
        }

        AvatarOptions options;
        if (subject instanceof String) {
            options = AvatarOptions.PRESET_DEFAULT_FALLBACK;
        } else if (subject instanceof GroupModelOld) {
            options = AvatarOptions.PRESET_DEFAULT_FALLBACK;
        } else {
            options = AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE;
        }

        avatarService.loadAvatarIntoImage(
            subject,
            holder.avatarView.getAvatarView(),
            options,
            requestManager
        );

        holder.avatarView.setVisibility(View.VISIBLE);
    }

}
