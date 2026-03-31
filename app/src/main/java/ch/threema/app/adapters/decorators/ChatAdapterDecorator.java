package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.Spannable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.media3.session.MediaController;
import ch.threema.app.R;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DownloadService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextExtensionsKt;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.DisplayTag;

import static ch.threema.app.utils.MessageUtilKt.getUiContentColor;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

abstract public class ChatAdapterDecorator extends AdapterDecorator implements LinkifyUtil.UnhandledClickHandler {
    private static final Logger logger = getThreemaLogger("ChatAdapterDecorator");

    public interface OnClickElement {
        void onClick(AbstractMessageModel messageModel);
    }

    public interface OnLongClickElement {
        void onLongClick(AbstractMessageModel messageModel);
    }

    public interface OnTouchElement {
        boolean onTouch(MotionEvent motionEvent, AbstractMessageModel messageModel);
    }

    private final AbstractMessageModel messageModel;
    protected final Helper helper;
    private final StateBitmapUtil stateBitmapUtil;

    protected OnClickElement onClickElement = null;
    private OnLongClickElement onLongClickElement = null;
    private OnTouchElement onTouchElement = null;
    @NonNull
    protected final ChatAdapterDecoratorListener chatAdapterDecoratorListener;
    @NonNull
    protected final LinkifyUtil.LinkifyListener linkifyListener;
    private long durationS = 0;
    private CharSequence datePrefix = "";
    protected String dateContentDescriptionPrefix = "";

    private long groupId = 0L;
    protected Map<String, Integer> identityColors = null;
    @Nullable
    protected String filterString;

    // whether this message should be displayed as a continuation of a previous message by the same sender
    private boolean isGroupedMessage = true;

    public static class ContactCache {
        public String identity;
        public String displayName;
        public Bitmap avatar;
        public ContactModel contactModel;
    }

    public static class Helper {
        private final String myIdentity;
        private final MessageService messageService;
        private final UserService userService;
        private final ContactService contactService;
        private final FileService fileService;
        private final BallotService ballotService;
        private final ThumbnailCache thumbnailCache;
        private final PreferenceService preferenceService;
        private final DownloadService downloadService;
        private final LicenseService licenseService;
        private MessageReceiver messageReceiver;
        private int thumbnailWidth;
        protected int regularColor;
        private final Map<String, ContactCache> contacts = new HashMap<>();
        private final int maxBubbleTextLength;
        private final int maxQuoteTextLength;
        private final ListenableFuture<MediaController> mediaControllerFuture;

        public Helper(
            String myIdentity,
            MessageService messageService,
            UserService userService,
            ContactService contactService,
            FileService fileService,
            BallotService ballotService,
            ThumbnailCache thumbnailCache,
            PreferenceService preferenceService,
            DownloadService downloadService,
            LicenseService licenseService,
            MessageReceiver messageReceiver,
            int thumbnailWidth,
            int regularColor,
            int maxBubbleTextLength,
            int maxQuoteTextLength,
            ListenableFuture<MediaController> mediaControllerFuture) {
            this.myIdentity = myIdentity;
            this.messageService = messageService;
            this.userService = userService;
            this.contactService = contactService;
            this.fileService = fileService;
            this.ballotService = ballotService;
            this.thumbnailCache = thumbnailCache;
            this.preferenceService = preferenceService;
            this.downloadService = downloadService;
            this.licenseService = licenseService;
            this.messageReceiver = messageReceiver;
            this.thumbnailWidth = thumbnailWidth;
            this.regularColor = regularColor;
            this.maxBubbleTextLength = maxBubbleTextLength;
            this.maxQuoteTextLength = maxQuoteTextLength;
            this.mediaControllerFuture = mediaControllerFuture;
        }

        public int getThumbnailWidth() {
            return thumbnailWidth;
        }

        public ThumbnailCache getThumbnailCache() {
            return thumbnailCache;
        }

        public FileService getFileService() {
            return fileService;
        }

        public UserService getUserService() {
            return userService;
        }

        public ContactService getContactService() {
            return contactService;
        }

        public MessageService getMessageService() {
            return messageService;
        }

        public PreferenceService getPreferenceService() {
            return preferenceService;
        }

        public DownloadService getDownloadService() {
            return downloadService;
        }

        public LicenseService getLicenseService() {
            return licenseService;
        }

        public String getMyIdentity() {
            return myIdentity;
        }

        public BallotService getBallotService() {
            return ballotService;
        }

        public Map<String, ContactCache> getContactCache() {
            return contacts;
        }

        public MessageReceiver getMessageReceiver() {
            return messageReceiver;
        }

