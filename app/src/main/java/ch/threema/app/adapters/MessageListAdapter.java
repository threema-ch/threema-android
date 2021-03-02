/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CountBoxView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.TagModel;

public class MessageListAdapter extends AbstractRecyclerAdapter<ConversationModel, RecyclerView.ViewHolder> {
	private static final Logger logger = LoggerFactory.getLogger(MessageListAdapter.class);

	private static final int MAX_SELECTED_ITEMS = 0;

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_FOOTER = 1;

	private final Context context;
	private final GroupService groupService;
	private final ConversationTagService conversationTagService;
	private final ContactService contactService;
	private final DistributionListService distributionListService;
	private final DeadlineListService mutedChatsListService, hiddenChatsListService, mentionOnlyChatsListService;
	private final RingtoneService ringtoneService;
	private final ConversationService conversationService;
	private final EmojiMarkupUtil emojiMarkupUtil;
	private final Bitmap defaultContactImage, defaultGroupImage, defaultDistributionListImage;
	private final StateBitmapUtil stateBitmapUtil;
	private @ColorInt final int regularColor;
	private @ColorInt final int ackColor;
	private @ColorInt final int decColor;
	private @ColorInt final int backgroundColor;
	private final boolean isTablet;
	private final LayoutInflater inflater;
	private final ItemClickListener clickListener;
	private final List<ConversationModel> selectedChats = new ArrayList<>();
	private String highlightUid;

	// TODO: remove if custom tags are implemented
	private final TagModel starTagModel;

	public static class MessageListViewHolder extends RecyclerView.ViewHolder {

		TextView fromView;
		protected TextView dateView;
		TextView subjectView;
		ImageView deliveryView, attachmentView, pinIcon;
		View listItemFG;
		View latestMessageContainer;
		View typingContainer;
		TextView groupMemberName;
		CountBoxView unreadCountView;
		View unreadIndicator;
		ImageView muteStatus;
		ImageView hiddenStatus;
		protected AvatarView avatarView;
		protected ConversationModel conversationModel;
		AvatarListItemHolder avatarListItemHolder;
		//TODO: change this logic, if custom tags are implemented
		final View tagStarOn;

		MessageListViewHolder(final View itemView) {
			super(itemView);

			tagStarOn = itemView.findViewById(R.id.tag_star_on);

			fromView = itemView.findViewById(R.id.from);
			dateView = itemView.findViewById(R.id.date);
			subjectView = itemView.findViewById(R.id.subject);
			unreadCountView = itemView.findViewById(R.id.unread_count);
			avatarView = itemView.findViewById(R.id.avatar_view);
			attachmentView = itemView.findViewById(R.id.attachment);
			deliveryView = itemView.findViewById(R.id.delivery);
			listItemFG = itemView.findViewById(R.id.list_item_fg);
			latestMessageContainer = itemView.findViewById(R.id.latest_message_container);
			typingContainer = itemView.findViewById(R.id.typing_container);
			groupMemberName = itemView.findViewById(R.id.group_member_name);
			unreadIndicator = itemView.findViewById(R.id.unread_view);
			muteStatus = itemView.findViewById(R.id.mute_status);
			hiddenStatus = itemView.findViewById(R.id.hidden_status);
			pinIcon = itemView.findViewById(R.id.pin_icon);
			avatarListItemHolder = new AvatarListItemHolder();
			avatarListItemHolder.avatarView = avatarView;
			avatarListItemHolder.avatarLoadingAsyncTask = null;
		}

		public View getItem() {
			return itemView;
		}

		public ConversationModel getConversationModel() { return conversationModel; }
	}

	public static class FooterViewHolder extends RecyclerView.ViewHolder {
		FooterViewHolder(View itemView) {
			super(itemView);
		}
	}

	public interface ItemClickListener {
		void onItemClick(View view, int position, ConversationModel conversationModel);
		boolean onItemLongClick(View view, int position, ConversationModel conversationModel);
		void onAvatarClick(View view, int position, ConversationModel conversationModel);
		void onFooterClick(View view);
	}

