/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.chip.Chip;

import java.util.HashMap;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.InitialAvatarView;
import ch.threema.app.utils.TestUtil;
import ch.threema.client.work.WorkDirectoryCategory;
import ch.threema.client.work.WorkDirectoryContact;
import ch.threema.client.work.WorkOrganization;

public class DirectoryAdapter extends PagedListAdapter<WorkDirectoryContact, RecyclerView.ViewHolder> {
	private final Context context;
	private final LayoutInflater inflater;
	private final PreferenceService preferenceService;
	private final ContactService contactService;
	private final WorkOrganization workOrganization;
	private final HashMap<String, String> categoryMap = new HashMap<>();
	private DirectoryAdapter.OnClickItemListener onClickItemListener;
	@DrawableRes private int backgroundRes;

	private static class DirectoryHolder extends RecyclerView.ViewHolder {
		private final TextView nameView;
		private final TextView identityView;
		private final AppCompatImageView statusImageView;
		private final InitialAvatarView avatarView;
		private final TextView categoriesView;
		private final Chip organizationView;
		protected WorkDirectoryContact contact;

		private DirectoryHolder(final View itemView) {
			super(itemView);

			this.nameView = itemView.findViewById(R.id.name);
			this.identityView = itemView.findViewById(R.id.identity);
			this.statusImageView = itemView.findViewById(R.id.status);
			this.avatarView = itemView.findViewById(R.id.avatar_view);
			this.categoriesView = itemView.findViewById(R.id.categories);
			this.organizationView = itemView.findViewById(R.id.organization);
		}

		public View getItem() {
			return itemView;
		}
	}

	public DirectoryAdapter(Context context, PreferenceService preferenceService, ContactService contactService, List<WorkDirectoryCategory> categoryList) {
		super(DIFF_CALLBACK);

		this.context = context;
		this.inflater = LayoutInflater.from(context);
		this.preferenceService = preferenceService;
		this.contactService = contactService;
		this.workOrganization = preferenceService.getWorkOrganization();

		for(WorkDirectoryCategory category: categoryList) {
			this.categoryMap.put(category.id, category.name);
		}

		TypedValue outValue = new TypedValue();
		context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
		this.backgroundRes = outValue.resourceId;
	}

	public interface OnClickItemListener {
		void onClick(WorkDirectoryContact workDirectoryContact, int position);
		void onAdd(WorkDirectoryContact workDirectoryContact, int position);
	}

	public DirectoryAdapter setOnClickItemListener(DirectoryAdapter.OnClickItemListener onClickItemListener) {
		this.onClickItemListener = onClickItemListener;
		return this;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
		View itemView = inflater.inflate(R.layout.item_directory, viewGroup, false);
		itemView.setBackgroundResource(R.drawable.listitem_background_selector);
		return new DirectoryHolder(itemView);
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
		final DirectoryHolder holder = (DirectoryHolder) viewHolder;

		final WorkDirectoryContact workDirectoryContact = this.getItem(position);

		if (workDirectoryContact == null) {
			return;
		}

		holder.contact = workDirectoryContact;

		if (this.onClickItemListener != null) {
			holder.statusImageView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onClickItemListener.onAdd(holder.contact, viewHolder.getAdapterPosition());
				}
			});
			holder.itemView.setOnClickListener(v -> onClickItemListener.onClick(holder.contact, viewHolder.getAdapterPosition()));
		}

		String name;
		if (preferenceService.isContactFormatFirstNameLastName()) {
			name = (workDirectoryContact.firstName != null ? workDirectoryContact.firstName + " " : "") +
				(workDirectoryContact.lastName != null ? workDirectoryContact.lastName : "");
		}
		else {
			name = (workDirectoryContact.lastName != null ? workDirectoryContact.lastName + " " : "") +
				(workDirectoryContact.firstName != null ? workDirectoryContact.firstName : "");
		}

		if (!TestUtil.empty(workDirectoryContact.csi)) {
			name += " " + workDirectoryContact.csi;
		}

		holder.nameView.setText(name);

		StringBuilder categoriesBuilder = new StringBuilder("");
		if (workDirectoryContact.categoryIds.size() > 0) {
			int count = 0;
			for (String categoryId: workDirectoryContact.categoryIds) {
				if (count != 0) {
					categoriesBuilder.append(", ");
				}
				categoriesBuilder.append(categoryMap.get(categoryId));
				count++;
			}
		}
		holder.categoriesView.setText(categoriesBuilder.toString());
		holder.avatarView.setInitials(workDirectoryContact.firstName, workDirectoryContact.lastName);
		holder.identityView.setText(workDirectoryContact.threemaId);

		if (workDirectoryContact.organization != null &&
			workDirectoryContact.organization.getName() != null &&
			!workDirectoryContact.organization.getName().equals(workOrganization.getName())) {
			holder.organizationView.setText(workDirectoryContact.organization.getName());
			holder.organizationView.setVisibility(View.VISIBLE);
		} else {
			holder.organizationView.setVisibility(View.GONE);
		}

		boolean isAddedContact = contactService.getByIdentity(workDirectoryContact.threemaId) != null;

		holder.statusImageView.setBackgroundResource(isAddedContact ? 0 : this.backgroundRes);
		holder.statusImageView.setImageResource(isAddedContact ? R.drawable.ic_keyboard_arrow_right_black_24dp : R.drawable.ic_add_circle_outline_black_24dp);
		holder.statusImageView.setClickable(!isAddedContact);
		holder.statusImageView.setFocusable(!isAddedContact);
	}

	private static final DiffUtil.ItemCallback<WorkDirectoryContact> DIFF_CALLBACK =
			new DiffUtil.ItemCallback<WorkDirectoryContact>() {
				@Override
				public boolean areItemsTheSame(@NonNull WorkDirectoryContact oldItem, @NonNull WorkDirectoryContact newItem) {
					return oldItem.threemaId != null && oldItem.threemaId.equals(newItem.threemaId);
				}

				@Override
				public boolean areContentsTheSame(@NonNull WorkDirectoryContact oldItem, @NonNull WorkDirectoryContact newItem) {
					return oldItem.threemaId != null && oldItem.threemaId.equals(newItem.threemaId);
				}
			};
}
