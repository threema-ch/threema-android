package ch.threema.app.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.activities.DistributionListAddActivity;
import ch.threema.app.adapters.DistributionListAdapter;
import ch.threema.app.services.DistributionListService;
import ch.threema.storage.models.DistributionListModel;
import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

public class DistributionListFragment extends RecipientListFragment {

    private final Lazy<DistributionListService> distributionListServiceLazy = inject(DistributionListService.class);

    @Override
    protected boolean isMultiSelectAllowed() {
        return false;
    }

    @Override
    protected String getBundleName() {
        return "DistListState";
    }

    @Override
    protected int getEmptyText() {
        return R.string.no_matching_distribution_lists;
    }

    @Override
    protected int getAddIcon() {
        return R.drawable.ic_bullhorn_outline;
    }

    @Override
    protected int getAddText() {
        return R.string.title_add_distribution_list;
    }

    @Override
    protected Intent getAddIntent() {
        return DistributionListAddActivity.createIntent(requireContext());
    }

    @SuppressLint("StaticFieldLeak")
    protected void createListAdapter(ArrayList<Integer> checkedItemPositions) {
        new AsyncTask<Void, Void, List<DistributionListModel>>() {
            @Override
            protected List<DistributionListModel> doInBackground(Void... voids) {
                return distributionListServiceLazy.getValue().getAll(new DistributionListService.DistributionListFilter() {
                    @Override
                    public boolean sortingByDate() {
                        return true;
                    }

                    @Override
                    public boolean sortingAscending() {
                        return false;
                    }

                    @Override
                    public boolean showHidden() {
                        return false;
                    }
                });
            }

            @Override
            protected void onPostExecute(List<DistributionListModel> distributionListModels) {
                adapter = new DistributionListAdapter(
                    activity,
                    distributionListModels,
                    checkedItemPositions,
                    distributionListServiceLazy.getValue(),
                    DistributionListFragment.this
                );
                setListAdapter(adapter);
                if (listInstanceState != null) {
                    if (isAdded() && getView() != null && getActivity() != null) {
                        getListView().onRestoreInstanceState(listInstanceState);
                    }
                    listInstanceState = null;
                    restoreCheckedItems(checkedItemPositions);

                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
