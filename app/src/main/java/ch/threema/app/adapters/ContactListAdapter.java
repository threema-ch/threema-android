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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.RequestManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.VerificationLevelImageView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;

public class ContactListAdapter extends FilterableListAdapter implements SectionIndexer {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ContactListAdapter");

    private static final int MAX_RECENTLY_ADDED_CONTACTS = 1;

    private final ContactService contactService;
    private final PreferenceService preferenceService;
    @NonNull
    private final BlockedIdentitiesService blockedIdentitiesService;

    public static final int VIEW_TYPE_NORMAL = 0;
    public static final int VIEW_TYPE_RECENTLY_ADDED = 1;
    public static final int VIEW_TYPE_COUNT = 2;

    private static final String PLACEHOLDER_BLANK_HEADER = " ";
    private static final String PLACEHOLDER_CHANNELS = "\uffff";
    private static final String PLACEHOLDER_RECENTLY_ADDED = "\u0001";
    private static final String CHANNEL_SIGN = "\u002a";
    public static final String RECENTLY_ADDED_SIGN = "+";

    private List<ContactModel> values, ovalues, recentlyAdded = new ArrayList<>();
    private ContactListFilter contactListFilter;
    private final AvatarListener avatarListener;
    private final HashMap<String, Integer> alphaIndexer = new HashMap<String, Integer>();
    private final HashMap<Integer, String> positionIndexer = new HashMap<Integer, String>();
    private String[] sections;
    private Integer[] counts;
    private final LayoutInflater inflater;
    private final Collator collator;
    private final @NonNull RequestManager requestManager;

    public interface AvatarListener {
        void onAvatarClick(View view, int position);

        boolean onAvatarLongClick(View view, int position);

        void onRecentlyAddedClick(ContactModel contactModel);
    }

    public ContactListAdapter(
        @NonNull Context context,
        @NonNull List<ContactModel> values,
        ContactService contactService,
        PreferenceService preferenceService,
        BlockedIdentitiesService blockedIdentitiesService,
        AvatarListener avatarListener,
        @NonNull RequestManager requestManager
    ) {
        super(context, R.layout.item_contact_list, (List<Object>) (Object) values);

        this.values = updateRecentlyAdded(values);
        this.ovalues = this.values;
        this.contactService = contactService;
        this.preferenceService = preferenceService;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.avatarListener = avatarListener;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        this.collator = Collator.getInstance();
        this.collator.setStrength(Collator.PRIMARY);

        this.requestManager = requestManager;

        setupIndexer();
    }

    private List<ContactModel> updateRecentlyAdded(List<ContactModel> all) {
        ArrayList<ContactModel> recents = new ArrayList<>();
        Date recentlyAddedDate = new Date(System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS);

        for (ContactModel contactModel : all) {
            if (contactModel != null && contactModel.getDateCreated() != null && recentlyAddedDate.before(contactModel.getDateCreated()) && !"ECHOECHO".equalsIgnoreCase(contactModel.getIdentity())) {
                recents.add(contactModel);
            }
        }

        if (!recents.isEmpty() && recents.size() < 10) {
            // filter latest
            Collections.sort(recents, (o1, o2) -> o2.getDateCreated().compareTo(o1.getDateCreated()));
            this.recentlyAdded = recents.subList(0, Math.min(recents.size(), MAX_RECENTLY_ADDED_CONTACTS));

            all.addAll(0, this.recentlyAdded);
        } else {
            this.recentlyAdded.clear();
        }
        return all;
    }

    public void updateData(@NonNull List<ContactModel> all) {
        setNotifyOnChange(false);
        this.values = updateRecentlyAdded(all);
        this.ovalues = this.values;
        setupIndexer();
        setNotifyOnChange(true);
        notifyDataSetChanged();
    }

