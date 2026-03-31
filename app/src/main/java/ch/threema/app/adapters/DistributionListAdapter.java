package ch.threema.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import ch.threema.app.R;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListAdapter extends FilterableListAdapter {
    private final Context context;
    private List<DistributionListModel> values;
    private List<DistributionListModel> ovalues;
    private DistributionListFilter groupListFilter;
    private final DistributionListService distributionListService;
    private final PreferenceService preferenceService;
    private final FilterResultsListener filterResultsListener;

    public DistributionListAdapter(
        Context context,
        List<DistributionListModel> values,
        List<Integer> checkedItems,
        DistributionListService distributionListService,
        PreferenceService preferenceService,
        FilterResultsListener filterResultsListener
    ) {
        super(context, R.layout.item_distribution_list, (List<Object>) (Object) values);

        this.context = context;
        this.values = values;
        this.ovalues = values;
        this.distributionListService = distributionListService;
        this.preferenceService = preferenceService;
        this.filterResultsListener = filterResultsListener;

        if (checkedItems != null && !checkedItems.isEmpty()) {
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
            itemView.setOnCheckedChangeListener((checkableView, isChecked) -> {
                if (isChecked) {
                    checkedItems.add(((DistributionListHolder) checkableView.getTag()).originalPosition);
                } else {
                    checkedItems.remove(((DistributionListHolder) checkableView.getTag()).originalPosition);
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

        holder.nameView.setText(
            highlightMatches(
                NameUtil.getDistributionListDisplayName(
                    distributionListModel,
                    this.distributionListService,
                    preferenceService.getContactNameFormat()
                ),
                filterString
            )
        );
        holder.subjectView.setText(this.distributionListService.getMembersString(distributionListModel));

        // load avatars asynchronously
        AvatarListItemUtil.loadAvatar(
            distributionListModel.getId(),
            this.distributionListService,
            holder,
            Glide.with(context)
        );

        ((ListView) parent).setItemChecked(position, checkedItems.contains(holder.originalPosition));

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

                Collection<DistributionListModel> distributionListModelList = ovalues.stream()
                    .filter( distributionListModel ->
                            NameUtil.getDistributionListDisplayName(
                                distributionListModel,
                                distributionListService,
                                preferenceService.getContactNameFormat()
                            )
                            .toUpperCase().contains(filterString.toUpperCase())
                    )
                    .collect(Collectors.toList());

                results.values = distributionListModelList;
                results.count = distributionListModelList.size();
            }
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            values = (List<DistributionListModel>) results.values;
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

        for (int position : checkedItems) {
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
