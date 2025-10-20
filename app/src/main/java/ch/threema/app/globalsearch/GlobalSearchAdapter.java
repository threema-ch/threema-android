/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.globalsearch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.text.SpannableStringBuilder;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiImageSpan;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextExtensionsKt;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.BallotDataModel;

import static ch.threema.app.utils.MessageUtilKt.getUiContentColor;

public class GlobalSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GlobalSearchAdapter");
    private static final String FLOW_CHARACTER = "\u25BA\uFE0E"; // "►"

    private GroupService groupService;
    private ContactService contactService;
    private BallotService ballotService;
    @Nullable
    private ConversationCategoryService conversationCategoryService;

    private final Context context;
    private OnClickItemListener onClickItemListener;
    private final SparseBooleanArray checkedItems = new SparseBooleanArray();
    private String queryString;
    private final int snippetThreshold;
    private List<AbstractMessageModel> messageModels; // Cached copy of AbstractMessageModels
    private final @LayoutRes int itemLayout;
    private final ColorStateList colorStateListSend, colorStateListReceive;
    private final @NonNull RequestManager requestManager;

    private static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView dateView;
        private final TextView snippetView;
        @Nullable
        private final TextView deletedPlaceholder; // will be null for layouts other than item_starred_messages
        private final ImageView thumbnailView;
        private final MaterialCardView messageBlock;
        AvatarListItemHolder avatarListItemHolder;

        private ViewHolder(final View itemView) {
            super(itemView);

            titleView = itemView.findViewById(R.id.name);
            dateView = itemView.findViewById(R.id.date);
            snippetView = itemView.findViewById(R.id.snippet);
            deletedPlaceholder = itemView.findViewById(R.id.deleted_placeholder);
            AvatarView avatarView = itemView.findViewById(R.id.avatar_view);
            thumbnailView = itemView.findViewById(R.id.thumbnail_view);
            messageBlock = itemView.findViewById(R.id.message_block);

            avatarListItemHolder = new AvatarListItemHolder();
            avatarListItemHolder.avatarView = avatarView;
        }

        protected boolean getHasBubbleBackground() {
            return this.messageBlock != null;
        }
    }

    public GlobalSearchAdapter(
        @NonNull Context context,
        @NonNull RequestManager requestManager,
        @LayoutRes int itemLayout,
        int snippetThreshold
    ) {
        this.context = context;
        this.requestManager = requestManager;
        this.itemLayout = itemLayout;
        this.snippetThreshold = snippetThreshold;

        try {
            this.groupService = ThreemaApplication.requireServiceManager().getGroupService();
            this.contactService = ThreemaApplication.requireServiceManager().getContactService();
            this.ballotService = ThreemaApplication.requireServiceManager().getBallotService();
            this.conversationCategoryService = ThreemaApplication.requireServiceManager().getConversationCategoryService();
        } catch (Exception e) {
            logger.error("Unable to get Services", e);
        }

        this.colorStateListSend = ContextCompat.getColorStateList(context, R.color.bubble_send_colorstatelist);
        this.colorStateListReceive = ContextCompat.getColorStateList(context, R.color.bubble_receive_colorstatelist);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
            .inflate(itemLayout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ViewHolder viewHolder = (ViewHolder) holder;

        if (messageModels != null) {
            AbstractMessageModel messageModel = getItem(position);

            if (viewHolder.getHasBubbleBackground()) {
                if (messageModel.isOutbox()) {
                    viewHolder.messageBlock.setCardBackgroundColor(colorStateListSend);
                } else {
                    viewHolder.messageBlock.setCardBackgroundColor(colorStateListReceive);
                }

                var uiContentColor = getUiContentColor(messageModel, context);
                viewHolder.titleView.setTextColor(uiContentColor);
                viewHolder.snippetView.setTextColor(uiContentColor);
                viewHolder.dateView.setTextColor(uiContentColor);
            }

            viewHolder.snippetView.setVisibility(View.VISIBLE);
            if (viewHolder.deletedPlaceholder != null) {
                viewHolder.deletedPlaceholder.setVisibility(View.GONE);
            }

            final @NonNull String uid = messageModel instanceof GroupMessageModel
                ? GroupUtil.getUniqueIdString(((GroupMessageModel) messageModel).getGroupId())
                : ContactUtil.getUniqueIdString(messageModel.getIdentity());
            if (conversationCategoryService == null || conversationCategoryService.isPrivateChat(uid)) {
                viewHolder.dateView.setText("");
                viewHolder.thumbnailView.setVisibility(View.GONE);
                viewHolder.titleView.setText("");
                viewHolder.snippetView.setText(R.string.private_chat_subject);
                viewHolder.avatarListItemHolder.avatarView.setVisibility(View.INVISIBLE);
                viewHolder.avatarListItemHolder.avatarView.setBadgeVisible(false);
            } else if (messageModel.isDeleted()) {
                // deleted placeholder for starred items - global search will never find deleted items
                initDeletedViewHolder(viewHolder, messageModel);
            } else {
                if (messageModel instanceof GroupMessageModel) {
                    final GroupModel groupModel = groupService.getById(((GroupMessageModel) messageModel).getGroupId());
                    AvatarListItemUtil.loadAvatar(
                        groupModel,
                        groupService,
                        viewHolder.avatarListItemHolder,
                        requestManager
                    );

                    viewHolder.titleView.setText(getTitle((GroupMessageModel) messageModel, groupModel));
                } else {
                    AvatarListItemUtil.loadAvatar(
                        messageModel.getIdentity(),
                        contactService,
                        viewHolder.avatarListItemHolder,
                        requestManager
                    );

                    final ContactModel contactModel = this.contactService.getByIdentity(messageModel.getIdentity());
                    viewHolder.titleView.setText(getTitle(messageModel, contactModel));
                }
                viewHolder.dateView.setText(LocaleUtil.formatDateRelative(messageModel.getCreatedAt().getTime()));

                if (messageModel.getType() == MessageType.FILE && messageModel.getFileData().isDownloaded()) {
                    loadThumbnail(messageModel, viewHolder);
                    viewHolder.thumbnailView.setVisibility(View.VISIBLE);
                } else if (messageModel.getType() == MessageType.BALLOT) {
                    viewHolder.thumbnailView.setImageResource(R.drawable.ic_outline_rule);
                    viewHolder.thumbnailView.setVisibility(View.VISIBLE);
                    setupPlaceholder(viewHolder);
                } else {
                    viewHolder.thumbnailView.setVisibility(View.GONE);
                }

                setSnippetToTextView(messageModel, viewHolder);

                if (viewHolder.snippetView.getText() == null || viewHolder.snippetView.getText().length() == 0) {
                    if (messageModel.getType() == MessageType.FILE) {
                        String mimeString = messageModel.getFileData().getMimeType();
                        if (!TestUtil.isEmptyOrNull(mimeString)) {
                            viewHolder.snippetView.setText(MimeUtil.getMimeDescription(context, messageModel.getFileData().getMimeType()));
                        } else {
                            viewHolder.snippetView.setText("");
                        }
                    }
                }
            }

            if (this.onClickItemListener != null) {
                viewHolder.itemView.setOnClickListener(v -> onClickItemListener.onClick(messageModel, viewHolder.itemView, position));
                viewHolder.itemView.setOnLongClickListener(v -> onClickItemListener.onLongClick(messageModel, viewHolder.itemView, position));
            }

            ((CheckableRelativeLayout) viewHolder.itemView).setChecked(checkedItems.get(position));
        } else {
            // Covers the case of data not being ready yet.
            viewHolder.titleView.setText("");
            viewHolder.dateView.setText("");
            viewHolder.snippetView.setText("");
        }
    }

    private void initDeletedViewHolder(@NonNull ViewHolder viewHolder, AbstractMessageModel message) {
        viewHolder.snippetView.setVisibility(View.INVISIBLE);
        viewHolder.snippetView.setText("");

        if (viewHolder.deletedPlaceholder != null) {
            viewHolder.deletedPlaceholder.setVisibility(View.VISIBLE);
            viewHolder.deletedPlaceholder.setText(R.string.message_was_deleted);
            if (viewHolder.getHasBubbleBackground()) {
                assert viewHolder.deletedPlaceholder != null;
                viewHolder.deletedPlaceholder.setTextColor(getUiContentColor(message, viewHolder.deletedPlaceholder.getContext()));
            }
        }

        viewHolder.dateView.setText(LocaleUtil.formatDateRelative(message.getCreatedAt().getTime()));

        if (message instanceof GroupMessageModel) {
            final GroupModel groupModel = groupService.getById(((GroupMessageModel) message).getGroupId());
            viewHolder.titleView.setText(getTitle((GroupMessageModel) message, groupModel));
        } else {
            final ContactModel contactModel = this.contactService.getByIdentity(message.getIdentity());
            viewHolder.titleView.setText(getTitle(message, contactModel));
        }
    }

    private String getTitle(@NonNull AbstractMessageModel messageModel, @Nullable ContactModel contactModel) {
        String name = NameUtil.getDisplayNameOrNickname(context, messageModel, contactService);
        return messageModel.isOutbox() ?
            name + " " + FLOW_CHARACTER + " " + NameUtil.getDisplayNameOrNickname(contactModel, true) :
            name;
    }

    @NonNull
    private String getTitle(@NonNull GroupMessageModel messageModel, GroupModel groupModel) {
        final ContactModel contactModel = messageModel.isOutbox() ? this.contactService.getMe() : this.contactService.getByIdentity(messageModel.getIdentity());
        String groupName = NameUtil.getDisplayName(groupModel, groupService);
        return String.format("%s %s %s", NameUtil.getDisplayNameOrNickname(contactModel, true), FLOW_CHARACTER, groupName);
    }

    private void loadThumbnail(@NonNull AbstractMessageModel messageModel, ViewHolder viewHolder) {
        @DrawableRes int placeholderIcon;

        if (messageModel.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE) {
            placeholderIcon = R.drawable.ic_keyboard_voice_outline;
        } else if (messageModel.getType() == MessageType.FILE) {
            placeholderIcon = IconUtil.getMimeIcon(messageModel.getFileData().getMimeType());
        } else {
            placeholderIcon = IconUtil.getMimeIcon("application/x-error");
        }

        Glide.with(context)
            .asBitmap()
            .load(messageModel)
            .transition(BitmapTransitionOptions.withCrossFade())
            .centerCrop()
            .error(placeholderIcon)
            .addListener(new RequestListener<>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Bitmap> target, boolean isFirstResource) {
                    setupPlaceholder(viewHolder);
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Bitmap resource, @NonNull Object model, Target<Bitmap> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                    viewHolder.thumbnailView.clearColorFilter();
                    viewHolder.thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    return false;
                }
            })
            .into(viewHolder.thumbnailView);
    }

    private void setupPlaceholder(@NonNull ViewHolder viewHolder) {
        viewHolder.thumbnailView.setBackgroundColor(getThumbnailViewBackgroundColor());
        viewHolder.thumbnailView.setColorFilter(getThumbnailViewIconTintColor(), PorterDuff.Mode.SRC_IN);
        viewHolder.thumbnailView.setScaleType(ImageView.ScaleType.CENTER);
    }

    @ColorInt
    private int getThumbnailViewBackgroundColor() {
        return ConfigUtils.getColorFromAttribute(context, R.attr.colorSecondary);
    }

    @ColorInt
    private int getThumbnailViewIconTintColor() {
        return ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSecondary);
    }

    /**
     * Returns a snippet containing the first occurrence of needle in fullText
     * Splits text on emoji boundary
     * Note: the match is case-insensitive
     *
     * @param fullText   Full text
     * @param needle     Text to search for
     * @param viewHolder ItemHolder containing a textview
     * @return Snippet containing the match with a trailing ellipsis if the match is located beyond the first snippetThreshold characters
     */
    private CharSequence getSnippet(@NonNull String fullText, @Nullable String needle, ViewHolder viewHolder) {
        if (!TestUtil.isEmptyOrNull(needle)) {
            int firstMatch = fullText.toLowerCase().indexOf(needle);
            if (firstMatch > snippetThreshold) {
                int snippetStart = firstMatch > (snippetThreshold + 3) ? firstMatch - (snippetThreshold + 3) : 0;

                for (int i = snippetStart; i < firstMatch; i++) {
                    if (Character.isWhitespace(fullText.charAt(i))) {
                        return "…" + fullText.substring(i + 1);
                    }
                }

                SpannableStringBuilder emojified = (SpannableStringBuilder) EmojiMarkupUtil.getInstance().addTextSpans(context, fullText, viewHolder.snippetView, true, false, false, false);

                int transitionStart = emojified.nextSpanTransition(firstMatch - snippetThreshold, firstMatch, EmojiImageSpan.class);
                if (transitionStart == firstMatch) {
                    // there are no spans here
                    return "…" + emojified.subSequence(firstMatch - snippetThreshold, emojified.length());
                } else {
                    return "…" + emojified.subSequence(transitionStart, emojified.length());
                }
            }
        }
        return EmojiMarkupUtil.getInstance().addTextSpans(context, fullText, viewHolder.snippetView, false, false, false, false);
    }


    public void setMessageModels(List<AbstractMessageModel> messageModels) {
        this.messageModels = messageModels;
        notifyDataSetChanged();
    }

    private void setSnippetToTextView(@NonNull AbstractMessageModel messageModel, ViewHolder viewHolder) {
        CharSequence snippetText = null;
        MessageType type = messageModel.getType();
        if (type == null) {
            logger.warn("Message type is null");
            return;
        }
        switch (type) {
            case FILE:
                // fallthrough
            case IMAGE:
                if (!TestUtil.isEmptyOrNull(messageModel.getCaption())) {
                    snippetText = getSnippet(messageModel.getCaption(), this.queryString, viewHolder);
                }
                break;
            case TEXT:
                if (!TestUtil.isEmptyOrNull(messageModel.getBody())) {
                    snippetText = getSnippet(messageModel.getBody(), this.queryString, viewHolder);
                }
                break;
            case BALLOT:
                snippetText = context.getString(R.string.attach_ballot);
                if (!TestUtil.isEmptyOrNull(messageModel.getBody())) {
                    BallotDataModel ballotData = messageModel.getBallotData();
                    final BallotModel ballotModel = ballotService.get(ballotData.getBallotId());
                    if (ballotModel != null) {
                        snippetText = getSnippet(ballotModel.getName(), this.queryString, viewHolder);
                    }
                }
                break;
            case LOCATION:
                final @NonNull LocationDataModel locationDataModel = messageModel.getLocationData();
                if (locationDataModel.poi == null) {
                    break;
                }
                final @Nullable String poiSnippetForSearch = locationDataModel.poi.getSnippetForSearchOrNull();
                if (poiSnippetForSearch != null) {
                    snippetText = getSnippet(poiSnippetForSearch, this.queryString, viewHolder);
                }
                break;
            default:
                // Audio and Video Messages don't have text or captions
                break;
        }

        if (snippetText != null) {
            viewHolder.snippetView.setText(
                TextExtensionsKt.highlightMatches(
                    snippetText,
                    context,
                    this.queryString,
                    true,
                    false
                )
            );
        } else {
            viewHolder.snippetView.setText(null);
        }
    }

    private AbstractMessageModel getItem(int position) {
        return messageModels.get(position);
    }

    // getItemCount() is called many times, and when it is first called,
    // messageModels has not been updated (means initially, it's null, and we can't return null).
    @Override
    public int getItemCount() {
        if (messageModels != null) {
            return messageModels.size();
        } else {
            return 0;
        }
    }

    public void toggleChecked(int pos) {
        if (checkedItems.get(pos, false)) {
            checkedItems.delete(pos);
        } else {
            checkedItems.put(pos, true);
        }
        notifyItemChanged(pos);
    }

    public int getCheckedItemsCount() {
        return checkedItems.size();
    }

    public List<AbstractMessageModel> getCheckedItems() {
        List<AbstractMessageModel> items = new ArrayList<>(checkedItems.size());
        for (int i = 0; i < checkedItems.size(); i++) {
            items.add(messageModels.get(checkedItems.keyAt(i)));
        }
        return items;
    }

    public void clearCheckedItems() {
        checkedItems.clear();
        notifyDataSetChanged();
    }

    public void setOnClickItemListener(OnClickItemListener onClickItemListener) {
        this.onClickItemListener = onClickItemListener;
    }

    public void onQueryChanged(String queryText) {
        this.queryString = queryText;
    }

    public interface OnClickItemListener {
        void onClick(AbstractMessageModel messageModel, View view, int position);

        default boolean onLongClick(AbstractMessageModel messageModel, View view, int position) {
            return false;
        }
    }
}