    private boolean containsKeyLocaleAware(String newKey) {
        for (String key : alphaIndexer.keySet()) {
            if (collator.equals(key, newKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get Unicode-aware index character for headers and thumbscroller
     *
     * @param input Input string
     * @return Unicode character at beginning of input
     */
    private String getIndexCharacter(String input) {
        try {
            int codePoint = Character.codePointAt(input, 0);
            return input.substring(0, Character.charCount(codePoint)).toUpperCase();
        } catch (Exception e) {
            return input.substring(0, 1).toUpperCase();
        }
    }

    private void setupIndexer() {
        int size = values.size();
        String firstLetter;

        alphaIndexer.clear();
        positionIndexer.clear();

        // create index for fast scroll
        for (int i = 0; i < size; i++) {
            ContactModel c = values.get(i);

            if (c == null) {
                // this case only happens if setupList() is called on
                // values that already have headers added
                values.remove(i);
                i--;
                size--;
                continue;
            }

            firstLetter = getInitial(c, false, i);

            if (PLACEHOLDER_BLANK_HEADER.equals(firstLetter) ||
                PLACEHOLDER_CHANNELS.equals(firstLetter) ||
                PLACEHOLDER_RECENTLY_ADDED.equals(firstLetter)) {
                // placeholders
                if (!alphaIndexer.containsKey(firstLetter)) {
                    alphaIndexer.put(firstLetter, i);
                    positionIndexer.put(i, firstLetter);
                }
            } else {
                if (!containsKeyLocaleAware(firstLetter)) {
                    firstLetter = Normalizer.normalize(firstLetter, Normalizer.Form.NFD);
                    alphaIndexer.put(firstLetter, i);
                    positionIndexer.put(i, firstLetter);
                }
            }
        }

        // create a list from the set to sort
        ArrayList<String> sectionList = new ArrayList<String>(alphaIndexer.keySet());
        Collections.sort(sectionList, collator);
        if (sectionList.contains(PLACEHOLDER_CHANNELS)) {
            // replace channels placeholder by star sign AFTER sorting
            sectionList.set(sectionList.indexOf(PLACEHOLDER_CHANNELS), CHANNEL_SIGN);
            if (alphaIndexer.containsKey(PLACEHOLDER_CHANNELS)) {
                alphaIndexer.put(CHANNEL_SIGN, alphaIndexer.get(PLACEHOLDER_CHANNELS));
                alphaIndexer.remove(PLACEHOLDER_CHANNELS);
            }
        }
        sections = new String[sectionList.size()];
        sectionList.toArray(sections);

        // create array for reverse lookup
        ArrayList<Integer> countsList = new ArrayList<Integer>(positionIndexer.keySet());
        Collections.sort(countsList);
        counts = new Integer[countsList.size()];
        countsList.toArray(counts);
    }

    private String getInitial(ContactModel c, boolean afterSorting, int position) {
        String firstLetter, sortingValue;

        sortingValue = ContactUtil.getSafeNameString(c, preferenceService.isContactListSortingFirstName());
        if (sortingValue.isEmpty()) {
            firstLetter = PLACEHOLDER_BLANK_HEADER;
        } else {
            if (ContactUtil.isGatewayContact(c)) {
                firstLetter = afterSorting ? CHANNEL_SIGN : PLACEHOLDER_CHANNELS;
            } else if (getItemViewType(position) == VIEW_TYPE_RECENTLY_ADDED) {
                if (contactListFilter != null && contactListFilter.getFilterString() != null) {
                    if (position > 0) {
                        for (int i = Math.min(position - 1, MAX_RECENTLY_ADDED_CONTACTS - 1); i >= 0; i--) {
                            if (values.get(i).equals(values.get(position))) {
                                return getIndexCharacter(sortingValue);
                            }
                        }
                    }
                }
                firstLetter = afterSorting ? RECENTLY_ADDED_SIGN : PLACEHOLDER_RECENTLY_ADDED;
            } else {
                firstLetter = getIndexCharacter(sortingValue);
            }
        }
        return firstLetter;
    }

    private static class ContactListHolder extends AvatarListItemHolder {
        TextView contactTextTopLeft;
        TextView contactTextBottomLeft;
        TextView contactTextBottomRight;
        VerificationLevelImageView verificationLevelView;
        ImageView blockedContactView;
        EmojiTextView initialView;
        ImageView initialImageView;
        int originalPosition;
        int viewType;
        ShapeableImageView shapeableAvatarView;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        final int viewType = getItemViewType(position);
        ConstraintLayout itemView = (ConstraintLayout) convertView;

        ContactListHolder holder;

        if (convertView == null) {
            // This a new view we inflate the new layout
            holder = new ContactListHolder();

            itemView = (ConstraintLayout) inflater.inflate(
                viewType == VIEW_TYPE_RECENTLY_ADDED ?
                    R.layout.item_contact_list_recently_added :
                    R.layout.item_contact_list, parent, false
            );

            holder.contactTextTopLeft = itemView.findViewById(R.id.contact_text_top_left);
            holder.contactTextBottomLeft = itemView.findViewById(R.id.contact_text_bottom_left);
            holder.contactTextBottomRight = itemView.findViewById(R.id.contact_text_bottom_right);

            holder.verificationLevelView = itemView.findViewById(R.id.verification_level);
            holder.avatarView = itemView.findViewById(R.id.avatar_view);
            holder.shapeableAvatarView = itemView.findViewById(R.id.shapeable_avatar_view);
            holder.blockedContactView = itemView.findViewById(R.id.blocked_contact);
            holder.initialView = itemView.findViewById(R.id.initial);
            holder.initialImageView = itemView.findViewById(R.id.initial_image);

            itemView.setTag(holder);

            if (viewType == VIEW_TYPE_NORMAL) {
                ((CheckableConstraintLayout) itemView).setOnCheckedChangeListener((checkableView, isChecked) -> {
                    if (isChecked) {
                        checkedItems.add(((ContactListHolder) checkableView.getTag()).originalPosition);
                    } else {
                        checkedItems.remove(((ContactListHolder) checkableView.getTag()).originalPosition);
                    }
                });
            } else if (viewType == VIEW_TYPE_RECENTLY_ADDED) {
                int cornerSize = getContext().getResources().getDimensionPixelSize(R.dimen.recently_added_background_corner_size);
                int recentlyAddedLastPosition = recentlyAdded.size() - 1;

                ShapeAppearanceModel shapeAppearanceModel = new ShapeAppearanceModel.Builder()
                    .setTopLeftCornerSize(position == 0 ? cornerSize : 0)
                    .setTopRightCornerSize(position == 0 ? cornerSize : 0)
                    .setBottomLeftCornerSize(position == recentlyAddedLastPosition ? cornerSize : 0)
                    .setBottomRightCornerSize(position == recentlyAddedLastPosition ? cornerSize : 0)
                    .build();

                MaterialCardView cardView = itemView.findViewById(R.id.recently_added_background);
                cardView.setShapeAppearanceModel(shapeAppearanceModel);
                cardView.setOnClickListener(v -> {
                    avatarListener.onRecentlyAddedClick(values.get(position));
                });
            }
        } else {
            holder = (ContactListHolder) itemView.getTag();
            if (holder.avatarView != null) {
                try {
                    requestManager.clear(holder.avatarView);
                } catch (IllegalArgumentException e) {
                    logger.debug("Invalid destination view");
                }
            }
        }

        final ContactModel contactModel = values.get(position);
        holder.originalPosition = ovalues.indexOf(contactModel);

        String filterString = null;
        if (contactListFilter != null) {
            filterString = contactListFilter.getFilterString();
        }

        // Text slot top left
        final @NonNull String contactTextTopLeft = contactModel.getContactListItemTextTopLeft(preferenceService.isContactFormatFirstNameLastName());
        final @NonNull Spannable contactTextTopLeftSpannable = (viewType != VIEW_TYPE_RECENTLY_ADDED)
            ? highlightMatches(contactTextTopLeft, filterString, true)
            : new SpannableString(contactTextTopLeft);
        ViewUtil.showAndSet(
            holder.contactTextTopLeft,
            contactTextTopLeftSpannable
        );
        AdapterUtil.styleContact(holder.contactTextTopLeft, contactModel);

        // Text slot bottom left
        final @NonNull String contactTextBottomLeft = contactModel.getContactListItemTextBottomLeft();
        final @NonNull Spannable contactTextBottomLeftSpannable = (viewType != VIEW_TYPE_RECENTLY_ADDED)
            ? highlightMatches(contactTextBottomLeft, filterString, true)
            : new SpannableString(contactTextBottomLeft);
        ViewUtil.showAndSet(
            holder.contactTextBottomLeft,
            contactTextBottomLeftSpannable
        );
        AdapterUtil.styleContact(holder.contactTextBottomLeft, contactModel);

        // Text slot bottom right
        final @NonNull String contactTextBottomRight = contactModel.getContactListItemTextBottomRight();
        ViewUtil.showAndSet(
            holder.contactTextBottomRight,
            highlightMatches(contactTextBottomRight, filterString, true)
        );
        AdapterUtil.styleContact(holder.contactTextBottomRight, contactModel);

        if (holder.verificationLevelView != null) {
            holder.verificationLevelView.setVerificationLevel(
                contactModel.verificationLevel,
                contactModel.getWorkVerificationLevel()
            );
        }

        ViewUtil.show(
            holder.blockedContactView,
            blockedIdentitiesService.isBlocked(contactModel.getIdentity())
        );

        if (viewType == VIEW_TYPE_RECENTLY_ADDED) {
            contactService.loadAvatarIntoImage(
                contactModel,
                holder.shapeableAvatarView,
                new AvatarOptions.Builder()
                    .setHighRes(true)
                    .toOptions(),
                requestManager
            );
            holder.viewType = VIEW_TYPE_RECENTLY_ADDED;
        } else {
            AvatarListItemUtil.loadAvatar(
                contactModel,
                this.contactService,
                holder,
                requestManager
            );
            holder.avatarView.setContentDescription(
                ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
                    ThreemaApplication.getAppContext().getString(R.string.mime_contact),
                    contactTextTopLeft
                )
            );
            holder.avatarView.setOnClickListener(v -> avatarListener.onAvatarClick(v, position));
            holder.avatarView.setOnLongClickListener(v -> avatarListener.onAvatarLongClick(v, position));
            holder.viewType = VIEW_TYPE_NORMAL;
        }

        String previousInitial = PLACEHOLDER_CHANNELS;
        String currentInitial = getInitial(contactModel, true, position);
        if (position > 0) {
            previousInitial = getInitial(values.get(position - 1), true, position - 1);
        }

        if (holder.initialView != null) {
            if (previousInitial != null && !previousInitial.equals(currentInitial)) {
                if (!RECENTLY_ADDED_SIGN.equals(currentInitial)) {
                    holder.initialView.setText(currentInitial);
                    holder.initialView.setVisibility(View.VISIBLE);
                    holder.initialImageView.setVisibility(View.GONE);
                }
            } else {
                if (!RECENTLY_ADDED_SIGN.equals(currentInitial)) {
                    holder.initialView.setVisibility(View.GONE);
                    holder.initialImageView.setVisibility(View.GONE);
                }
            }
        }

        return itemView;
    }

    @Override
    public int getItemViewType(int position) {
        ContactModel c = values.get(position);

        if (recentlyAdded != null && !recentlyAdded.isEmpty() && position < recentlyAdded.size() && recentlyAdded.contains(c)) {
            return VIEW_TYPE_RECENTLY_ADDED;
        }
        return VIEW_TYPE_NORMAL;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public int getPositionForSection(int section) {
        if (section < 0 || section >= sections.length) {
            return -1;
        }

        return alphaIndexer.get(sections[section]);
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position < 0 || position >= values.size()) {
            return -1;
        }
        int index = Arrays.binarySearch(counts, position);

        /*
         * Consider this example: section positions are 0, 3, 5; the supplied
         * position is 4. The section corresponding to position 4 starts at
         * position 3, so the expected return value is 1. Binary search will not
         * find 4 in the array and thus will return -insertPosition-1, i.e. -3.
         * To get from that number to the expected value of 1 we need to negate
         * and subtract 2.
         */
        return index >= 0 ? index : -index - 2;
    }

    @Override
    public Object[] getSections() {
        return sections;
    }

    public class ContactListFilter extends Filter {
        String filterString = null;

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults filterResults = new FilterResults();

            if (constraint == null || constraint.length() == 0) {
                // no filtering
                filterString = null;
                filterResults.values = ovalues;
                filterResults.count = ovalues.size();
            } else {
                // perform filtering
                filterString = constraint.toString();
                List<ContactModel> filteredContacts = ovalues.stream()
                    .filter(
                        contactModel -> contactModel != null && contactModel.matchesFilterQuery(preferenceService, filterString)
                    ).collect(Collectors.toList());
                filterResults.values = filteredContacts;
                filterResults.count = filteredContacts.size();
            }
            return filterResults;
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

    @NotNull
    @Override
    public Filter getFilter() {
        if (contactListFilter == null)
            contactListFilter = new ContactListFilter();

        return contactListFilter;
    }

    @Override
    public int getCount() {
        return values != null ? values.size() : 0;
    }

    @Override
    public HashSet<ContactModel> getCheckedItems() {
        HashSet<ContactModel> contacts = new HashSet<>();
        ContactModel contactModel;

        Iterator<Integer> iterator = checkedItems.iterator();
        while (iterator.hasNext()) {
            int position = iterator.next();
            try {
                contactModel = ovalues.get(position);
                if (contactModel != null) {
                    contacts.add(contactModel);
                }
            } catch (IndexOutOfBoundsException e) {
                iterator.remove();
            }
        }
        return contacts;
    }

    @Override
    public ContactModel getClickedItem(View v) {
        if (!ovalues.isEmpty()) {
            return ovalues.get(getClickedItemPosition(v));
        }
        return null;
    }

    public int getClickedItemPosition(View v) {
        if (v != null && v.getTag() != null) {
            return ((ContactListHolder) v.getTag()).originalPosition;
        }
        return 0;
    }

    public int getViewTypeFromView(View v) {
        if (v != null && v.getTag() != null) {
            return ((ContactListHolder) v.getTag()).viewType;
        }
        return VIEW_TYPE_NORMAL;
    }

    public String getInitial(int position) {
        if (position < values.size() && position > 0) {
            return getInitial(values.get(position), true, position);
        }
        return "";
    }

    @Override
    public boolean isEmpty() {
        return values != null && getCount() == 0;
    }
}
