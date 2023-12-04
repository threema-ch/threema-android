/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.app.adapters;

import static ch.threema.domain.protocol.csp.messages.file.FileData.RENDERING_DEFAULT;

import android.animation.LayoutTransition;
import android.content.Context;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ListView;

import androidx.annotation.IntDef;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.media3.session.MediaController;

import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;

import ch.threema.app.R;
import ch.threema.app.adapters.decorators.AnimGifChatAdapterDecorator;
import ch.threema.app.adapters.decorators.AudioChatAdapterDecorator;
import ch.threema.app.adapters.decorators.BallotChatAdapterDecorator;
import ch.threema.app.adapters.decorators.ChatAdapterDecorator;
import ch.threema.app.adapters.decorators.DateSeparatorChatAdapterDecorator;
import ch.threema.app.adapters.decorators.FileChatAdapterDecorator;
import ch.threema.app.adapters.decorators.FirstUnreadChatAdapterDecorator;
import ch.threema.app.adapters.decorators.ForwardSecurityStatusChatAdapterDecorator;
import ch.threema.app.adapters.decorators.GroupCallStatusDataChatAdapterDecorator;
import ch.threema.app.adapters.decorators.GroupStatusAdapterDecorator;
import ch.threema.app.adapters.decorators.ImageChatAdapterDecorator;
import ch.threema.app.adapters.decorators.LocationChatAdapterDecorator;
import ch.threema.app.adapters.decorators.StatusChatAdapterDecorator;
import ch.threema.app.adapters.decorators.TextChatAdapterDecorator;
import ch.threema.app.adapters.decorators.VideoChatAdapterDecorator;
import ch.threema.app.adapters.decorators.VoipStatusDataChatAdapterDecorator;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DownloadService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.messageplayer.MessagePlayerService;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DateSeparatorMessageModel;
import ch.threema.storage.models.FirstUnreadMessageModel;
import ch.threema.storage.models.MessageType;

