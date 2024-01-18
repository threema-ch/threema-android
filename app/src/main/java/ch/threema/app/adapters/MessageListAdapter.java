/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;

public class MessageListAdapter extends AbstractRecyclerAdapter<ConversationModel, RecyclerView.ViewHolder> {

	private static final int MAX_SELECTED_ITEMS = 0;

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_FOOTER = 1;

	private final @NonNull Context context;
	private final @NonNull GroupCallManager groupCallManager;
	private final @NonNull ConversationService conversationService;
	private final @NonNull ContactService contactService;
	private final @NonNull GroupService groupService;
	private final @NonNull DeadlineListService mutedChatsListService;
	private final @NonNull DeadlineListService mentionOnlyChatsListService;
	private final @NonNull RingtoneService ringtoneService;
	private final @NonNull DeadlineListService hiddenChatsListService;
	private final @NonNull MessageListViewHolder.MessageListItemParams messageListItemParams;
	private final @NonNull MessageListViewHolder.MessageListItemStrings messageListItemStrings;
	private final @NonNull LayoutInflater inflater;
	private final @NonNull ItemClickListener clickListener;
	private final @NonNull MessageListViewHolder.MessageListViewHolderClickListener clickForwarder = new MessageListViewHolder.MessageListViewHolderClickListener() {
		@Override
		public void onItemClick(@NonNull View view, int position) {
			clickListener.onItemClick(view, position, getEntity(position));
		}

		@Override
		public boolean onItemLongClick(@NonNull View view, int position) {
			return clickListener.onItemLongClick(view, position, getEntity(position));
		}

		@Override
		public void onAvatarClick(@NonNull View view, int position) {
			clickListener.onAvatarClick(view, position, getEntity(position));
		}

		@Override
		public void onJoinGroupCallClick(int position) {
			clickListener.onJoinGroupCallClick(getEntity(position));
		}
	};
	private final List<ConversationModel> selectedChats = new ArrayList<>();
	private RecyclerView recyclerView;
	private final Map<ConversationModel, MessageListAdapterItem> messageListAdapterItemsCache;

	private final @NonNull RequestManager requestManager;


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

		void onJoinGroupCallClick(ConversationModel conversationModel);
	}

	public MessageListAdapter(
		@NonNull Context context,
		@NonNull ContactService contactService,
		@NonNull GroupService groupService,
		@NonNull DistributionListService distributionListService,
		@NonNull ConversationService conversationService,
		@NonNull DeadlineListService mutedChatsListService,
		@NonNull DeadlineListService mentionOnlyChatsListService,
		@NonNull RingtoneService ringtoneService,
		@NonNull DeadlineListService hiddenChatsListService,
		@NonNull GroupCallManager groupCallManager,
		@Nullable String highlightUid,
		@NonNull ItemClickListener clickListener,
		@NonNull Map<ConversationModel, MessageListAdapterItem> messageListAdapterItemCache,
		@NonNull RequestManager requestManager
		) {
		this.context = context;
		this.inflater = LayoutInflater.from(context);
		this.conversationService = conversationService;
		this.contactService = contactService;
		this.groupService = groupService;
		this.mutedChatsListService = mutedChatsListService;
		this.mentionOnlyChatsListService = mentionOnlyChatsListService;
		this.ringtoneService = ringtoneService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.clickListener = clickListener;
		EmojiMarkupUtil emojiMarkupUtil = EmojiMarkupUtil.getInstance();
		StateBitmapUtil stateBitmapUtil = StateBitmapUtil.getInstance();

		messageListItemParams = new MessageListViewHolder.MessageListItemParams(
			ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSurface),
			ContextCompat.getColor(context, R.color.material_green),
			ContextCompat.getColor(context, R.color.material_orange),
			ConfigUtils.getColorFromAttribute(context, android.R.attr.colorBackground),
			ConfigUtils.isTabletLayout(),
			emojiMarkupUtil,
			contactService,
			groupService,
			distributionListService,
			highlightUid,
			stateBitmapUtil
		);

		messageListItemStrings = new MessageListViewHolder.MessageListItemStrings(
			context.getString(R.string.notes),
			context.getString(R.string.prefs_group_notifications),
			context.getString(R.string.distribution_list),
			context.getString(R.string.state_sent),
			String.format(" %s", context.getString(R.string.draft))
		);

		this.groupCallManager = groupCallManager;
		this.messageListAdapterItemsCache = messageListAdapterItemCache;

		this.requestManager = requestManager;
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

	@Override
	public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
		super.onViewRecycled(holder);
		ConversationModel conversationModel = null;
		if (holder instanceof MessageListViewHolder) {
			MessageListAdapterItem item = ((MessageListViewHolder) holder).getMessageListAdapterItem();
			if (item != null) {
				conversationModel = item.getConversationModel();
			}
		}
		if (conversationModel != null && conversationModel.isGroupConversation()) {
			MessageListViewHolder messageListViewHolder = (MessageListViewHolder) holder;
			GroupModel group = conversationModel.getGroup();
			if (group != null) {
				groupCallManager.removeGroupCallObserver(group, messageListViewHolder);
			}
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
		if (viewType == TYPE_ITEM) {
			View itemView = inflater.inflate(R.layout.item_message_list, viewGroup, false);
			itemView.setClickable(true);
			return new MessageListViewHolder(
				itemView,
				context,
				clickForwarder,
				groupCallManager,
				messageListItemParams,
				messageListItemStrings,
				requestManager
			);
		}
		return new FooterViewHolder(inflater.inflate(R.layout.footer_message_section, viewGroup, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
		if (h instanceof MessageListViewHolder) {
			ConversationModel conversationModel = getEntity(h.getAbsoluteAdapterPosition());
			MessageListAdapterItem item;
			if (messageListAdapterItemsCache.containsKey(conversationModel)) {
				item = messageListAdapterItemsCache.get(conversationModel);
			} else {
				item = new MessageListAdapterItem(
					conversationModel,
					contactService,
					groupService,
					mutedChatsListService,
					mentionOnlyChatsListService,
					ringtoneService,
					hiddenChatsListService
				);
				synchronized (messageListAdapterItemsCache) {
					messageListAdapterItemsCache.put(conversationModel, item);
				}
			}
			((MessageListViewHolder) h).setMessageListAdapterItem(item);
		} else {
			// footer
			MaterialButton archivedButton = h.itemView.findViewById(R.id.archived_text);

			int archivedCount = conversationService.getArchivedCount();
			if (archivedCount > 0) {
				archivedButton.setVisibility(View.VISIBLE);
				archivedButton.setOnClickListener(clickListener::onFooterClick);
				archivedButton.setText(ConfigUtils.getSafeQuantityString(ThreemaApplication.getAppContext(), R.plurals.num_archived_chats, archivedCount, archivedCount));
				if (recyclerView != null) {
					((EmptyRecyclerView) recyclerView).setNumHeadersAndFooters(0);
				}
			} else {
				archivedButton.setVisibility(View.GONE);
				if (recyclerView != null) {
					((EmptyRecyclerView) recyclerView).setNumHeadersAndFooters(1);
				}
			}
		}
	}

	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onAttachedToRecyclerView(recyclerView);

		this.recyclerView = recyclerView;
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		this.recyclerView = null;

		super.onDetachedFromRecyclerView(recyclerView);
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

	public void refreshFooter() {
		notifyItemChanged(getItemCount() - 1);
	}

	public List<ConversationModel> getCheckedItems() {
		return selectedChats;
	}

	public void setHighlightItem(String uid) {
		messageListItemParams.setHighlightUid(uid);
	}
}