        public void setThumbnailWidth(int preferredThumbnailWidth) {
            thumbnailWidth = preferredThumbnailWidth;
        }

        public int getMaxBubbleTextLength() {
            return maxBubbleTextLength;
        }

        public int getMaxQuoteTextLength() {
            return maxQuoteTextLength;
        }

        public void setMessageReceiver(MessageReceiver messageReceiver) {
            this.messageReceiver = messageReceiver;
        }

        public ListenableFuture<MediaController> getMediaControllerFuture() {
            return this.mediaControllerFuture;
        }
    }

    public ChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper
    ) {
        this.messageModel = messageModel;
        this.chatAdapterDecoratorListener = chatAdapterDecoratorListener;
        this.linkifyListener = linkifyListener;
        this.helper = helper;
        stateBitmapUtil = StateBitmapUtil.getInstance();
    }

    public void setGroupMessage(long groupId, Map<String, Integer> identityColors) {
        this.groupId = groupId;
        this.identityColors = identityColors;
    }

    public void setOnClickElement(OnClickElement onClickElement) {
        this.onClickElement = onClickElement;
    }

    public void setOnLongClickElement(OnLongClickElement onClickElement) {
        onLongClickElement = onClickElement;
    }

    public void setOnTouchElement(OnTouchElement onTouchElement) {
        this.onTouchElement = onTouchElement;
    }

    @Override
    public void onUnhandledClick(@NonNull AbstractMessageModel messageModel) {
        if (onClickElement != null) {
            onClickElement.onClick(messageModel);
        }
    }

    final public void setFilter(@Nullable String filterString) {
        this.filterString = filterString;
    }

    /**
     * Is necessary because depending on the message model, we have to use a different color state list
     *
     * @param contentColor The color-state-list that will be applied to all {@code TextView} instances in {@code ComposeMessageHolder}
     */
    @MustBeInvokedByOverriders
    protected void applyContentColor(
        final @NonNull ComposeMessageHolder viewHolder,
        final @NonNull ColorStateList contentColor
    ) {
        if (viewHolder.bodyTextView != null) {
            viewHolder.bodyTextView.setTextColor(contentColor);
        }
        if (viewHolder.secondaryTextView != null) {
            viewHolder.secondaryTextView.setTextColor(contentColor);
        }
        if (viewHolder.tertiaryTextView != null) {
            viewHolder.tertiaryTextView.setTextColor(contentColor);
        }
        if (viewHolder.size != null) {
            viewHolder.size.setTextColor(contentColor);
        }
        if (viewHolder.senderName != null) {
            viewHolder.senderName.setTextColor(contentColor);
        }
        if (viewHolder.dateView != null) {
            viewHolder.dateView.setTextColor(contentColor);
        }
        if (viewHolder.editedText != null) {
            viewHolder.editedText.setTextColor(contentColor);
        }
    }

    @Override
    final protected void configure(final AbstractListItemHolder abstractViewHolder, Context context, int position) {
        if (!(abstractViewHolder instanceof ComposeMessageHolder) || abstractViewHolder.position != position) {
            return;
        }

        boolean isUserMessage = !getMessageModel().isStatusMessage()
            && getMessageModel().getType() != MessageType.STATUS
            && getMessageModel().getType() != MessageType.GROUP_CALL_STATUS;

        String identity = messageModel.isOutbox()
            ? helper.getMyIdentity()
            : messageModel.getIdentity();

        final @NonNull ComposeMessageHolder viewHolder = (ComposeMessageHolder) abstractViewHolder;

        applyContentColor(viewHolder, getUiContentColor(getMessageModel(), context));

        //configure the chat message
        configureChatMessage(viewHolder, context, position);

        if (isUserMessage) {
            if (!messageModel.isOutbox() && groupId > 0) {

                ContactCache contactCache = helper.getContactCache().get(identity);
                if (contactCache == null) {
                    ContactModel contactModel = helper.getContactService().getByIdentity(messageModel.getIdentity());
                    contactCache = new ContactCache();
                    contactCache.displayName = NameUtil.getContactDisplayNameOrNickname(
                        contactModel,
                        true,
                        helper.preferenceService.getContactNameFormat()
                    );
                    contactCache.avatar = helper.getContactService().getAvatar(messageModel.getIdentity(), false);

                    contactCache.contactModel = contactModel;
                    helper.getContactCache().put(identity, contactCache);
                }

                if (viewHolder.senderView != null) {
                    if (isGroupedMessage) {
                        viewHolder.senderView.setVisibility(View.VISIBLE);
                        viewHolder.senderName.setText(contactCache.displayName);

                        if (identityColors != null && identityColors.containsKey(identity)) {
                            viewHolder.senderName.setTextColor(identityColors.get(identity));
                        } else {
                            viewHolder.senderName.setTextColor(helper.regularColor);
                        }
                    } else {
                        // hide sender name in grouped messages
                        viewHolder.senderView.setVisibility(View.GONE);
                    }
                }

                if (viewHolder.avatarView != null) {
                    if (isGroupedMessage) {
                        viewHolder.avatarView.setImageBitmap(contactCache.avatar);
                        viewHolder.avatarView.setVisibility(View.VISIBLE);
                        if (contactCache.contactModel != null) {
                            viewHolder.avatarView.setBadgeVisible(helper.getContactService().showBadge(contactCache.contactModel));
                        }
                    } else {
                        // hide avatar in grouped messages
                        viewHolder.avatarView.setVisibility(View.INVISIBLE);
                    }
                }
            } else {
                if (viewHolder.avatarView != null) {
                    viewHolder.avatarView.setVisibility(View.GONE);
                }
                if (viewHolder.senderView != null) {
                    viewHolder.senderView.setVisibility(View.GONE);
                }
            }

            @Nullable CharSequence displayDate = MessageUtil.getDisplayDate(
                context,
                messageModel.getPostedAt(),
                messageModel.isOutbox(),
                messageModel.getModifiedAt(),
                true
            );
            if (displayDate == null) {
                displayDate = "";
            }

            CharSequence contentDescription;

            if (!TestUtil.isBlankOrNull(datePrefix)) {
                contentDescription = context.getString(R.string.state_dialog_modified) + ": " + displayDate;
                if (messageModel.isOutbox()) {
                    displayDate = TextUtils.concat(datePrefix, " | " + displayDate);
                } else {
                    displayDate = TextUtils.concat(displayDate + " | ", datePrefix);
                }
            } else {
                contentDescription = displayDate;
            }
            if (viewHolder.dateView != null) {
                viewHolder.dateView.setText(displayDate);
                viewHolder.dateView.setContentDescription(contentDescription);
            }

            if (viewHolder.datePrefixIcon != null) {
                viewHolder.datePrefixIcon.setVisibility(durationS > 0L ? View.VISIBLE : View.GONE);
            }

            if (viewHolder.starredIcon != null) {
                viewHolder.starredIcon.setVisibility((messageModel.getDisplayTags() & DisplayTag.DISPLAY_TAG_STARRED) == DisplayTag.DISPLAY_TAG_STARRED ? View.VISIBLE : View.GONE);
            }

            if (viewHolder.deliveredIndicator != null) {
                stateBitmapUtil.setStateDrawable(context, messageModel, viewHolder.deliveredIndicator, null);
            }

            if (viewHolder.editedText != null) {
                viewHolder.editedText.setVisibility(messageModel.getEditedAt() != null ? View.VISIBLE : View.GONE);
            }

            if (viewHolder.controller != null) {
                viewHolder.controller.setIsUsedForOutboxMessage(getMessageModel().isOutbox());
            }
        }
    }

    public Spannable highlightMatches(@NonNull Context context, @Nullable CharSequence fullText, @Nullable String filterText) {
        return TextExtensionsKt.highlightMatches(
            fullText,
            context,
            filterText,
            true,
            false
        );
    }

    private CharSequence formatTextString(@NonNull Context context, @Nullable String string, String filterString) {
        return formatTextString(context, string, filterString, -1);
    }

    protected CharSequence formatTextString(@NonNull Context context, @Nullable String string, String filterString, int maxLength) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }

        if (maxLength > 0 && string.length() > maxLength) {
            return highlightMatches(context, string.substring(0, maxLength - 1), filterString);
        }
        return highlightMatches(context, string, filterString);
    }

    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, int position) {
        if (holder.attachmentImage instanceof ShapeableImageView) {
            ShapeAppearanceModel shapeAppearanceModel = new ShapeAppearanceModel.Builder()
                .setAllCornerSizes(ImageViewUtil.getCornerRadius(context))
                .build();
            ((ShapeableImageView) holder.attachmentImage).setShapeAppearanceModel(shapeAppearanceModel);
        }
    }

    protected void setDatePrefix(String prefix) {
        datePrefix = prefix;
    }

    protected void setDuration(long durationS) {
        this.durationS = durationS;
    }

    protected MessageService getMessageService() {
        return helper.getMessageService();
    }

    protected FileService getFileService() {
        return helper.getFileService();
    }

    protected int getThumbnailWidth() {
        return helper.getThumbnailWidth();
    }

    protected ThumbnailCache getThumbnailCache() {
        return helper.getThumbnailCache();
    }

    protected AbstractMessageModel getMessageModel() {
        return messageModel;
    }

    protected PreferenceService getPreferenceService() {
        return helper.getPreferenceService();
    }

    protected UserService getUserService() {
        return helper.getUserService();
    }

    protected ContactService getContactService() {
        return helper.getContactService();
    }

    protected void setOnClickListener(final View.OnClickListener onViewClickListener, View view) {
        if (view != null) {
            view.setOnClickListener(v -> {
                if (onViewClickListener != null && !chatAdapterDecoratorListener.isActionModeEnabled()) {
                    // do not propagate click if actionMode (selection mode) is enabled in parent
                    onViewClickListener.onClick(v);
                }
                if (onClickElement != null) {
                    //propagate event to parents
                    onClickElement.onClick(getMessageModel());
                }
            });

            // propagate long click listener
            view.setOnLongClickListener(v -> {
                if (onLongClickElement != null) {
                    onLongClickElement.onLongClick(getMessageModel());
                }
                return false;
            });

            // propagate touch listener
            view.setOnTouchListener((arg0, event) -> {
                if (onTouchElement != null) {
                    return onTouchElement.onTouch(event, getMessageModel());
                }
                return false;
            });
        }
    }

    void setStickerBackground(ComposeMessageHolder holder) {
        holder.messageBlockView.setCardBackgroundColor(AppCompatResources.getColorStateList(holder.messageBlockView.getContext(), R.color.bubble_sticker_colorstatelist));
    }

    void setDefaultBackground(ComposeMessageHolder holder) {
        if (holder.messageBlockView.getCardBackgroundColor().getDefaultColor() == Color.TRANSPARENT) {
            int colorStateListRes;

            if (getMessageModel().isOutbox() && !(getMessageModel() instanceof DistributionListMessageModel)) {
                // outgoing
                colorStateListRes = R.color.bubble_send_colorstatelist;
            } else {
                // incoming
                colorStateListRes = R.color.bubble_receive_colorstatelist;
            }
            holder.messageBlockView.setCardBackgroundColor(AppCompatResources.getColorStateList(holder.messageBlockView.getContext(), colorStateListRes));

            logger.debug("*** setDefaultBackground");
        }
    }

    /**
     * Set whether this message should be displayed as the continuation of a previous message by the same sender
     *
     * @param grouped If this is a grouped message, following another message by the same sender
     */
    public void setGroupedMessage(boolean grouped) {
        isGroupedMessage = grouped;
    }

    /**
     * Setup "Tap to resend" UI
     *
     * @param holder ComposeMessageHolder
     */
    protected void setupResendStatus(ComposeMessageHolder holder) {
        if (holder.tapToResend != null) {
            if (getMessageModel() != null &&
                getMessageModel().isOutbox() &&
                (getMessageModel().getState() == MessageState.FS_KEY_MISMATCH ||
                    getMessageModel().getState() == MessageState.SENDFAILED)) {
                holder.tapToResend.setVisibility(View.VISIBLE);
                holder.dateView.setVisibility(View.GONE);
            } else {
                holder.tapToResend.setVisibility(View.GONE);
                holder.dateView.setVisibility(View.VISIBLE);
            }
        }
    }

    protected void configureBodyText(@NonNull ComposeMessageHolder holder, @Nullable String caption) {
        if (!TestUtil.isEmptyOrNull(caption)) {
            holder.bodyTextView.setText(formatTextString(holder.bodyTextView.getContext(), caption, filterString));
            // remove movement method. Otherwise clicks on the text are not handled correctly
            holder.bodyTextView.setMovementMethod(null);
            LinkifyUtil.getInstance().linkify(
                holder.bodyTextView,
                getMessageModel(),
                /* includePhoneNumbers = */ true,
                /* unhandledClickHandler = */ this,
                /* linkifyListener = */ linkifyListener
            );

            showHide(holder.bodyTextView, true);
        } else {
            showHide(holder.bodyTextView, false);
        }
    }

    protected void propagateControllerRetryClickToParent() {
        if (
            getMessageModel().getState() == MessageState.FS_KEY_MISMATCH ||
                getMessageModel().getState() == MessageState.SENDFAILED
        ) {
            propagateControllerClickToParent();
        }
    }

    protected void propagateControllerClickToParent() {
        if (onClickElement != null) {
            onClickElement.onClick(getMessageModel());
        }
    }

    @Override
    protected boolean isInChoiceMode() {
        return chatAdapterDecoratorListener.isInChoiceMode();
    }
}
