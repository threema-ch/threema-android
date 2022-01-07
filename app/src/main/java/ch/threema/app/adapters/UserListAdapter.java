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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.IdListService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.CheckableView;
import ch.threema.app.ui.VerificationLevelImageView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.ContactModel;

public class UserListAdapter extends FilterableListAdapter {
	private final Context context;
	private List<ContactModel> values;
	private List<ContactModel> ovalues;
	private UserListFilter userListFilter;
	private final Bitmap defaultContactImage;
	private final ContactService contactService;
	private final IdListService blacklistService;
	private final DeadlineListService hiddenChatsListService;

	public UserListAdapter(Context context, final List<ContactModel> values, final List<String> preselectedIdentities, final List<Integer> checkedItems, ContactService contactService, IdListService blacklistService, DeadlineListService hiddenChatsListService) {
		super(context, R.layout.item_user_list, (List<Object>) (Object) values);

		this.context = context;
		this.values = values;
		this.ovalues = values;
		this.contactService = contactService;
		this.blacklistService = blacklistService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.defaultContactImage = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_contact);

		if (checkedItems != null && checkedItems.size() > 0) {
			// restore checked items
			this.checkedItems.addAll(checkedItems);
		}
		// validate if preselected items are in dataset
		else if (values != null && preselectedIdentities != null && preselectedIdentities.size() > 0) {
			// TODO do not restore after rotate (members)
			for (int i = 0; i < values.size() ; i++) {
				ContactModel contactModel = values.get(i);
				if (preselectedIdentities.contains(contactModel.getIdentity())) {
					this.checkedItems.add(i);
				}
			}
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		CheckableConstraintLayout itemView = (CheckableConstraintLayout) convertView;

		UserListHolder holder = new UserListHolder();

		if (convertView == null) {
			// This a new view we inflate the new layout
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			itemView = (CheckableConstraintLayout) inflater.inflate(R.layout.item_user_list, parent, false);

			TextView nameView = itemView.findViewById(R.id.name);
			TextView subjectView = itemView.findViewById(R.id.subject);
			VerificationLevelImageView verificationLevelView = itemView.findViewById(R.id.verification_level);
			AvatarView avatarView = itemView.findViewById(R.id.avatar_view);
			ImageView blockedView = itemView.findViewById(R.id.blocked_contact);
			CheckableView checkableView = itemView.findViewById(R.id.check_box);
			TextView dateView = itemView.findViewById(R.id.date);
			ImageView lastMessageView = itemView.findViewById(R.id.last_message_icon);

			holder.nameView = nameView;
			holder.subjectView = subjectView;
			holder.verificationLevelView = verificationLevelView;
			holder.avatarView = avatarView;
			holder.blockedView = blockedView;
			holder.checkableView = checkableView;
			holder.dateView = dateView;
			holder.lastMessageView = lastMessageView;

			itemView.setTag(holder);
			itemView.setOnCheckedChangeListener(new CheckableConstraintLayout.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CheckableConstraintLayout checkableView, boolean isChecked) {
					if (isChecked) {
						checkedItems.add(((UserListHolder) checkableView.getTag()).originalPosition);
					} else {
						checkedItems.remove(((UserListHolder) checkableView.getTag()).originalPosition);
					}
				}
			});
		} else {
			holder = (UserListHolder) itemView.getTag();
		}

		final ContactModel contactModel = values.get(position);
		holder.originalPosition = ovalues.indexOf(contactModel);

		String filterString = null;
		if (userListFilter != null) {
			filterString = userListFilter.getFilterString();
		}
		ViewUtil.showAndSet(
				holder.nameView,
				highlightMatches(NameUtil.getDisplayNameOrNickname(contactModel, true), filterString));
		AdapterUtil.styleContact(holder.nameView, contactModel);

		ViewUtil.showAndSet(
				holder.subjectView,
				highlightMatches(contactModel.getIdentity(), filterString));

		AdapterUtil.styleContact(holder.subjectView, contactModel);

		ViewUtil.show(
				holder.blockedView,
				blacklistService != null && blacklistService.has(contactModel.getIdentity())
		);

		holder.verificationLevelView.setContactModel(contactModel);

		String lastMessageDateString = null;
		MessageReceiver messageReceiver = this.contactService.createReceiver(contactModel);
		if (messageReceiver != null && !hiddenChatsListService.has(messageReceiver.getUniqueIdString())) {
			lastMessageDateString = MessageUtil.getDisplayDate(this.context, ((ContactMessageReceiver) messageReceiver).getLastMessage(), false);
		}

		ViewUtil.showAndSet(
				holder.dateView,
				lastMessageDateString);

		ViewUtil.show(
				holder.lastMessageView,
				!TestUtil.empty(lastMessageDateString));

		// load avatars asynchronously
		AvatarListItemUtil.loadAvatar(
				position,
				contactModel,
				this.defaultContactImage,
				this.contactService,
				holder
		);

		((ListView)parent).setItemChecked(position, checkedItems.contains(holder.originalPosition));

		return itemView;
	}

	private static class UserListHolder extends AvatarListItemHolder{
		TextView nameView;
		TextView subjectView;
		VerificationLevelImageView verificationLevelView;
		ImageView blockedView;
		CheckableView checkableView;
		TextView dateView;
		ImageView lastMessageView;
		int originalPosition;
	}

	public class UserListFilter extends Filter {
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
				List<ContactModel> nContactList = new ArrayList<ContactModel>();
				filterString = constraint.toString();

				for (ContactModel contactModel : ovalues) {
					if ((NameUtil.getDisplayNameOrNickname(contactModel, false).toUpperCase().contains(filterString.toUpperCase())) ||
							(contactModel.getIdentity().toUpperCase().contains(filterString.toUpperCase()))) {
						nContactList.add(contactModel);
					}
				}
				results.values = nContactList;
				results.count = nContactList.size();
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			values = (List<ContactModel>) results.values;
			notifyDataSetChanged();
		}

		public String getFilterString() {
			return filterString;
		}
	}

	@Override
	public Filter getFilter() {
		if (userListFilter == null)
			userListFilter = new UserListFilter();

		return userListFilter;
	}

	@Override
	public int getCount() {
		return values != null ? values.size() : 0;
	}

	@Override
	public HashSet<ContactModel> getCheckedItems() {
		HashSet<ContactModel> contacts = new HashSet<>();
		ContactModel contactModel;

		for (int position: checkedItems) {
			contactModel = ovalues.get(position);
			if (contactModel != null) {
				contacts.add(contactModel);
			}
		}
		return contacts;
	}

	@Override
	public ContactModel getClickedItem(View v) {
		return ovalues.get(((UserListHolder) v.getTag()).originalPosition);
	}
}
