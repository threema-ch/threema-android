/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiImageSpan;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.data.LocationDataModel;

public class GlobalSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GlobalSearchAdapter");
	private static final String FLOW_CHARACTER = "\u25BA\uFE0E";

	private GroupService groupService;
	private ContactService contactService;

	private final Context context;
	private OnClickItemListener onClickItemListener;
	private String queryString;
	private List<AbstractMessageModel> messageModels; // Cached copy of AbstractMessageModels

	private static class ItemHolder extends RecyclerView.ViewHolder {
		private final TextView titleView;
		private final TextView dateView;
		private final TextView snippetView;
		private final AvatarView avatarView;
		AvatarListItemHolder avatarListItemHolder;

		private ItemHolder(final View itemView) {
			super(itemView);

			titleView = itemView.findViewById(R.id.name);
			dateView = itemView.findViewById(R.id.date);
			snippetView = itemView.findViewById(R.id.snippet);
			avatarView = itemView.findViewById(R.id.avatar_view);
			avatarListItemHolder = new AvatarListItemHolder();
			avatarListItemHolder.avatarView = avatarView;
			avatarListItemHolder.avatarLoadingAsyncTask = null;
		}
	}

	GlobalSearchAdapter(Context context) {
		this.context = context;

		try {
			this.groupService = ThreemaApplication.getServiceManager().getGroupService();
			this.contactService = ThreemaApplication.getServiceManager().getContactService();
		} catch (Exception e) {
			logger.error("Unable to get Services", e);
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
			.inflate(R.layout.item_global_search, parent, false);

		return new ItemHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		ItemHolder itemHolder = (ItemHolder) holder;

		if (messageModels != null) {
			AbstractMessageModel current = getItem(position);

			if (current instanceof GroupMessageModel) {
				final ContactModel contactModel = current.isOutbox() ? this.contactService.getMe() : this.contactService.getByIdentity(current.getIdentity());
				final GroupModel groupModel = groupService.getById(((GroupMessageModel) current).getGroupId());
				AvatarListItemUtil.loadAvatar(groupModel, groupService, itemHolder.avatarListItemHolder);

				String groupName = NameUtil.getDisplayName(groupModel, groupService);
				itemHolder.titleView.setText(
					String.format("%s %s %s", NameUtil.getDisplayNameOrNickname(contactModel, true), FLOW_CHARACTER, groupName)
				);
			} else {
				final ContactModel contactModel = this.contactService.getByIdentity(current.getIdentity());
				AvatarListItemUtil.loadAvatar( current.isOutbox() ? contactService.getMe() : contactModel, contactService, itemHolder.avatarListItemHolder);

				String name = NameUtil.getDisplayNameOrNickname(context, current, contactService);
				itemHolder.titleView.setText(
					current.isOutbox() ?
						name + " " + FLOW_CHARACTER + " " + NameUtil.getDisplayNameOrNickname(contactModel, true) :
						name
				);
			}
			itemHolder.dateView.setText(LocaleUtil.formatDateRelative(current.getCreatedAt().getTime()));

			setSnippetToTextView(current, itemHolder);

			if (this.onClickItemListener != null) {
				itemHolder.itemView.setOnClickListener(v -> onClickItemListener.onClick(current, itemHolder.itemView));
			}
		} else {
			// Covers the case of data not being ready yet.
			itemHolder.titleView.setText("No data");
			itemHolder.dateView.setText("");
			itemHolder.snippetView.setText("");
		}
	}

	/**
	 * Returns a snippet containing the first occurrence of needle in fullText
	 * Splits text on emoji boundary
	 * Note: the match is case-insensitive
	 * @param fullText Full text
	 * @param needle Text to search for
	 * @param holder ItemHolder containing a textview
	 * @return Snippet containing the match with a trailing ellipsis if the match is located beyond the first 17 characters
	 */
	private String getSnippet(@NonNull String fullText, @NonNull String needle, ItemHolder holder) {
		int firstMatch = fullText.toLowerCase().indexOf(needle);
		if (firstMatch > 17) {
			int until = firstMatch > 20 ? firstMatch - 20 : 0;

			for (int i = firstMatch; i > until; i--) {
				if (Character.isWhitespace(fullText.charAt(i))) {
					return "…" + fullText.substring(i + 1);
				}
			}

			SpannableStringBuilder emojified = (SpannableStringBuilder) EmojiMarkupUtil.getInstance().addTextSpans(context, fullText, holder.snippetView, true);

			int transitionStart = emojified.nextSpanTransition(firstMatch - 17, firstMatch, EmojiImageSpan.class);
			if (transitionStart == firstMatch) {
				// there are no spans here
				return "…" + emojified.subSequence(firstMatch - 17, emojified.length()).toString();
			} else {
				return "…" + emojified.subSequence(transitionStart, emojified.length()).toString();
			}
		}
		return fullText;
	}


	void setMessageModels(List<AbstractMessageModel> messageModels){
		this.messageModels = messageModels;
		notifyDataSetChanged();
	}

	private void setSnippetToTextView(AbstractMessageModel current, ItemHolder itemHolder) {
		String snippetText = null;
		if (!TestUtil.empty(this.queryString)) {
			switch (current.getType()) {
				case FILE:
					// fallthrough
				case IMAGE:
					if (!TestUtil.empty(current.getCaption())) {
						snippetText = getSnippet(current.getCaption(), this.queryString, itemHolder);
					}
					break;
				case TEXT:
					// fallthrough
				case BALLOT:
					if (!TestUtil.empty(current.getBody())) {
						snippetText = getSnippet(current.getBody(), this.queryString, itemHolder);
					}
					break;
				case LOCATION:
					final LocationDataModel location = current.getLocationData();
					if (location != null) {
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
					}
					break;
				default:
					// Audio and Video Messages don't have text or captions
					break;
			}
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
		}
		else {
			return 0;
		}
	}

	void setOnClickItemListener(OnClickItemListener onClickItemListener) {
		this.onClickItemListener = onClickItemListener;
	}

	public void onQueryChanged(String queryText) {
		this.queryString = queryText;
	}

	public interface OnClickItemListener {
		void onClick(AbstractMessageModel messageModel, View view);
	}
}
