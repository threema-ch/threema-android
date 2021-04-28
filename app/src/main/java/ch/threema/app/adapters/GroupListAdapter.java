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
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.storage.models.GroupModel;

public class GroupListAdapter extends FilterableListAdapter {
	private final Context context;
	private List<GroupModel> values;
	private List<GroupModel> ovalues;
	private GroupListFilter groupListFilter;
	private final Bitmap defaultGroupImage;
	private final GroupService groupService;

	public GroupListAdapter(Context context, List<GroupModel> values, List<Integer> checkedItems, GroupService groupService) {
		super(context, R.layout.item_group_list, (List<Object>) (Object) values);

		this.context = context;
		this.values = values;
		this.ovalues = values;
		this.groupService = groupService;
		this.defaultGroupImage = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_group);

		if (checkedItems != null && checkedItems.size() > 0) {
			// restore checked items
			this.checkedItems.addAll(checkedItems);
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		CheckableConstraintLayout itemView = (CheckableConstraintLayout) convertView;

		GroupListHolder holder = new GroupListHolder();

		if (convertView == null) {
			// This a new view we inflate the new layout
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			itemView = (CheckableConstraintLayout) inflater.inflate(R.layout.item_group_list, parent, false);

			TextView nameView = itemView.findViewById(R.id.name);
			TextView subjectView = itemView.findViewById(R.id.subject);
			ImageView roleView = itemView.findViewById(R.id.role);
			AvatarView avatarView = itemView.findViewById(R.id.avatar_view);

			holder.nameView = nameView;
			holder.subjectView = subjectView;
			holder.roleView = roleView;
			holder.avatarView = avatarView;

			itemView.setTag(holder);
			itemView.setOnCheckedChangeListener(new CheckableConstraintLayout.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CheckableConstraintLayout checkableView, boolean isChecked) {
					if (isChecked) {
						checkedItems.add(((GroupListHolder) checkableView.getTag()).originalPosition);
					} else {
						checkedItems.remove(((GroupListHolder) checkableView.getTag()).originalPosition);
					}
				}
			});
		} else {
			holder = (GroupListHolder) itemView.getTag();
		}

		final GroupModel groupModel = values.get(position);
		holder.originalPosition = ovalues.indexOf(groupModel);

		String filterString = null;
		if (groupListFilter != null) {
			filterString = groupListFilter.getFilterString();
		}

		holder.nameView.setText(TextUtil.highlightMatches(context, NameUtil.getDisplayName(groupModel, this.groupService), filterString, false, false));
		AdapterUtil.styleGroup(holder.nameView, groupService, groupModel);

		holder.subjectView.setText(this.groupService.getMembersString(groupModel));
 		holder.roleView.setImageResource(groupService.isGroupOwner(groupModel)
		    ? (groupService.isNotesGroup(groupModel) ? R.drawable.ic_spiral_bound_booklet_outline : R.drawable.ic_group_outline)
		    : R.drawable.ic_group_filled);

		// load avatars asynchronously
		AvatarListItemUtil.loadAvatar(
				position,
				groupModel,
				this.defaultGroupImage,
				this.groupService,
				holder
		);

		((ListView)parent).setItemChecked(position, checkedItems.contains(holder.originalPosition));

		return itemView;
	}

	private static class GroupListHolder extends AvatarListItemHolder {
		public TextView nameView;
		private TextView subjectView;
		private ImageView roleView;
		private int originalPosition;
	}

	public class GroupListFilter extends Filter {
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
				List<GroupModel> nGroupList = new ArrayList<GroupModel>();
				filterString = constraint.toString();

				for (GroupModel groupModel : ovalues) {
					if (NameUtil.getDisplayName(groupModel, groupService).toUpperCase().contains(filterString.toUpperCase())) {
						nGroupList.add(groupModel);
					}
				}
				results.values = nGroupList;
				results.count = nGroupList.size();
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			values = (List<GroupModel>) results.values;
			notifyDataSetChanged();
		}

		public String getFilterString() {
			return filterString;
		}
	}

	@Override
	public Filter getFilter() {
		if (groupListFilter == null)
			groupListFilter = new GroupListFilter();

		return groupListFilter;
	}

	@Override
	public int getCount() {
		return values != null ? values.size() : 0;
	}

	@Override
	public HashSet<GroupModel> getCheckedItems() {
		HashSet<GroupModel> groups = new HashSet<>();
		GroupModel groupModel;

		for (int position: checkedItems) {
			groupModel = ovalues.get(position);
			if (groupModel != null) {
				groups.add(groupModel);
			}
		}
		return groups;
	}

	@Override
	public GroupModel getClickedItem(View v) {
		return ovalues.get(((GroupListHolder) v.getTag()).originalPosition);
	}
}