	public MessageListAdapter(
			Context context,
			ContactService contactService,
			GroupService groupService,
			DistributionListService distributionListService,
			ConversationService conversationService,
			DeadlineListService mutedChatsListService,
			DeadlineListService mentionOnlyChatsListService,
			DeadlineListService hiddenChatsListService,
			ConversationTagService conversationTagService,
			RingtoneService ringtoneService,
			String highlightUid,
			ItemClickListener clickListener) {

		this.context = context;
		this.inflater = LayoutInflater.from(context);
		this.contactService = contactService;
		this.groupService = groupService;
		this.conversationTagService = conversationTagService;
		this.emojiMarkupUtil = EmojiMarkupUtil.getInstance();
		this.stateBitmapUtil = StateBitmapUtil.getInstance();
		this.distributionListService = distributionListService;
		this.conversationService = conversationService;
		this.mutedChatsListService = mutedChatsListService;
		this.mentionOnlyChatsListService = mentionOnlyChatsListService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.ringtoneService = ringtoneService;
		this.defaultContactImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact);
		this.defaultGroupImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_group);
		this.defaultDistributionListImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_distribution_list);
		this.highlightUid = highlightUid;
		this.clickListener = clickListener;

		this.regularColor = ConfigUtils.getColorFromAttribute(context, android.R.attr.textColorSecondary);
		this.backgroundColor = ConfigUtils.getColorFromAttribute(context, android.R.attr.windowBackground);

		this.ackColor = context.getResources().getColor(R.color.material_green);
		this.decColor = context.getResources().getColor(R.color.material_orange);

		this.isTablet = ConfigUtils.isTabletLayout();

		// TODO: select the star model
		this.starTagModel = this.conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN);
	}

	@Override
	public int getItemViewType(int position) {
		return position >= super.getItemCount() ? TYPE_FOOTER : TYPE_ITEM;
	}

	@Override
	public int getItemCount() {
		int count = super.getItemCount();

		if (count > 0) {
			return count + 1;
		} else {
			return 1;
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
		if (viewType == TYPE_ITEM) {
			View itemView = inflater.inflate(R.layout.item_message_list, viewGroup, false);
			itemView.setClickable(true);
			// TODO: MaterialCardView: Setting a custom background is not supported.
			itemView.setBackgroundResource(R.drawable.listitem_background_selector);
			return new MessageListViewHolder(itemView);
		}
		return new FooterViewHolder(inflater.inflate(R.layout.footer_message_section, viewGroup, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
		if (h instanceof MessageListViewHolder) {
			final MessageListViewHolder holder = (MessageListViewHolder) h;
			final int position = h.getAdapterPosition();

			final ConversationModel conversationModel = this.getEntity(position);
			holder.conversationModel = conversationModel;

			holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// position may have changed after the item was bound. query current position from holder
					int currentPos = holder.getLayoutPosition();

					if (currentPos >= 0) {
						clickListener.onItemClick(v, currentPos, getEntity(currentPos));
					}
				}
			});

			holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					// position may have changed after the item was bound. query current position from holder
					int currentPos = holder.getLayoutPosition();

					if (currentPos >= 0) {
						return clickListener.onItemLongClick(v, currentPos, getEntity(currentPos));
					}
					return false;
				}
			});

			holder.avatarView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// position may have changed after the item was bound. query current position from holder
					int currentPos = holder.getLayoutPosition();

					if (currentPos >= 0) {
						clickListener.onAvatarClick(v, currentPos, getEntity(currentPos));
					}
				}
			});

			// Show or hide star tag
			boolean isTagStarOn = this.conversationTagService.isTaggedWith(conversationModel, this.starTagModel);
			ViewUtil.show(holder.tagStarOn, isTagStarOn);
			ViewUtil.show(holder.pinIcon, isTagStarOn);

			AbstractMessageModel messageModel = conversationModel.getLatestMessage();

			if (holder.groupMemberName != null) {
				holder.groupMemberName.setVisibility(View.GONE);
			}

			// see 01-83
			// Spannable from = new SpannableString(fromtext);
			// from.setSpan(new ForegroundColorSpan(R.color.message_count_color), fromtext.lastIndexOf(fromcounttext), fromtext.lastIndexOf(fromcounttext) + fromcounttext.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			// holder.fromView.setText(from);
			holder.fromView.setText(conversationModel.getReceiver().getDisplayName());

			if (conversationModel.hasUnreadMessage() && messageModel != null && !messageModel.isOutbox()) {
				holder.fromView.setTextAppearance(context, R.style.Threema_TextAppearance_List_FirstLine_Bold);
				holder.subjectView.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine_Bold);
				if (holder.groupMemberName != null && holder.dateView != null) {
					holder.groupMemberName.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine_Bold);
				}
				long unreadCount = conversationModel.getUnreadCount();
				if (unreadCount > 0) {
					holder.unreadCountView.setText(String.valueOf(unreadCount));
					holder.unreadCountView.setVisibility(View.VISIBLE);
					holder.unreadIndicator.setVisibility(View.VISIBLE);
				}
			} else {
				holder.fromView.setTextAppearance(context, R.style.Threema_TextAppearance_List_FirstLine);
				holder.subjectView.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine);
				if (holder.groupMemberName != null && holder.dateView != null) {
					holder.groupMemberName.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine);
				}
				holder.unreadCountView.setVisibility(View.GONE);
				holder.unreadIndicator.setVisibility(View.GONE);
			}

			holder.deliveryView.setColorFilter(this.regularColor);
			holder.attachmentView.setColorFilter(this.regularColor);
			holder.muteStatus.setColorFilter(this.regularColor);
			holder.dateView.setTextAppearance(context, R.style.Threema_TextAppearance_List_ThirdLine);
			holder.subjectView.setVisibility(View.VISIBLE);

			String uniqueId = conversationModel.getReceiver().getUniqueIdString();

			if (messageModel != null) {
				if (hiddenChatsListService.has(uniqueId)) {
					holder.hiddenStatus.setVisibility(View.VISIBLE);
					// give user some privacy even in visible mode
					holder.subjectView.setText(R.string.private_chat_subject);
					holder.attachmentView.setVisibility(View.GONE);
					holder.dateView.setVisibility(View.INVISIBLE);
					holder.deliveryView.setVisibility(View.GONE);
				} else {
					holder.hiddenStatus.setVisibility(View.GONE);
					holder.dateView.setText(MessageUtil.getDisplayDate(this.context, messageModel, false));
					holder.dateView.setContentDescription("." + context.getString(R.string.state_dialog_modified) + "." + holder.dateView.getText() + ".");
					holder.dateView.setVisibility(View.VISIBLE);

					String draft = ThreemaApplication.getMessageDraft(uniqueId);
					if (!TestUtil.empty(draft)) {
						holder.groupMemberName.setVisibility(View.GONE);
						holder.attachmentView.setVisibility(View.GONE);
						holder.deliveryView.setVisibility(View.GONE);
						holder.dateView.setText(" " + context.getString(R.string.draft));
						holder.dateView.setContentDescription(null);
						holder.dateView.setTextAppearance(context, R.style.Threema_TextAppearance_List_ThirdLine_Bold);
						holder.dateView.setVisibility(View.VISIBLE);
						holder.subjectView.setText(emojiMarkupUtil.formatBodyTextString(context, draft + " ", 100));
					} else {
						if (conversationModel.isGroupConversation()) {
							if (holder.groupMemberName != null) {
								holder.groupMemberName.setText(NameUtil.getShortName(this.context, messageModel, this.contactService) + ": ");
								holder.groupMemberName.setVisibility(View.VISIBLE);
							}
						}
						// Configure subject
						MessageUtil.MessageViewElement viewElement = MessageUtil.getViewElement(this.context, messageModel);
						String subject = viewElement.text;

						if (messageModel.getType() == MessageType.TEXT) {
							// we need to add an arbitrary character - otherwise span-only strings are formatted incorrectly in the item layout
							subject += " ";
						}

						if (viewElement.icon != null) {
							holder.attachmentView.setVisibility(View.VISIBLE);
							holder.attachmentView.setImageResource(viewElement.icon);
							if (viewElement.placeholder != null) {
								holder.attachmentView.setContentDescription(viewElement.placeholder);
							} else {
								holder.attachmentView.setContentDescription("");
							}

							// Configure attachment
							// Configure color of the attachment view
							if (viewElement.color != null) {
								holder.attachmentView.setColorFilter(
										this.context.getResources().getColor(viewElement.color),
										PorterDuff.Mode.SRC_IN);
							}
						} else {
							holder.attachmentView.setVisibility(View.GONE);
						}

						if (TestUtil.empty(subject)) {
							holder.subjectView.setText("");
							holder.subjectView.setContentDescription("");
						} else {
							// Append space if attachmentView is visible
							if (holder.attachmentView.getVisibility() == View.VISIBLE) {
								subject = " " + subject;
							}
							holder.subjectView.setText(emojiMarkupUtil.formatBodyTextString(context, subject, 100));
							holder.subjectView.setContentDescription(viewElement.contentDescription);
						}

						// Special icons for voice call message
						if (messageModel.getType() == MessageType.VOIP_STATUS) {
							// Always show the phone icon
							holder.deliveryView.setImageResource(R.drawable.ic_phone_locked);
						} else {
							if (!messageModel.isOutbox()) {
								holder.deliveryView.setImageResource(R.drawable.ic_reply_filled);
								holder.deliveryView.setContentDescription(context.getString(R.string.state_sent));

								if (messageModel.getState() != null) {
									switch (messageModel.getState()) {
										case USERACK:
											holder.deliveryView.setColorFilter(this.ackColor);
											break;
										case USERDEC:
											holder.deliveryView.setColorFilter(this.decColor);
											break;
									}
								}
								holder.deliveryView.setVisibility(View.VISIBLE);
							} else {
								stateBitmapUtil.setStateDrawable(messageModel, holder.deliveryView, false);
							}
						}

						if (conversationModel.isGroupConversation()) {
							if (groupService.isGroupOwner(conversationModel.getGroup()) && groupService.countMembers(conversationModel.getGroup()) == 1) {
								holder.deliveryView.setImageResource(R.drawable.ic_spiral_bound_booklet_outline);
								holder.deliveryView.setContentDescription(context.getString(R.string.notes));
							} else {
								holder.deliveryView.setImageResource(R.drawable.ic_group_filled);
								holder.deliveryView.setContentDescription(context.getString(R.string.prefs_group_notifications));
							}
							holder.deliveryView.setVisibility(View.VISIBLE);
						} else if (conversationModel.isDistributionListConversation()) {
							holder.deliveryView.setImageResource(R.drawable.ic_distribution_list_filled);
							holder.deliveryView.setContentDescription(context.getString(R.string.distribution_list));
							holder.deliveryView.setVisibility(View.VISIBLE);
						}
					}
				}
				if (mutedChatsListService.has(uniqueId)) {
					holder.muteStatus.setImageResource(R.drawable.ic_do_not_disturb_filled);
					holder.muteStatus.setVisibility(View.VISIBLE);
				} else if (mentionOnlyChatsListService.has(uniqueId)) {
					holder.muteStatus.setImageResource(R.drawable.ic_dnd_mention_black_18dp);
					holder.muteStatus.setVisibility(View.VISIBLE);
				} else if (ringtoneService.hasCustomRingtone(uniqueId) && ringtoneService.isSilent(uniqueId, conversationModel.isGroupConversation())) {
					holder.muteStatus.setImageResource(R.drawable.ic_notifications_off_filled);
					holder.muteStatus.setVisibility(View.VISIBLE);
				} else {
					holder.muteStatus.setVisibility(View.GONE);
				}
			} else {
				// empty chat
				holder.attachmentView.setVisibility(View.GONE);
				holder.deliveryView.setVisibility(View.GONE);
				holder.dateView.setVisibility(View.GONE);
				holder.dateView.setContentDescription(null);
				holder.subjectView.setText("");
				holder.subjectView.setContentDescription("");
				holder.muteStatus.setVisibility(View.GONE);
				holder.hiddenStatus.setVisibility(uniqueId != null && hiddenChatsListService.has(uniqueId) ? View.VISIBLE : View.GONE);
			}

			AdapterUtil.styleConversation(holder.fromView, groupService, conversationModel);

			// load avatars asynchronously
			AvatarListItemUtil.loadAvatar(
					position,
					conversationModel,
					this.defaultContactImage,
					this.defaultGroupImage,
					this.defaultDistributionListImage,
					this.contactService,
					this.groupService,
					this.distributionListService,
					holder.avatarListItemHolder
			);

			this.updateTypingIndicator(
					holder,
					conversationModel.isTyping()
			);

			holder.itemView.setActivated(selectedChats.contains(conversationModel));

			if (isTablet) {
				// handle selection in multi-pane mode
				if (highlightUid != null && highlightUid.equals(conversationModel.getUid()) && context instanceof ComposeMessageActivity) {
					if (ConfigUtils.getAppTheme(context) == ConfigUtils.THEME_DARK) {
						holder.listItemFG.setBackgroundResource(R.color.settings_multipane_selection_bg_dark);
					} else {
						holder.listItemFG.setBackgroundResource(R.color.settings_multipane_selection_bg_light);
					}
				} else {
					holder.listItemFG.setBackgroundColor(this.backgroundColor);
				}
			}
		} else {
			// footer
			Chip archivedChip = h.itemView.findViewById(R.id.archived_text);

			int count = conversationService.getArchivedCount();
			if (count > 0) {
				archivedChip.setVisibility(View.VISIBLE);
				archivedChip.setOnClickListener(v -> clickListener.onFooterClick(v));
				archivedChip.setText(String.format(context.getString(R.string.num_archived_chats), count));
			} else {
				archivedChip.setVisibility(View.GONE);
			}
		}
	}

	public void toggleItemChecked(ConversationModel model, int position) {
		if (selectedChats.contains(model)) {
			selectedChats.remove(model);
		} else if (selectedChats.size() <= MAX_SELECTED_ITEMS) {
			selectedChats.add(model);
		}
		notifyItemChanged(position);
	}

	public void clearSelections() {
		selectedChats.clear();
		notifyDataSetChanged();
	}

	public int getCheckedItemCount() {
		return selectedChats.size();
	}

	public List<ConversationModel> getCheckedItems() {
		return selectedChats;
	}

	public void setHighlightItem(String uid) {
		highlightUid = uid;
	}

	private void updateTypingIndicator(MessageListViewHolder holder, boolean isTyping) {
		if(holder != null && holder.latestMessageContainer != null && holder.typingContainer != null) {
			holder.latestMessageContainer.setVisibility(isTyping ? View.GONE : View.VISIBLE);
			holder.typingContainer.setVisibility(!isTyping ? View.GONE : View.VISIBLE);
		}
	}
}
