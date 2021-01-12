/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.archive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.MessageType;

public class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.ArchiveViewHolder> {

	private static final Logger logger = LoggerFactory.getLogger(ArchiveAdapter.class);

	private final Context context;
	private ArchiveAdapter.OnClickItemListener onClickItemListener;
	private final Bitmap defaultContactImage, defaultGroupImage, defaultDistributionListImage;
	private ContactService contactService;
	private GroupService groupService;
	private DistributionListService distributionListService;
	private DeadlineListService hiddenChatsListService;
	private SparseBooleanArray checkedItems = new SparseBooleanArray();

	class ArchiveViewHolder extends RecyclerView.ViewHolder {

		TextView fromView;
		TextView dateView;
		TextView subjectView;
		ImageView deliveryView, attachmentView;
		View latestMessageContainer;
		TextView groupMemberName;
		AvatarView avatarView;
		AvatarListItemHolder avatarListItemHolder;

		private ArchiveViewHolder(final View itemView) {
			super(itemView);

			fromView = itemView.findViewById(R.id.from);
			dateView = itemView.findViewById(R.id.date);
			subjectView = itemView.findViewById(R.id.subject);
			avatarView = itemView.findViewById(R.id.avatar_view);
			attachmentView = itemView.findViewById(R.id.attachment);
			deliveryView = itemView.findViewById(R.id.delivery);
			latestMessageContainer = itemView.findViewById(R.id.latest_message_container);
			groupMemberName = itemView.findViewById(R.id.group_member_name);
			avatarListItemHolder = new AvatarListItemHolder();
			avatarListItemHolder.avatarView = avatarView;
			avatarListItemHolder.avatarLoadingAsyncTask = null;
		}
	}

	private final LayoutInflater inflater;
	private List<ConversationModel> conversationModels; // Cached copy of conversationModels

	ArchiveAdapter(Context context) {
		this.context = context;
		this.inflater = LayoutInflater.from(context);

		this.defaultContactImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact);
		this.defaultGroupImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_group);
		this.defaultDistributionListImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_distribution_list);

		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager == null) {
				throw new ThreemaException("Missing servicemanager");
			}
			this.distributionListService = serviceManager.getDistributionListService();
			this.groupService = serviceManager.getGroupService();
			this.contactService = serviceManager.getContactService();
			this.hiddenChatsListService = serviceManager.getHiddenChatsListService();
		} catch (Exception e) {
			logger.debug("Exception", e);
		}
	}

	@NonNull
	@Override
	public ArchiveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View itemView = inflater.inflate(R.layout.item_archive, parent, false);
		return new ArchiveViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull ArchiveViewHolder holder, int position) {
		if (conversationModels != null) {
			final ConversationModel conversationModel = conversationModels.get(position);
			final AbstractMessageModel messageModel = conversationModel.getLatestMessage();

			if (holder.groupMemberName != null) {
				holder.groupMemberName.setVisibility(View.GONE);
			}

			holder.deliveryView.setVisibility(View.GONE);
			holder.fromView.setText(conversationModel.getReceiver().getDisplayName());
			holder.fromView.setTextAppearance(context, R.style.Threema_TextAppearance_List_FirstLine);
			holder.subjectView.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine);

			if (holder.groupMemberName != null && holder.dateView != null) {
				holder.groupMemberName.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine);
				holder.dateView.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine);
			}

			if (messageModel != null) {
				if (hiddenChatsListService.has(conversationModel.getReceiver().getUniqueIdString())) {
					// give user some privacy even in visible mode
					holder.subjectView.setText(R.string.private_chat_subject);
					holder.subjectView.setVisibility(View.VISIBLE);
					holder.attachmentView.setVisibility(View.GONE);
					holder.dateView.setVisibility(View.INVISIBLE);
					holder.deliveryView.setVisibility(View.GONE);
				} else {
					holder.dateView.setText(MessageUtil.getDisplayDate(this.context, messageModel, false));
					holder.dateView.setVisibility(View.VISIBLE);

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

					if (ViewUtil.show(holder.subjectView, subject != null)) {
						// Append space if attachmentView is visible

						if (holder.attachmentView.getVisibility() == View.VISIBLE) {
							subject = " " + subject;
						}
						holder.subjectView.setText(EmojiMarkupUtil.getInstance().formatBodyTextString(context, subject, 100));
					}
				}
			} else {
				// empty chat
				holder.attachmentView.setVisibility(View.GONE);
				holder.deliveryView.setVisibility(View.GONE);
				holder.dateView.setVisibility(View.GONE);
				holder.subjectView.setVisibility(View.VISIBLE);
				holder.subjectView.setText("");
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

			((CheckableRelativeLayout) holder.itemView).setChecked(checkedItems.get(position));

			if (this.onClickItemListener != null) {
				holder.itemView.setOnClickListener(v -> onClickItemListener.onClick(conversationModel, holder.itemView, position));
				holder.itemView.setOnLongClickListener(v -> onClickItemListener.onLongClick(conversationModel, holder.itemView, position));
			}
		} else {
			// Covers the case of data not being ready yet.
			holder.fromView.setText("No data");
			holder.dateView.setText("");
			holder.subjectView.setText("");
		}
	}

	// getItemCount() is called many times, and when it is first called,
	// conversationModels has not been updated (means initially, it's null, and we can't return null).
	@Override
	public int getItemCount() {
		if (conversationModels != null)
			return conversationModels.size();
		else return 0;
	}

	void setConversationModels(List<ConversationModel> newConversationModels) {
		if (conversationModels != null) {
			SparseBooleanArray newCheckedItems = new SparseBooleanArray(newConversationModels.size());
			for (int i = 0; i < newConversationModels.size(); i++) {
				String newUid = newConversationModels.get(i).getUid();
				if (newUid != null) {
					for (int j = 0; j < conversationModels.size(); j++) {
						if (newUid.equals(conversationModels.get(j).getUid())) {
							if (checkedItems.get(j)) {
								newCheckedItems.put(i, true);
							}
							break;
						}
					}
				}
			}
			this.checkedItems = newCheckedItems;
		}
		this.conversationModels = newConversationModels;
		notifyDataSetChanged();
	}

	void setOnClickItemListener(OnClickItemListener onClickItemListener) {
		this.onClickItemListener = onClickItemListener;
	}

	void toggleChecked(int pos) {
		if (checkedItems.get(pos, false)) {
			checkedItems.delete(pos);
		}
		else {
			checkedItems.put(pos, true);
		}
		notifyItemChanged(pos);
	}

	void clearCheckedItems() {
		checkedItems.clear();
		notifyDataSetChanged();
	}

	void selectAll() {
		if (checkedItems.size() == conversationModels.size()) {
			clearCheckedItems();
		} else {
			for (int i = 0; i < conversationModels.size(); i++) {
				checkedItems.put(i, true);
			}
			notifyDataSetChanged();
		}
	}

	int getCheckedItemsCount() {
		return checkedItems.size();
	}

	public List<ConversationModel> getCheckedItems() {
		List<ConversationModel> items = new ArrayList<>(checkedItems.size());
		for (int i = 0; i < checkedItems.size(); i++) {
			items.add(conversationModels.get(checkedItems.keyAt(i)));
		}
		return items;
	}

	public interface OnClickItemListener {
		void onClick(ConversationModel conversationModel, View view, int position);
		boolean onLongClick(ConversationModel conversationModel, View itemView, int position);
	}
}