public class ComposeMessageAdapter extends ArrayAdapter<AbstractMessageModel> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ComposeMessageAdapter");
	public static final int MIN_CONSTRAINT_LENGTH = 2;

	private final List<AbstractMessageModel> values;
	private final ChatAdapterDecorator.Helper decoratorHelper;
	private final MessageService messageService;
	private final UserService userService;
	private final FileService fileService;
	private final SparseIntArray resultMap = new SparseIntArray();
	private int resultMapIndex;
	private ConversationListFilter convListFilter = new ConversationListFilter();
	public ListView listView;
	private int groupId;
	private final EmojiMarkupUtil emojiMarkupUtil = EmojiMarkupUtil.getInstance();
	private CharSequence currentConstraint = "";
	private final int bubblePaddingLeftRight;
	private final int bubblePaddingBottom;
	private final int bubblePaddingBottomGrouped;

	private int firstUnreadPos = -1, unreadMessagesCount;
	private final Context context;
	private final ShapeAppearanceModel shapeAppearanceModelReceiveTop, shapeAppearanceModelReceiveMiddle, shapeAppearanceModelReceiveBottom, shapeAppearanceModelSendTop, shapeAppearanceModelSendMiddle, shapeAppearanceModelSendBottom, shapeAppearanceModelSingle;
	private final LayoutInflater layoutInflater;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({
		TYPE_SEND,
		TYPE_RECV,
		TYPE_STATUS,
		TYPE_FIRST_UNREAD,
		TYPE_MEDIA_SEND,
		TYPE_MEDIA_RECV,
		TYPE_LOCATION_SEND,
		TYPE_LOCATION_RECV,
		TYPE_AUDIO_SEND,
		TYPE_AUDIO_RECV,
		TYPE_FILE_SEND,
		TYPE_FILE_RECV,
		TYPE_BALLOT_SEND,
		TYPE_BALLOT_RECV,
		TYPE_ANIMGIF_SEND,
		TYPE_ANIMGIF_RECV,
		TYPE_TEXT_QUOTE_SEND,
		TYPE_TEXT_QUOTE_RECV,
		TYPE_STATUS_DATA_SEND,
		TYPE_STATUS_DATA_RECV,
		TYPE_DATE_SEPARATOR,
		TYPE_FILE_MEDIA_SEND,
		TYPE_FILE_MEDIA_RECV,
		TYPE_FILE_VIDEO_SEND,
		TYPE_GROUP_CALL_STATUS,
		TYPE_FORWARD_SECURITY_STATUS
	})
	public @interface ItemType {}

	public static final int TYPE_SEND = 0;
	public static final int TYPE_RECV = 1;
	public static final int TYPE_STATUS = 2;
	public static final int TYPE_FIRST_UNREAD = 3;
	public static final int TYPE_MEDIA_SEND = 4;
	public static final int TYPE_MEDIA_RECV = 5;
	public static final int TYPE_LOCATION_SEND = 6;
	public static final int TYPE_LOCATION_RECV = 7;
	public static final int TYPE_AUDIO_SEND = 8;
	public static final int TYPE_AUDIO_RECV = 9;
	public static final int TYPE_FILE_SEND = 10;
	public static final int TYPE_FILE_RECV = 11;
	public static final int TYPE_BALLOT_SEND = 12;
	public static final int TYPE_BALLOT_RECV = 13;
	public static final int TYPE_ANIMGIF_SEND = 14;
	public static final int TYPE_ANIMGIF_RECV = 15;
	public static final int TYPE_TEXT_QUOTE_SEND = 16;
	public static final int TYPE_TEXT_QUOTE_RECV = 17;
	public static final int TYPE_STATUS_DATA_SEND = 18;
	public static final int TYPE_STATUS_DATA_RECV = 19;
	public static final int TYPE_DATE_SEPARATOR = 20;
	public static final int TYPE_FILE_MEDIA_SEND = 21;
	public static final int TYPE_FILE_MEDIA_RECV = 22;
	public static final int TYPE_FILE_VIDEO_SEND = 23;
	public static final int TYPE_GROUP_CALL_STATUS = 24;
	public static final int TYPE_FORWARD_SECURITY_STATUS = 25;

	// don't forget to update this after adding new types:
	private static final int TYPE_MAX_COUNT = TYPE_FORWARD_SECURITY_STATUS + 1;

	private OnClickListener onClickListener;
	private Map<String, Integer> identityColors = null;

	public interface OnClickListener {
		void click(View view, int position, AbstractMessageModel messageModel);
		void longClick(View view, int position, AbstractMessageModel messageModel);
		boolean touch(View view, MotionEvent motionEvent, AbstractMessageModel messageModel);
		void avatarClick(View view, int position, AbstractMessageModel messageModel);
		void onSearchResultsUpdate(int searchResultsIndex, int searchResultsSize, int queryLength);
		void onSearchInProgress(boolean inProgress);
	}

	public ComposeMessageAdapter(
			Context context,
			MessagePlayerService messagePlayerService,
			List<AbstractMessageModel> values,
			UserService userService,
			ContactService contactService,
			FileService fileService,
			MessageService messageService,
			BallotService ballotService,
			PreferenceService preferenceService,
			DownloadService downloadService,
			LicenseService licenseService,
			MessageReceiver messageReceiver,
			ListView listView,
			ThumbnailCache<?> thumbnailCache,
			int thumbnailWidth,
			Fragment fragment,
			int unreadMessagesCount,
			ListenableFuture<MediaController> mediaControllerFuture) {
		super(context, R.layout.conversation_list_item_send, values);

		this.context = context;
		this.values = values;
		this.listView = listView;
		this.layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		int regularColor = ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSurface);
		int maxBubbleTextLength = context.getResources().getInteger(R.integer.max_bubble_text_length);
		int maxQuoteTextLength = context.getResources().getInteger(R.integer.max_quote_text_length);
		this.resultMapIndex = 0;
		this.unreadMessagesCount = unreadMessagesCount;
		this.messageService = messageService;
		this.userService = userService;
		this.fileService = fileService;
		this.decoratorHelper = new ChatAdapterDecorator.Helper(
				userService.getIdentity(),
				messageService,
				userService,
				contactService,
				fileService,
				messagePlayerService,
				ballotService,
				thumbnailCache,
				preferenceService,
				downloadService,
				licenseService,
				messageReceiver,
				thumbnailWidth,
				fragment,
				regularColor,
				maxBubbleTextLength,
				maxQuoteTextLength,
				mediaControllerFuture
		);

		int cornerRadius = context.getResources().getDimensionPixelSize(R.dimen.chat_bubble_border_radius),
			cornerRadiusSharp = context.getResources().getDimensionPixelSize(R.dimen.chat_bubble_border_radius_sharp);
		this.bubblePaddingLeftRight = getContext().getResources().getDimensionPixelSize(R.dimen.chat_bubble_container_padding_left_right);
		this.bubblePaddingBottom = getContext().getResources().getDimensionPixelSize(R.dimen.chat_bubble_container_padding_bottom);
		this.bubblePaddingBottomGrouped = getContext().getResources().getDimensionPixelSize(R.dimen.chat_bubble_container_padding_bottom_grouped);
		this.shapeAppearanceModelReceiveTop = new ShapeAppearanceModel.Builder()
			.setTopLeftCornerSize(cornerRadius)
			.setTopRightCornerSize(cornerRadius)
			.setBottomLeftCornerSize(cornerRadiusSharp)
			.setBottomRightCornerSize(cornerRadius)
			.build();
		this.shapeAppearanceModelReceiveMiddle = new ShapeAppearanceModel.Builder()
			.setTopLeftCornerSize(cornerRadiusSharp)
			.setTopRightCornerSize(cornerRadius)
			.setBottomLeftCornerSize(cornerRadiusSharp)
			.setBottomRightCornerSize(cornerRadius)
			.build();
		this.shapeAppearanceModelReceiveBottom = new ShapeAppearanceModel.Builder()
			.setTopLeftCornerSize(cornerRadiusSharp)
			.setTopRightCornerSize(cornerRadius)
			.setBottomLeftCornerSize(cornerRadius)
			.setBottomRightCornerSize(cornerRadius)
			.build();
		this.shapeAppearanceModelSendTop = new ShapeAppearanceModel.Builder()
			.setTopLeftCornerSize(cornerRadius)
			.setTopRightCornerSize(cornerRadius)
			.setBottomLeftCornerSize(cornerRadius)
			.setBottomRightCornerSize(cornerRadiusSharp)
			.build();
		this.shapeAppearanceModelSendMiddle = new ShapeAppearanceModel.Builder()
			.setTopLeftCornerSize(cornerRadius)
			.setTopRightCornerSize(cornerRadiusSharp)
			.setBottomLeftCornerSize(cornerRadius)
			.setBottomRightCornerSize(cornerRadiusSharp)
			.build();
		this.shapeAppearanceModelSendBottom = new ShapeAppearanceModel.Builder()
			.setTopLeftCornerSize(cornerRadius)
			.setTopRightCornerSize(cornerRadiusSharp)
			.setBottomLeftCornerSize(cornerRadius)
			.setBottomRightCornerSize(cornerRadius)
			.build();
		this.shapeAppearanceModelSingle = new ShapeAppearanceModel.Builder()
			.setAllCornerSizes(cornerRadius)
			.build();
	}

	/**
	 * remove the contact saved stuff and update the list
	 * @param contactModel
	 */
	@UiThread
	public void resetCachedContactModelData(ContactModel contactModel) {
		if(contactModel != null && this.decoratorHelper != null) {
			if(this.decoratorHelper.getContactCache().remove(contactModel.getIdentity())
				!= null) {
				notifyDataSetChanged();
			}
		}
	}
	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}

	public void setMessageReceiver(MessageReceiver messageReceiver) {
		if (this.decoratorHelper != null) {
			this.decoratorHelper.setMessageReceiver(messageReceiver);
		}
	}

	public void setThumbnailWidth(int preferredThumbnailWidth) {
		if (this.decoratorHelper != null) {
			this.decoratorHelper.setThumbnailWidth(preferredThumbnailWidth);
		}
	}

	public void setOnClickListener(OnClickListener onClickListener) {
		this.onClickListener = onClickListener;
	}

	@Override
	public @ItemType int getItemViewType(int position) {
		if (position < values.size()) {
			final AbstractMessageModel m = this.getItem(position);
			return this.getItemType(m);
		}
		return TYPE_STATUS;
	}

	@Nullable
	@Override
	public AbstractMessageModel getItem(int position) {
		if (position < values.size()) {
			if (position >= 0) {
				return super.getItem(position);
			}
		}
		return null;
	}

	private @ItemType int getItemType(AbstractMessageModel m) {
		if(m != null) {
			if(m.isStatusMessage()) {
				// Special handling for data status messages
				if (m instanceof FirstUnreadMessageModel) {
					return TYPE_FIRST_UNREAD;
				} else if (m instanceof DateSeparatorMessageModel) {
					return TYPE_DATE_SEPARATOR;
				} else if (m.getType() == MessageType.GROUP_CALL_STATUS) {
					return TYPE_GROUP_CALL_STATUS;
				} else if (m.getType() == MessageType.FORWARD_SECURITY_STATUS) {
					return TYPE_FORWARD_SECURITY_STATUS;
				} else {
					return TYPE_STATUS;
				}
			}
			else {
				boolean o = m.isOutbox();
				switch (m.getType()) {
					case LOCATION:
						return o ? TYPE_LOCATION_SEND : TYPE_LOCATION_RECV;
					case IMAGE:
						/* fallthrough */
					case VIDEO:
						return o ? TYPE_MEDIA_SEND : TYPE_MEDIA_RECV;
					case VOICEMESSAGE:
						return o ? TYPE_AUDIO_SEND : TYPE_AUDIO_RECV;
					case FILE:
						String mimeType = m.getFileData().getMimeType();
						int renderingType = m.getFileData().getRenderingType();
						if (MimeUtil.isGifFile(mimeType)) {
							return o ? TYPE_ANIMGIF_SEND : TYPE_ANIMGIF_RECV;
						} else if (MimeUtil.isAudioFile(mimeType) && renderingType == FileData.RENDERING_MEDIA) {
							return o ? TYPE_AUDIO_SEND : TYPE_AUDIO_RECV;
						} else if (renderingType == FileData.RENDERING_MEDIA || renderingType == FileData.RENDERING_STICKER) {
							if (MimeUtil.isImageFile(mimeType)) {
								return o ? TYPE_FILE_MEDIA_SEND : TYPE_FILE_MEDIA_RECV;
							} else if (MimeUtil.isVideoFile(mimeType)) {
								return o ? TYPE_FILE_VIDEO_SEND : TYPE_FILE_MEDIA_RECV;
							}
						}
						return o ? TYPE_FILE_SEND : TYPE_FILE_RECV;
					case BALLOT:
						return o ? TYPE_BALLOT_SEND : TYPE_BALLOT_RECV;
					case VOIP_STATUS:
						return o ? TYPE_STATUS_DATA_SEND : TYPE_STATUS_DATA_RECV;
					case GROUP_CALL_STATUS:
						return TYPE_GROUP_CALL_STATUS;
					case FORWARD_SECURITY_STATUS:
						return TYPE_FORWARD_SECURITY_STATUS;
					default:
						if (QuoteUtil.getQuoteType(m) != QuoteUtil.QUOTE_TYPE_NONE) {
							return o ? TYPE_TEXT_QUOTE_SEND : TYPE_TEXT_QUOTE_RECV;
						}
						return o ? TYPE_SEND : TYPE_RECV;
				}
			}
		}
		return TYPE_RECV;
	}

	private @LayoutRes int getLayoutByItemType(@ItemType int itemTypeId) {
		switch (itemTypeId) {
			case TYPE_SEND:
				return R.layout.conversation_list_item_send;
			case TYPE_RECV:
				return R.layout.conversation_list_item_recv;
			case TYPE_STATUS:
			case TYPE_FORWARD_SECURITY_STATUS:
				return R.layout.conversation_list_item_status;
			case TYPE_FIRST_UNREAD:
				return R.layout.conversation_list_item_unread;
			case TYPE_MEDIA_SEND:
			case TYPE_FILE_MEDIA_SEND:
				return R.layout.conversation_list_item_media_send;
			case TYPE_MEDIA_RECV:
			case TYPE_FILE_MEDIA_RECV:
				return R.layout.conversation_list_item_media_recv;
			case TYPE_FILE_VIDEO_SEND:
				return R.layout.conversation_list_item_video_send;
			case TYPE_LOCATION_SEND:
				return R.layout.conversation_list_item_location_send;
			case TYPE_LOCATION_RECV:
				return R.layout.conversation_list_item_location_recv;
			case TYPE_AUDIO_SEND:
				return R.layout.conversation_list_item_audio_send;
			case TYPE_AUDIO_RECV:
				return R.layout.conversation_list_item_audio_recv;
			case TYPE_FILE_SEND:
				return R.layout.conversation_list_item_file_send;
			case TYPE_FILE_RECV:
				return R.layout.conversation_list_item_file_recv;
			case TYPE_BALLOT_SEND:
				return R.layout.conversation_list_item_ballot_send;
			case TYPE_BALLOT_RECV:
				return R.layout.conversation_list_item_ballot_recv;
			case TYPE_ANIMGIF_SEND:
				return R.layout.conversation_list_item_animgif_send;
			case TYPE_ANIMGIF_RECV:
				return R.layout.conversation_list_item_animgif_recv;
			case TYPE_TEXT_QUOTE_SEND:
				return R.layout.conversation_list_item_quote_send;
			case TYPE_TEXT_QUOTE_RECV:
				return R.layout.conversation_list_item_quote_recv;
			case TYPE_STATUS_DATA_SEND:
				return R.layout.conversation_list_item_voip_status_send;
			case TYPE_STATUS_DATA_RECV:
				return R.layout.conversation_list_item_voip_status_recv;
			case TYPE_DATE_SEPARATOR:
				return R.layout.conversation_list_item_date_separator;
			case TYPE_GROUP_CALL_STATUS:
				return R.layout.conversation_list_item_group_call_status;
		}

		//return default!?
		return R.layout.conversation_list_item_recv;
	}

	@Override
	public int getViewTypeCount() {
		return TYPE_MAX_COUNT;
	}

	@NonNull
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View itemView = convertView;
		final ComposeMessageHolder holder;
		final AbstractMessageModel messageModel = values.get(position);
		MessageType messageType = messageModel.getType();

		@ItemType int itemType = this.getItemType(messageModel);
		int itemLayout = this.getLayoutByItemType(itemType);

		if (messageModel.isStatusMessage() && messageModel instanceof FirstUnreadMessageModel) {
			firstUnreadPos = position;
		}

		if ((convertView == null) || (getItemViewType(position) != itemType)) {
			// this is a new view or the ListView item type (and thus the layout) has changed
			holder = new ComposeMessageHolder();
			itemView = this.layoutInflater.inflate(itemLayout, parent, false);

			if (itemView != null) {
				holder.bodyTextView = itemView.findViewById(R.id.text_view);
				holder.messageBlockView = itemView.findViewById(R.id.message_block);
				holder.footerView = itemView.findViewById(R.id.indicator_container);
				holder.dateView = itemView.findViewById(R.id.date_view);
				holder.datePrefixIcon = itemView.findViewById(R.id.date_prefix_icon);

				if (isUserMessage(itemType)) {
					holder.senderView = itemView.findViewById(R.id.group_sender_view);
					holder.senderName = itemView.findViewById(R.id.group_sender_name);
					holder.deliveredIndicator = itemView.findViewById(R.id.delivered_indicator);
					holder.attachmentImage = itemView.findViewById(R.id.attachment_image_view);
					holder.avatarView = itemView.findViewById(R.id.avatar_view);
					holder.contentView = itemView.findViewById(R.id.content_block);
					holder.secondaryTextView = itemView.findViewById(R.id.secondary_text_view);
					holder.seekBar = itemView.findViewById(R.id.seek);
					holder.tertiaryTextView = itemView.findViewById(R.id.tertiaryTextView);
					holder.size = itemView.findViewById(R.id.document_size_view);
					holder.controller = itemView.findViewById(R.id.controller);
					holder.quoteBar = itemView.findViewById(R.id.quote_bar);
					holder.quoteThumbnail = itemView.findViewById(R.id.quote_thumbnail);
					holder.quoteTypeImage = itemView.findViewById(R.id.quote_type_image);
					holder.transcoderView = itemView.findViewById(R.id.transcoder_view);
					holder.readOnContainer = itemView.findViewById(R.id.read_on_container);
					holder.readOnButton = itemView.findViewById(R.id.read_on_button);
					holder.messageTypeButton = itemView.findViewById(R.id.message_type_button);
					holder.groupAckContainer = itemView.findViewById(R.id.groupack_container);
					holder.groupAckThumbsUpCount = itemView.findViewById(R.id.groupack_thumbsup_count);
					holder.groupAckThumbsDownCount = itemView.findViewById(R.id.groupack_thumbsdown_count);
					holder.groupAckThumbsUpImage = itemView.findViewById(R.id.groupack_thumbsup);
					holder.groupAckThumbsDownImage = itemView.findViewById(R.id.groupack_thumbsdown);
					holder.tapToResend = itemView.findViewById(R.id.tap_to_resend);
					holder.starredIcon = itemView.findViewById(R.id.star_icon);

					((ViewGroup) holder.groupAckContainer).getLayoutTransition().enableTransitionType(LayoutTransition.DISAPPEARING|LayoutTransition.APPEARING);
				}
				itemView.setTag(holder);
			}
		} else {
			// recycled view - reset a few views to their initial state
			holder = (ComposeMessageHolder) itemView.getTag();
			if (holder.messagePlayer != null) {
				// remove any references to listeners in case of a recycled view
				holder.messagePlayer.removeListeners();
				holder.messagePlayer = null;
			}

			// make sure height is re-set to zero to force redraw of item layout if it's recycled after swipe-to-delete
			if (isUserMessage(itemType)) {
				itemView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.WRAP_CONTENT, 0));
				if (messageModel.isOutbox()) {
					holder.messageBlockView.setCardBackgroundColor(AppCompatResources.getColorStateList(context, R.color.bubble_send_colorstatelist));
				} else {
					holder.messageBlockView.setCardBackgroundColor(AppCompatResources.getColorStateList(context, R.color.bubble_receive_colorstatelist));
				}
			} else {
				itemView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, 0));
			}
		}
		holder.position = position;

		final ChatAdapterDecorator decorator;

		if (itemType == TYPE_FIRST_UNREAD) {
			// add number of unread messages
			decorator = new FirstUnreadChatAdapterDecorator(this.context, messageModel, this.decoratorHelper, unreadMessagesCount);
		}
		else {
			final boolean showAvatar = adjustMarginsForMessageGrouping(holder, itemView, itemType, messageModel);

			if (messageType == null) {
				messageType = MessageType.STATUS;
			}

			switch (messageType) {
				case STATUS:
					decorator = new StatusChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case VIDEO:
					decorator = new VideoChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case IMAGE:
					decorator = new ImageChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case LOCATION:
					decorator = new LocationChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case VOICEMESSAGE:
					decorator = new AudioChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case BALLOT:
					decorator = new BallotChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case FILE:
					if (MimeUtil.isGifFile(messageModel.getFileData().getMimeType())) {
						decorator = new AnimGifChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					} else if (MimeUtil.isVideoFile(messageModel.getFileData().getMimeType()) &&
						(messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA ||
							messageModel.getFileData().getRenderingType() == FileData.RENDERING_STICKER)) {
						decorator = new VideoChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					} else if (MimeUtil.isAudioFile(messageModel.getFileData().getMimeType()) &&
						messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
						decorator = new AudioChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					} else {
						decorator = new FileChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					}
					break;
				case VOIP_STATUS:
					decorator = new VoipStatusDataChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case GROUP_CALL_STATUS:
					decorator = new GroupCallStatusDataChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case FORWARD_SECURITY_STATUS:
					decorator = new ForwardSecurityStatusChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
				case GROUP_STATUS:
					decorator = new GroupStatusAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					break;
					// Fallback to text chat adapter
				default:
					if (messageModel.isStatusMessage()) {
						if (messageModel instanceof DateSeparatorMessageModel) {
							decorator = new DateSeparatorChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
						} else {
							decorator = new StatusChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
						}
					} else {
						decorator = new TextChatAdapterDecorator(this.context, messageModel, this.decoratorHelper);
					}
			}

			if (groupId > 0) {
				decorator.setGroupMessage(groupId, this.identityColors);
				decorator.setGroupedMessage(showAvatar);
			}

			if (this.onClickListener != null) {
				final View v = holder.messageBlockView;

				decorator.setOnClickElement(messageModel12 -> onClickListener.click(v, position, messageModel12));

				decorator.setOnLongClickElement(messageModel13 -> onClickListener.longClick(v, position, messageModel13));

				decorator.setOnTouchElement((motionEvent, messageModel14) -> onClickListener.touch(v, motionEvent, messageModel14));

				if (!messageModel.isOutbox() && holder.avatarView != null) {
					if (groupId > 0) {
						holder.avatarView.setOnClickListener(v1 -> onClickListener.avatarClick(v1, position, messageModel));
						if (messageModel.getIdentity() != null) {
							ContactModel contactModel = decoratorHelper.getContactService().getByIdentity(messageModel.getIdentity());
							String displayName = NameUtil.getDisplayNameOrNickname(contactModel, true);
							holder.avatarView.setContentDescription(getContext().getString(R.string.show_contact) + ": " + displayName);
						}
					}
				}
			}
		}

		if (convListFilter != null && convListFilter.getHighlightMatches()) {
			/* show matches in decorator */
			decorator.setFilter(convListFilter.getFilterString());
		}
		if (parent != null && parent instanceof ListView) {
			decorator.setInListView(((ListView) parent));
		}
		decorator.decorate(holder, position);

		return itemView;
	}

	/**
	 * Adjust margins of item view so that items from the same sender may be displayed in a grouped fashion
	 * To be used in getView() call
	 * @param holder Holder object containing the current position within the Adapter in holder.position
	 * @param itemView The View for the current item
	 * @param itemType The Type the item is representing
	 * @return true if it's the first item in a group, false if it's a consecutive iitem
	 */
	private boolean adjustMarginsForMessageGrouping(ComposeMessageHolder holder, View itemView, @ItemType int itemType, @NonNull AbstractMessageModel currentItem) {
		boolean isFirstItemInGroup = true, hasPreviousItem = false, hasNextItem = false;

		if (itemView != null) {
			int paddingBottom = bubblePaddingBottom;
			if (isUserMessage(itemType)) {
				if (values.size() > holder.position + 1) {
					AbstractMessageModel nextItem = values.get(holder.position + 1);

					if (isUserMessage(getItemType(nextItem))) {
						if (isConsecutiveItem(currentItem, nextItem)) {
							paddingBottom = bubblePaddingBottomGrouped;
							hasNextItem = true;
						}
					}
				}

				if (holder.position > 0) {
					AbstractMessageModel previousItem = values.get(holder.position - 1);

					if (isUserMessage(getItemType(previousItem))) {
						if (isConsecutiveItem(currentItem, previousItem)) {
							isFirstItemInGroup = false;
							hasPreviousItem = true;
						}
					}
				}
			}

			holder.messageBlockView.setShapeAppearanceModel(getShapeAppearanceForBubble(currentItem.isOutbox(), hasPreviousItem, hasNextItem));

			if (itemView.getPaddingBottom() != paddingBottom) {
				itemView.setPadding(bubblePaddingLeftRight, 0, bubblePaddingLeftRight, paddingBottom);
			}
		}
		return isFirstItemInGroup;
	}

	/**
	 * Return the ShapeAppearanceModel that fits the combination of parameters
	 * @param isOutbox true if the user is the sender of the message(s), false if he is the receiver
	 * @param hasPreviousItem true if there is a consecutive message previous to this
	 * @param hasNextItem true if there is a consecutive message after this
	 * @return a ShapeAppearanceModel that fits the situation
	 */
	private ShapeAppearanceModel getShapeAppearanceForBubble(boolean isOutbox, boolean hasPreviousItem, boolean hasNextItem) {
		if (hasPreviousItem) {
			if (hasNextItem) {
				return isOutbox ? shapeAppearanceModelSendMiddle : shapeAppearanceModelReceiveMiddle;
			}
			return isOutbox ? shapeAppearanceModelSendBottom : shapeAppearanceModelReceiveBottom;
		}

		if (hasNextItem) {
			return isOutbox ? shapeAppearanceModelSendTop : shapeAppearanceModelReceiveTop;
		}

		return shapeAppearanceModelSingle;
	}

	/**
	 * Detect if the provided two messageModels are from the same sender
	 * @param firstModel AbstractMessageModel of first item
	 * @param secondModel AbstractMessageModel of second item
	 * @return true if sender is equal and items may possibly be grouped
	 */
	private boolean isConsecutiveItem(@Nullable AbstractMessageModel firstModel, @Nullable AbstractMessageModel secondModel) {
		if (firstModel != null && secondModel != null) {
			if (firstModel.isOutbox() == secondModel.isOutbox()) {
				if (groupId > 0) {
					if (firstModel.getIdentity() == null) {
						// I am the sender
						return secondModel.getIdentity()  == null;
					} else {
						return firstModel.getIdentity().equals(secondModel.getIdentity());
					}
				} else {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Check if the message of the provided type is a message originating from a user or if it's a system, status or stub/placeholder message
	 * @param itemType Type to check
	 * @return true if it's a user-generated message, false otherwise
	 */
	private boolean isUserMessage(@ItemType int itemType) {
		return (itemType != TYPE_STATUS &&
			itemType != TYPE_FIRST_UNREAD &&
			itemType != TYPE_DATE_SEPARATOR &&
			itemType != TYPE_GROUP_CALL_STATUS &&
			itemType != TYPE_FORWARD_SECURITY_STATUS);
	}

	public class ConversationListFilter extends Filter {
		private String filterString = null;
		private String filterIdentity = null;
		private String myIdentity = null;
		private boolean highlightMatches = true;

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			currentConstraint = constraint;
			onClickListener.onSearchInProgress(true);

			FilterResults results = new FilterResults();

			resultMap.clear();
			resultMapIndex = 0;
			searchUpdate();

			if (constraint == null || constraint.length() < MIN_CONSTRAINT_LENGTH) {
				// no filtering
				filterString = null;
			} else {
				// perform filtering
				int index = 0, position = 0;
				filterString = constraint.toString();

				if (filterIdentity != null) {
					// search for quotes referenced by either the text or the API message ID of the original message
					String apiMessageIdToSearchFor = null;
					if (filterString.startsWith("#") && filterString.length() == 17) {
						apiMessageIdToSearchFor = filterString.substring(1, 17);
					}

					for (position = values.size() - 1; position >= 0; position--) {
						AbstractMessageModel messageModel = values.get(position);

						if (apiMessageIdToSearchFor != null) {
							// search for message ids
							if (apiMessageIdToSearchFor.equals(messageModel.getApiMessageId())) {
								resultMap.put(index, position);
								break;
							}
						} else {
						if (((messageModel.getType() == MessageType.TEXT && !messageModel.isStatusMessage()) ||
							messageModel.getType() == MessageType.IMAGE ||
							messageModel.getType() == MessageType.FILE ||
							messageModel.getType() == MessageType.LOCATION)) {
							String body = messageModel.getCaption();
							if (TextUtils.isEmpty(body)) {
								body = QuoteUtil.getMessageBody(messageModel, false);
							}
							if (body != null) {
								if (body.equals(filterString)) {
									if (messageModel.isOutbox()) {
										if (filterIdentity.equals(myIdentity)) {
											resultMap.put(index, position);
											break;
										}
									} else {
										if (messageModel.getIdentity().equals(filterIdentity)) {
											resultMap.put(index, position);
											break;
										}
									}
								}
							}
						}
						}
					}
				} else {
					// filtering of matching messages by content
					for (AbstractMessageModel messageModel : values) {
						if ((messageModel.getType() == MessageType.TEXT && !messageModel.isStatusMessage())
								|| messageModel.getType() == MessageType.LOCATION
								|| messageModel.getType() == MessageType.BALLOT) {
							String body = messageModel.getBody();

							if (messageModel.getType() == MessageType.TEXT) {
								// enable searching in quoted text
								int quoteType = QuoteUtil.getQuoteType(messageModel);
								if (quoteType != QuoteUtil.QUOTE_TYPE_NONE) {
									QuoteUtil.QuoteContent quoteContent = QuoteUtil.getQuoteContent(
										messageModel,
										decoratorHelper.getMessageReceiver(),
										false,
										decoratorHelper.getThumbnailCache(),
										getContext(),
										messageService,
										userService,
										fileService
									);
									if (quoteContent != null) {
										body = quoteContent.quotedText + " " + quoteContent.bodyText;
									}
								}
								// strip away mentions
								body = emojiMarkupUtil.stripMentions(body);
							}

							if (body != null && body.toLowerCase().contains(filterString.toLowerCase())) {
								resultMap.put(index, position);
								index++;
							}
						} else if (messageModel.getType() == MessageType.FILE) {
							String searchString = "";

							if (messageModel.getFileData().getRenderingType() == RENDERING_DEFAULT && !TestUtil.empty(messageModel.getFileData().getFileName())) {
								// do not index filename for RENDERING_MEDIA or RENDERING_STICKER as it's not visible in the UI
								searchString += messageModel.getFileData().getFileName();
							}

							if (!TestUtil.empty(messageModel.getFileData().getCaption())) {
								searchString += messageModel.getFileData().getCaption();
							}

							if (searchString.toLowerCase().contains(filterString.toLowerCase())) {
								resultMap.put(index, position);
								index++;
							}
						} else if (!TestUtil.empty(messageModel.getCaption())) {
							if (messageModel.getCaption().toLowerCase().contains(filterString.toLowerCase())) {
								resultMap.put(index, position);
								index++;
							}
						}
						position++;
					}
				}
				results.values = resultMap;
				results.count = resultMap.size();
			}

			onClickListener.onSearchInProgress(false);
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			if (constraint != null && currentConstraint != null &&
					!constraint.toString().equals(currentConstraint.toString())) {
				return;
			}

			final int positionOfLastMatch = getMatchPosition(filterString);

			if (convListFilter != null && convListFilter.getHighlightMatches()) {
				notifyDataSetChanged();
				resultMapIndex = resultMap.size() - 1;
				searchUpdate();
				if (!TextUtils.isEmpty(filterString)) {
					listView.postDelayed(new Runnable() {
						@Override
						public void run() {
							listView.setSelection(positionOfLastMatch);
						}
					}, 500);
				}
			} else if (positionOfLastMatch != AbsListView.INVALID_POSITION) {
				if (listView != null) {
					notifyDataSetChanged();
					listView.post(() -> {
						smoothScrollTo(positionOfLastMatch);
						listView.postDelayed(new Runnable() {
							@Override
							public void run() {
								listView.setItemChecked(positionOfLastMatch, true);
								listView.postDelayed(new Runnable() {
									@Override
									public void run() {
										listView.setItemChecked(positionOfLastMatch, false);
										listView.postDelayed(new Runnable() {
											@Override
											public void run() {
												listView.setItemChecked(positionOfLastMatch, true);
												listView.postDelayed(new Runnable() {
													@Override
													public void run() {
														listView.setItemChecked(positionOfLastMatch, false);
													}
												}, 300);
											}
										}, 200);
									}
								}, 200);
							}
						}, 300);
					});
				}
			}
	}

		public String getFilterString() {
			return filterString;
		}

		public void setFilterIdentity(String filterIdentity) {
			this.filterIdentity = filterIdentity;
		}

		public void setMyIdentity(String myIdentity) {
			this.myIdentity = myIdentity;
		}

		public void setHighlightMatches(boolean highlightMatches) {
			this.highlightMatches = highlightMatches;
		}

		public boolean getHighlightMatches() {
			return highlightMatches;
		}
	}

	@NonNull
	@Override
	public Filter getFilter() {
		return convListFilter;
	}

	/**
	 * Create an instance of ConversationListFilter for quote searching
	 *
	 * @param quoteContent
	 * @return
	 */
	public Filter getQuoteFilter(QuoteUtil.QuoteContent quoteContent) {
		convListFilter = new ConversationListFilter();
		convListFilter.setFilterIdentity(quoteContent.identity);
		convListFilter.setMyIdentity(userService.getIdentity());
		convListFilter.setHighlightMatches(false);

		return convListFilter;
	}

	private void searchUpdate() {
		int size = resultMap.size();

		onClickListener.onSearchResultsUpdate(size > 0 ? resultMapIndex + 1 : 0, resultMap.size(), currentConstraint.length());
	}

	public void resetMatchPosition() {
		resultMapIndex = resultMap.size() > 0 ? resultMap.size() - 1 : 0;

		searchUpdate();
	}

	public void nextMatchPosition() {
		SingleToast.getInstance().close();
		if (resultMap.size() > 1) {
			resultMapIndex++;
			if (resultMapIndex >= resultMap.size()) {
				// wrap around - search from beginning
				resultMapIndex = 0;
			}
			smoothScrollTo(resultMap.get(resultMapIndex));
			searchUpdate();
		} else {
			SingleToast.getInstance().showShortText(context.getString(R.string.search_no_more_matches));
		}
	}

	public void previousMatchPosition() {
		SingleToast.getInstance().close();
		if (resultMap.size() > 1) {
			resultMapIndex--;
			if (resultMapIndex < 0) {
				// wrap around - search from end
				resultMapIndex = resultMap.size() - 1;
			}
			smoothScrollTo(resultMap.get(resultMapIndex));
			searchUpdate();
		} else {
			SingleToast.getInstance().showShortText(context.getString(R.string.search_no_more_matches));
		}
	}

	private void smoothScrollTo(int to) {
		int from = listView.getFirstVisiblePosition();
		if (Math.abs(to - from) < 5) {
			listView.smoothScrollToPosition(to);
		} else {
			listView.setSelection(to);
		}
	}

	public void clearFilter() {
		resultMapIndex = 0;
		resultMap.clear();
		convListFilter = new ConversationListFilter();
	}

	private int getMatchPosition(String filterString) {
		if ((resultMap.size() > 0) && (resultMapIndex < resultMap.size())) {
			//Destroy toast!
			SingleToast.getInstance().close();
			return resultMap.get(resultMap.size() - 1);
		} else if (filterString != null && filterString.length() > 0) {
			if (convListFilter.getHighlightMatches()) {
				SingleToast.getInstance().showShortText(context.getString(R.string.search_no_matches));
			} else {
				return AbsListView.INVALID_POSITION;
			}
		}
		return Integer.MAX_VALUE;
	}

	public void setUnreadMessagesCount(int unreadMessagesCount) {
		this.unreadMessagesCount = unreadMessagesCount;
	}

	public boolean removeFirstUnreadPosition() {
		if(this.firstUnreadPos >= 0) {
			if(this.firstUnreadPos >= this.getCount()) {
				this.firstUnreadPos = -1;
				return false;
			}

			AbstractMessageModel m = this.getItem(this.firstUnreadPos);
			if(m != null && m instanceof FirstUnreadMessageModel) {
				this.firstUnreadPos = -1;
				this.remove(m);
				return true;
			}
		}
		return false;
	}

	public void setIdentityColors(Map<String, Integer> colors) {
		this.identityColors = colors;
	}

	@Override
	public void remove(final AbstractMessageModel object) {
		int c = this.getCount();
		super.remove(object);

		if(c > 0 && c == this.getCount()) {
			//nothing deleted, search!
			AbstractMessageModel newObject = Functional.select(this.values, new IPredicateNonNull<AbstractMessageModel>() {
				@Override
				public boolean apply(@NonNull AbstractMessageModel o) {
					return o.getId() == object.getId();
				}
			});

			if(newObject != null) {
				super.remove(newObject);
			}
		}
	}

	@Override
	public boolean isEnabled(int position) {
		return false;
	}

	/**
	 * Get adapter position of next available (i.e. downloaded) voice message with same incoming/outgoing status
	 * @param messageModel of original message
	 * @return AbstractMessageModel of next message in adapter that matches the specified criteria or AbsListView.INVALID_POSITION if none is found
	 */
	public int getNextVoiceMessage(AbstractMessageModel messageModel) {
		int index = values.indexOf(messageModel);
		if (index < values.size() - 1) {
			AbstractMessageModel nextMessage = values.get(index + 1);
			if (nextMessage != null) {
				boolean isVoiceMessage = nextMessage.getType() == MessageType.VOICEMESSAGE;
				if (!isVoiceMessage) {
					// new school voice messages
					isVoiceMessage = nextMessage.getType() == MessageType.FILE &&
						MimeUtil.isAudioFile(nextMessage.getFileData().getMimeType()) &&
						nextMessage.getFileData().getRenderingType() == FileData.RENDERING_MEDIA &&
						nextMessage.getFileData().isDownloaded();
				}

				if (isVoiceMessage) {
					if (messageModel.isOutbox() == nextMessage.isOutbox()) {
						if (messageModel.isAvailable()) {
							return index + 1;
						}
					}
				}
			}
		}
		return AbsListView.INVALID_POSITION;
	}
}
