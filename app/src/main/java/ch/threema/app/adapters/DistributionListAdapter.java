/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.NameUtil;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListAdapter extends FilterableListAdapter {
	private final Context context;
	private List<DistributionListModel> values;
	private List<DistributionListModel> ovalues;
	private DistributionListFilter groupListFilter;
	private final Bitmap defaultAvatar;
	private final DistributionListService distributionListService;

	public DistributionListAdapter(Context context, List<DistributionListModel> values, List<Integer> checkedItems, DistributionListService distributionListService) {
		super(context, R.layout.item_distribution_list, (List<Object>) (Object) values);

		this.context = context;
		this.values = values;
		this.ovalues = values;
		this.distributionListService = distributionListService;
		this.defaultAvatar = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_distribution_list);

		if (checkedItems != null && checkedItems.size() > 0) {
			// restore checked items
			this.checkedItems.addAll(checkedItems);
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		CheckableConstraintLayout itemView = (CheckableConstraintLayout) convertView;

		DistributionListHolder holder = new DistributionListHolder();

		if (convertView == null) {
			// This a new view we inflate the new layout
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			itemView = (CheckableConstraintLayout) inflater.inflate(R.layout.item_distribution_list, parent, false);

			TextView nameView = itemView.findViewById(R.id.name);
			TextView subjectView = itemView.findViewById(R.id.subject);
			AvatarView avatarView = itemView.findViewById(R.id.avatar_view);

			holder.nameView = nameView;
			holder.subjectView = subjectView;
			holder.avatarView = avatarView;

			itemView.setTag(holder);
			itemView.setOnCheckedChangeListener(new CheckableConstraintLayout.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CheckableConstraintLayout checkableView, boolean isChecked) {
					if (isChecked) {
						checkedItems.add(((DistributionListHolder) checkableView.getTag()).originalPosition);
					} else {
						checkedItems.remove(((DistributionListHolder) checkableView.getTag()).originalPosition);
					}
				}
			});
		} else {
			holder = (DistributionListHolder) itemView.getTag();
		}

		final DistributionListModel distributionListModel = values.get(position);
		holder.originalPosition = ovalues.indexOf(distributionListModel);

		String filterString = null;
		if (groupListFilter != null) {
			filterString = groupListFilter.getFilterString();
		}

		holder.nameView.setText(highlightMatches(NameUtil.getDisplayName(distributionListModel, this.distributionListService), filterString));
		holder.subjectView.setText(this.distributionListService.getMembersString(distributionListModel));

		// load avatars asynchronously
		AvatarListItemUtil.loadAvatar(
				position,
				distributionListModel,
				this.defaultAvatar,
				this.distributionListService,
				holder
		);

		((ListView)parent).setItemChecked(position, checkedItems.contains(holder.originalPosition));

		return itemView;
	}

	private static class DistributionListHolder extends AvatarListItemHolder {
		public TextView nameView;
		public TextView subjectView;
		public int originalPosition;
	}

	public class DistributionListFilter extends Filter {
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
				filterString = constraint.toString();

				Collection<DistributionListModel> distributionListModelList = Functional.filter(ovalues, new IPredicateNonNull<DistributionListModel>() {
					@Override
					public boolean apply(@NonNull DistributionListModel distributionListModel) {
						return (NameUtil.getDisplayName(distributionListModel, distributionListService).toUpperCase().contains(filterString.toUpperCase()));
					}
				});

				results.values = distributionListModelList;
				results.count = distributionListModelList.size();
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			values = (List<DistributionListModel>) results.values;
			notifyDataSetChanged();
		}

		public String getFilterString() {
			return filterString;
		}
	}

	@Override
	public Filter getFilter() {
		if (groupListFilter == null)
			groupListFilter = new DistributionListFilter();

		return groupListFilter;
	}

	@Override
	public int getCount() {
		return values != null ? values.size() : 0;
	}

	@Override
	public HashSet<DistributionListModel> getCheckedItems() {
		HashSet<DistributionListModel> distributionLists = new HashSet<>();
		DistributionListModel distributionListModel;

		for (int position: checkedItems) {
			distributionListModel = ovalues.get(position);
			if (distributionListModel != null) {
				distributionLists.add(distributionListModel);
			}
		}
		return distributionLists;
	}

	@Override
	public DistributionListModel getClickedItem(View v) {
		return ovalues.get(((DistributionListHolder) v.getTag()).originalPosition);
	}
}
