/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2020 Threema GmbH
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
import org.slf4j.LoggerFactory;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.emojis.EmojiImageSpan;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.LocationDataModel;

public abstract class GlobalSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private static final Logger logger = LoggerFactory.getLogger(GlobalSearchAdapter.class);

	private static final int TYPE_HEADER = 0;
	private static final int TYPE_ITEM = 1;
	protected static final String FLOW_CHARACTER = "\u25BA\uFE0E";

	protected final Context context;
	protected GlobalSearchAdapter.OnClickItemListener onClickItemListener;
	protected String queryString;
	protected String headerString;
	protected List<AbstractMessageModel> messageModels; // Cached copy of AbstractMessageModels

	static class ItemHolder extends RecyclerView.ViewHolder {
		protected final TextView titleView;
		protected final TextView dateView;
		protected final TextView snippetView;
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

	public class HeaderHolder extends RecyclerView.ViewHolder {
		protected final TextView headerText;

		public HeaderHolder(View view) {
			super(view);

			this.headerText = itemView.findViewById(R.id.header_text);
		}
	}

	GlobalSearchAdapter(Context context, String headerString) {
		this.headerString = headerString;
		this.context = context;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		if (viewType == TYPE_ITEM) {
			View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_global_search, parent, false);

			return new GlobalSearchAdapter.ItemHolder(v);
		} else if (viewType == TYPE_HEADER) {
			View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.header_global_search, parent, false);

			return new GlobalSearchAdapter.HeaderHolder(v);
		}
		throw new RuntimeException("no matching item type");
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

	protected void setSnippetToTextView(AbstractMessageModel current, ItemHolder itemHolder) {
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

	protected AbstractMessageModel getItem(int position) {
		return messageModels.get(position - 1);
	}

	// getItemCount() is called many times, and when it is first called,
	// messageModels has not been updated (means initially, it's null, and we can't return null).
	@Override
	public int getItemCount() {
		if (messageModels != null && messageModels.size() > 0) {
			return messageModels.size() + 1; // account for header
		}
		else {
			return 0;
		}
	}

	@Override
	public int getItemViewType(int position) {
		if (isPositionHeader(position))
			return TYPE_HEADER;

		return TYPE_ITEM;
	}

	private boolean isPositionHeader(int position) {
		return position == 0;
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
