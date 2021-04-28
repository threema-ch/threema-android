/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public class RecentListAdapter extends FilterableListAdapter {
	private final Context context;
	private List<ConversationModel> values;
	private List<ConversationModel> ovalues;
	private RecentListFilter recentListFilter;
	private final Bitmap defaultContactImage, defaultGroupImage;
	private final Bitmap defaultDistributionListImage;
	private final ContactService contactService;
	private final GroupService groupService;
	private final DistributionListService distributionListService;

	public RecentListAdapter(Context context,
	                         final List<ConversationModel> values,
	                         final List<Integer> checkedItems,
	                         ContactService contactService,
	                         GroupService groupService,
	                         DistributionListService distributionListService) {
		super(context, R.layout.item_user_list, (List<Object>) (Object) values);

		this.context = context;
		this.values = values;
		this.ovalues = values;
		this.contactService = contactService;
		this.groupService = groupService;
		this.distributionListService = distributionListService;
		this.defaultContactImage = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_contact);
		this.defaultGroupImage = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_group);
		this.defaultDistributionListImage = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_distribution_list);
		if (checkedItems != null && checkedItems.size() > 0) {
			// restore checked items
			this.checkedItems.addAll(checkedItems);
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		CheckableConstraintLayout itemView = (CheckableConstraintLayout) convertView;

		RecentListHolder holder = new RecentListHolder();

		if (convertView == null) {
			// This a new view we inflate the new layout
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			itemView = (CheckableConstraintLayout) inflater.inflate(R.layout.item_recent_list, parent, false);

			TextView nameView = itemView.findViewById(R.id.name);
			TextView subjectView = itemView.findViewById(R.id.subject);
			ImageView groupView = itemView.findViewById(R.id.group);
			AvatarView avatarView = itemView.findViewById(R.id.avatar_view);

			holder.nameView = nameView;
			holder.subjectView = subjectView;
			holder.groupView = groupView;
			holder.avatarView = avatarView;

			itemView.setTag(holder);
			itemView.setOnCheckedChangeListener(new CheckableConstraintLayout.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CheckableConstraintLayout checkableView, boolean isChecked) {
					if (isChecked) {
						checkedItems.add(((RecentListHolder) checkableView.getTag()).originalPosition);
					} else {
						checkedItems.remove(((RecentListHolder) checkableView.getTag()).originalPosition);
					}
				}
			});
		} else {
			holder = (RecentListHolder) itemView.getTag();
		}

		final ConversationModel conversationModel = values.get(position);
		holder.originalPosition = ovalues.indexOf(conversationModel);

		final ContactModel contactModel = conversationModel.getContact();
		final GroupModel groupModel = conversationModel.getGroup();
		final DistributionListModel distributionListModel = conversationModel.getDistributionList();

		String fromtext, subjecttext;

		if(conversationModel.isGroupConversation()) {
			fromtext = NameUtil.getDisplayName(groupModel, this.groupService);
			subjecttext = groupService.getMembersString(groupModel);
			holder.groupView.setImageResource(groupService.isGroupOwner(groupModel) ? (groupService.isNotesGroup(groupModel) ? R.drawable.ic_spiral_bound_booklet_outline : R.drawable.ic_group_outline) : R.drawable.ic_group_filled);
		}
		else if(conversationModel.isDistributionListConversation()) {
			fromtext = NameUtil.getDisplayName(distributionListModel, this.distributionListService);
			subjecttext = context.getString(R.string.distribution_list);
			holder.groupView.setImageResource(R.drawable.ic_bullhorn_outline);
		}
		else {
			fromtext = NameUtil.getDisplayNameOrNickname(contactModel, true);
			subjecttext = contactModel.getIdentity();
			holder.groupView.setImageResource(R.drawable.ic_person_outline);
		}

		String filterString = null;
		if (recentListFilter != null) {
			filterString = recentListFilter.getFilterString();
		}

		holder.nameView.setText(highlightMatches(fromtext, filterString));
		if (conversationModel.isGroupConversation()) {
			AdapterUtil.styleGroup(holder.nameView, groupService, groupModel);
		} else if (conversationModel.isContactConversation()) {
			AdapterUtil.styleContact(holder.nameView, contactModel);
		}

		holder.subjectView.setText(highlightMatches(subjecttext, filterString));

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
				holder
		);

		((ListView)parent).setItemChecked(position, checkedItems.contains(holder.originalPosition));

		return itemView;
	}

	private static class RecentListHolder extends AvatarListItemHolder {
		TextView nameView;
		TextView subjectView;
		ImageView groupView;
		int originalPosition;
	}

	public class RecentListFilter extends Filter {
		String filterString = null;

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults results = new FilterResults();

			if (constraint == null || constraint.length() == 0) {
				// no filtering
				filterString = null;
				results.values = ovalues;
				results.count = ovalues.size();
			} else {
				// perform filtering
				List<ConversationModel> conversationList = new ArrayList<ConversationModel>();
				filterString = constraint.toString();

				for (ConversationModel conversationModel : ovalues) {

					if(conversationModel.isGroupConversation()) {
						String text = NameUtil.getDisplayName(conversationModel.getGroup(), groupService);
						if (text.toUpperCase().contains(filterString.toUpperCase())) {
							conversationList.add(conversationModel);
						}
					}
					else if(conversationModel.isDistributionListConversation()) {
						String text = NameUtil.getDisplayName(conversationModel.getDistributionList(), distributionListService);
						if (text.toUpperCase().contains(filterString.toUpperCase())) {
							conversationList.add(conversationModel);
						}
					}
					else {
						String text = NameUtil.getDisplayNameOrNickname(conversationModel.getContact(), false) + conversationModel.getContact().getIdentity();
						if (text.toUpperCase().contains(filterString.toUpperCase())) {
							conversationList.add(conversationModel);
						}
					}
				}

				results.values = conversationList;
				results.count = conversationList.size();
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			values = (List<ConversationModel>) results.values;
			notifyDataSetChanged();
		}

		public String getFilterString() {
			return filterString;
		}
	}

	@Override
	public Filter getFilter() {
		if (recentListFilter == null)
			recentListFilter = new RecentListFilter();

		return recentListFilter;
	}

	@Override
	public int getCount() {
		return values != null ? values.size() : 0;
	}

	@Override
	public HashSet<?> getCheckedItems() {
		HashSet<Object> conversations = new HashSet<>();

		for (int position: checkedItems) {
			conversations.add(getModel(ovalues.get(position)));
		}
		return conversations;
	}

	@Override
	public Object getClickedItem(View v) {
		return getModel(ovalues.get(((RecentListHolder) v.getTag()).originalPosition));
	}

	private Object getModel(ConversationModel conversationModel) {
		if (conversationModel.isGroupConversation()) {
			return conversationModel.getGroup();
		}
		else if(conversationModel.isDistributionListConversation()) {
			return conversationModel.getDistributionList();
		}
		else {
			return conversationModel.getContact();
		}
	}
}
