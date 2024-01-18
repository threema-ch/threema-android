/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
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

public class GlobalSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GlobalSearchAdapter");
	private static final String FLOW_CHARACTER = "\u25BA\uFE0E";

	private GroupService groupService;
	private ContactService contactService;
	private BallotService ballotService;
	private DeadlineListService hiddenChatsListService;

	private final Context context;
	private OnClickItemListener onClickItemListener;
	private final SparseBooleanArray checkedItems = new SparseBooleanArray();
	private String queryString;
	private final int snippetThreshold;
	private List<AbstractMessageModel> messageModels; // Cached copy of AbstractMessageModels
	private final @ColorInt int foregroundColor;
	private final @LayoutRes int itemLayout;
	private final ColorStateList colorStateListSend, colorStateListReceive;
	private final @NonNull RequestManager requestManager;

	private static class ItemHolder extends RecyclerView.ViewHolder {
		private final TextView titleView;
		private final TextView dateView;
		private final TextView snippetView;
		private final ImageView thumbnailView;
		private final MaterialCardView messageBlock;
		AvatarListItemHolder avatarListItemHolder;

		private ItemHolder(final View itemView) {
			super(itemView);

			titleView = itemView.findViewById(R.id.name);
			dateView = itemView.findViewById(R.id.date);
			snippetView = itemView.findViewById(R.id.snippet);
			AvatarView avatarView = itemView.findViewById(R.id.avatar_view);
			thumbnailView = itemView.findViewById(R.id.thumbnail_view);
			messageBlock = itemView.findViewById(R.id.message_block);

			avatarListItemHolder = new AvatarListItemHolder();
			avatarListItemHolder.avatarView = avatarView;
			avatarListItemHolder.avatarLoadingAsyncTask = null;
		}
	}

	public GlobalSearchAdapter(
		Context context,
		@NonNull RequestManager requestManager,
		@LayoutRes int itemLayout,
		int snippetThreshold
	) {
		this.context = context;
		this.requestManager = requestManager;
		this.itemLayout = itemLayout;
		this.snippetThreshold = snippetThreshold;

		try {
			this.groupService = ThreemaApplication.getServiceManager().getGroupService();
			this.contactService = ThreemaApplication.getServiceManager().getContactService();
			this.ballotService = ThreemaApplication.getServiceManager().getBallotService();
			this.hiddenChatsListService = ThreemaApplication.getServiceManager().getHiddenChatsListService();
		} catch (Exception e) {
			logger.error("Unable to get Services", e);
		}

		this.foregroundColor = ConfigUtils.getColorFromAttribute(context, R.attr.colorOnBackground);
		this.colorStateListSend = ContextCompat.getColorStateList(context, R.color.bubble_send_colorstatelist);
		this.colorStateListReceive = ContextCompat.getColorStateList(context, R.color.bubble_receive_colorstatelist);
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
			.inflate(itemLayout, parent, false);

		return new ItemHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		ItemHolder itemHolder = (ItemHolder) holder;

		if (messageModels != null) {
			AbstractMessageModel current = getItem(position);

			if (itemHolder.messageBlock != null) {
				if (current.isOutbox()) {
					itemHolder.messageBlock.setCardBackgroundColor(colorStateListSend);
				} else {
					itemHolder.messageBlock.setCardBackgroundColor(colorStateListReceive);
				}
			}

			if (hiddenChatsListService.has(
				current instanceof GroupMessageModel ?
					groupService.getUniqueIdString(
						((GroupMessageModel) current).getGroupId()
					) :
					contactService.getUniqueIdString(
						current.getIdentity()
					)
			)) {
				itemHolder.dateView.setText("");
				itemHolder.thumbnailView.setVisibility(View.GONE);
				itemHolder.titleView.setText("");
				itemHolder.snippetView.setText(R.string.private_chat_subject);
				itemHolder.avatarListItemHolder.avatarView.setVisibility(View.INVISIBLE);
				itemHolder.avatarListItemHolder.avatarView.setBadgeVisible(false);
			} else {
				if (current instanceof GroupMessageModel) {
					final ContactModel contactModel = current.isOutbox() ? this.contactService.getMe() : this.contactService.getByIdentity(current.getIdentity());
					final GroupModel groupModel = groupService.getById(((GroupMessageModel) current).getGroupId());
					AvatarListItemUtil.loadAvatar(
						groupModel,
						groupService,
						itemHolder.avatarListItemHolder,
						requestManager
					);

					String groupName = NameUtil.getDisplayName(groupModel, groupService);
					itemHolder.titleView.setText(
						String.format("%s %s %s", NameUtil.getDisplayNameOrNickname(contactModel, true), FLOW_CHARACTER, groupName)
					);
				} else {
					final ContactModel contactModel = this.contactService.getByIdentity(current.getIdentity());
					AvatarListItemUtil.loadAvatar(
						current.isOutbox() ? contactService.getMe() : contactModel,
						contactService,
						itemHolder.avatarListItemHolder,
						requestManager
					);

					String name = NameUtil.getDisplayNameOrNickname(context, current, contactService);
					itemHolder.titleView.setText(
						current.isOutbox() ?
							name + " " + FLOW_CHARACTER + " " + NameUtil.getDisplayNameOrNickname(contactModel, true) :
							name
					);
				}
				itemHolder.dateView.setText(LocaleUtil.formatDateRelative(current.getCreatedAt().getTime()));

				if (current.getType() == MessageType.FILE && current.getFileData().isDownloaded()) {
					loadThumbnail(current, itemHolder);
					itemHolder.thumbnailView.setVisibility(View.VISIBLE);
				} else if (current.getType() == MessageType.BALLOT) {
					itemHolder.thumbnailView.setImageResource(R.drawable.ic_outline_rule);
					itemHolder.thumbnailView.setVisibility(View.VISIBLE);
					setupPlaceholder(itemHolder);
				} else {
					itemHolder.thumbnailView.setVisibility(View.GONE);
				}

				setSnippetToTextView(current, itemHolder);

				if (itemHolder.snippetView.getText() == null || itemHolder.snippetView.getText().length() == 0) {
					if (current.getType() == MessageType.FILE) {
						String mimeString = current.getFileData().getMimeType();
						if (!TestUtil.empty(mimeString)) {
							itemHolder.snippetView.setText(MimeUtil.getMimeDescription(context, current.getFileData().getMimeType()));
						} else {
							itemHolder.snippetView.setText("");
						}
					}
				}
			}

			if (this.onClickItemListener != null) {
				itemHolder.itemView.setOnClickListener(v -> onClickItemListener.onClick(current, itemHolder.itemView, position));
				itemHolder.itemView.setOnLongClickListener(v -> onClickItemListener.onLongClick(current, itemHolder.itemView, position));
			}

			((CheckableRelativeLayout) holder.itemView).setChecked(checkedItems.get(position));
		} else {
			// Covers the case of data not being ready yet.
			itemHolder.titleView.setText("No data");
			itemHolder.dateView.setText("");
			itemHolder.snippetView.setText("");
		}
	}

	private void loadThumbnail(AbstractMessageModel messageModel, ItemHolder holder) {
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
				public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
					setupPlaceholder(holder);
					return false;
				}

				@Override
				public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
					holder.thumbnailView.clearColorFilter();
					holder.thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
					return false;
				}
			})
            .into(holder.thumbnailView);
	}

	private void setupPlaceholder(ItemHolder holder) {
		holder.thumbnailView.setColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN);
		holder.thumbnailView.setScaleType(ImageView.ScaleType.CENTER);
	}

	/**
	 * Returns a snippet containing the first occurrence of needle in fullText
	 * Splits text on emoji boundary
	 * Note: the match is case-insensitive
	 *
	 * @param fullText Full text
	 * @param needle   Text to search for
	 * @param holder   ItemHolder containing a textview
	 * @return Snippet containing the match with a trailing ellipsis if the match is located beyond the first snippetThreshold characters
	 */
	private String getSnippet(@NonNull String fullText, @Nullable String needle, ItemHolder holder) {
		if (!TestUtil.empty(needle)) {
			int firstMatch = fullText.toLowerCase().indexOf(needle);
			if (firstMatch > snippetThreshold) {
				int snippetStart = firstMatch > (snippetThreshold + 3) ? firstMatch - (snippetThreshold + 3) : 0;

				for (int i = snippetStart; i < firstMatch; i++) {
					if (Character.isWhitespace(fullText.charAt(i))) {
						return "…" + fullText.substring(i + 1);
					}
				}

				SpannableStringBuilder emojified = (SpannableStringBuilder) EmojiMarkupUtil.getInstance().addTextSpans(context, fullText, holder.snippetView, true);

				int transitionStart = emojified.nextSpanTransition(firstMatch - snippetThreshold, firstMatch, EmojiImageSpan.class);
				if (transitionStart == firstMatch) {
					// there are no spans here
					return "…" + emojified.subSequence(firstMatch - snippetThreshold, emojified.length());
				} else {
					return "…" + emojified.subSequence(transitionStart, emojified.length());
				}
			}
		}
		return fullText;
	}


	public void setMessageModels(List<AbstractMessageModel> messageModels) {
		this.messageModels = messageModels;
		notifyDataSetChanged();
	}

	private void setSnippetToTextView(AbstractMessageModel current, ItemHolder itemHolder) {
		String snippetText = null;
		switch (current.getType()) {
			case FILE:
				// fallthrough
			case IMAGE:
				if (!TestUtil.empty(current.getCaption())) {
					snippetText = getSnippet(current.getCaption(), this.queryString, itemHolder);
				}
				break;
			case TEXT:
				if (!TestUtil.empty(current.getBody())) {
					snippetText = getSnippet(current.getBody(), this.queryString, itemHolder);
				}
				break;
			case BALLOT:
				snippetText = context.getString(R.string.attach_ballot);
				if (!TestUtil.empty(current.getBody())) {
					BallotDataModel ballotData = current.getBallotData();
					final BallotModel ballotModel = ballotService.get(ballotData.getBallotId());
					if (ballotModel != null) {
						snippetText = getSnippet(ballotModel.getName(), this.queryString, itemHolder);
					}
				}
				break;
			case LOCATION:
				final LocationDataModel location = current.getLocationData();
				StringBuilder locationStringBuilder = new StringBuilder();
				if (!TestUtil.empty(location.getPoi())) {
					locationStringBuilder.append(location.getPoi());
				}
				if (!TestUtil.empty(location.getAddress())) {
					if (locationStringBuilder.length() > 0) {
						locationStringBuilder.append(" - ");
					}
					locationStringBuilder.append(location.getAddress());
				}
				if (locationStringBuilder.length() > 0) {
					snippetText = getSnippet(locationStringBuilder.toString(), this.queryString, itemHolder);
				}
				break;
			default:
				// Audio and Video Messages don't have text or captions
				break;
		}

		if (snippetText != null) {
			itemHolder.snippetView.setText(TextUtil.highlightMatches(context, snippetText, this.queryString, true, false));
		} else {
			itemHolder.snippetView.setText(null);
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
		}
		else {
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
