/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.bumptech.glide.RequestManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
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
import ch.threema.domain.models.Contact;
import ch.threema.storage.models.ContactModel;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

public class UserListAdapter extends FilterableListAdapter {
    private final Context context;
    private List<ContactModel> values;
    private final List<ContactModel> originalValues;
    private UserListFilter userListFilter;
    private final ContactService contactService;
    private final BlockedIdentitiesService blockedIdentitiesService;
    private final DeadlineListService hiddenChatsListService;
    private final FilterResultsListener filterResultsListener;
    private final @NonNull RequestManager requestManager;

    public UserListAdapter(
        Context context,
        @NonNull final List<ContactModel> values,
        @Nullable final List<String> preselectedIdentities,
        final List<Integer> checkedItems,
        ContactService contactService,
        BlockedIdentitiesService blockedIdentitiesService,
        DeadlineListService hiddenChatsListService,
        PreferenceService preferenceService,
        FilterResultsListener filterResultsListener,
        @NonNull RequestManager requestManager
    ) {
        super(context, R.layout.item_user_list, (List<Object>) (Object) values);

        this.context = context;
        this.contactService = contactService;
        this.hiddenChatsListService = hiddenChatsListService;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.filterResultsListener = filterResultsListener;
        this.requestManager = requestManager;

        this.values = new ArrayList<>(values);
        this.values.addAll(getMissingPreselectedContacts(values, preselectedIdentities));
        Collections.sort(
            this.values,
            ContactUtil.getContactComparator(preferenceService.isContactListSortingFirstName())
        );
        this.originalValues = new ArrayList<>(this.values);

        setCheckedItems(preselectedIdentities, checkedItems);
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
            itemView.setOnCheckedChangeListener((checkableView1, isChecked) -> {
                if (isChecked) {
                    checkedItems.add(((UserListHolder) checkableView1.getTag()).originalPosition);
                } else {
                    checkedItems.remove(((UserListHolder) checkableView1.getTag()).originalPosition);
                }
            });
        } else {
            holder = (UserListHolder) itemView.getTag();
        }

        final ContactModel contactModel = values.get(position);
        holder.originalPosition = originalValues.indexOf(contactModel);

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
            blockedIdentitiesService != null && blockedIdentitiesService.isBlocked(contactModel.getIdentity())
        );

        holder.verificationLevelView.setVerificationLevel(
            contactModel.verificationLevel,
            contactModel.getWorkVerificationLevel()
        );

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
            !TestUtil.isEmptyOrNull(lastMessageDateString));

        // load avatars asynchronously
        AvatarListItemUtil.loadAvatar(
            contactModel,
            this.contactService,
            holder,
            requestManager
        );

        position += ((ListView) parent).getHeaderViewsCount();

        ((ListView) parent).setItemChecked(position, checkedItems.contains(holder.originalPosition));

        return itemView;
    }

    private static class UserListHolder extends AvatarListItemHolder {
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
                results.values = originalValues;
                results.count = originalValues.size();
            } else {
                // perform filtering
                List<ContactModel> nContactList = new ArrayList<>();
                filterString = constraint.toString();

                for (ContactModel contactModel : originalValues) {
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
            if (filterResultsListener != null) {
                filterResultsListener.onResultsAvailable(TestUtil.isBlankOrNull(constraint) ? 0 : results.count);
            }
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

        for (int position : checkedItems) {
            contactModel = originalValues.get(position);
            if (contactModel != null) {
                contacts.add(contactModel);
            }
        }
        return contacts;
    }

    @Override
    public ContactModel getClickedItem(View v) {
        return originalValues.get(((UserListHolder) v.getTag()).originalPosition);
    }

    private List<ContactModel> getMissingPreselectedContacts(@NonNull List<ContactModel> contacts, @Nullable List<String> preselectedIdentities) {
        if (preselectedIdentities != null) {
            Set<String> includedIds = StreamSupport.stream(contacts)
                .map(Contact::getIdentity)
                .collect(Collectors.toSet());
            return StreamSupport.stream(preselectedIdentities)
                .filter(identity -> !includedIds.contains(identity))
                .map(contactService::getByIdentity)
                .filter(contact -> contact != null)
                .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private void setCheckedItems(@Nullable List<String> preselectedIdentities, List<Integer> checkedItems) {
        if (checkedItems != null && checkedItems.size() > 0) {
            // restore checked items
            this.checkedItems.addAll(checkedItems);
        }
        // validate if preselected items are in dataset
        else if (preselectedIdentities != null && preselectedIdentities.size() > 0) {
            for (int i = 0; i < values.size(); i++) {
                ContactModel contactModel = values.get(i);
                if (preselectedIdentities.contains(contactModel.getIdentity())) {
                    this.checkedItems.add(i);
                }
            }
        }
    }
}
