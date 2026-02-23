package ch.threema.app.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.activities.GroupAddActivity;
import ch.threema.app.adapters.GroupListAdapter;
import ch.threema.app.services.GroupService;
import ch.threema.storage.models.GroupModel;
import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

public class GroupListFragment extends RecipientListFragment {

    private final Lazy<GroupService> groupServiceLazy = inject(GroupService.class);

    @Override
    protected boolean isMultiSelectAllowed() {
        return multiSelect;
    }

    @Override
    protected String getBundleName() {
        return "GroupListState";
    }

    @Override
    protected int getEmptyText() {
        return R.string.no_matching_groups;
    }

    @Override
    protected int getAddIcon() {
        return R.drawable.ic_group_outline;
    }

    @Override
    protected int getAddText() {
        return R.string.title_addgroup;
    }

    @Override
    protected Intent getAddIntent() {
        return new Intent(getActivity(), GroupAddActivity.class);
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void createListAdapter(ArrayList<Integer> checkedItemPositions) {
        new AsyncTask<Void, Void, List<GroupModel>>() {
            @Override
            protected List<GroupModel> doInBackground(Void... voids) {
                return groupServiceLazy.getValue().getAll(new GroupService.GroupFilter() {
                    @Override
                    public boolean sortByDate() {
                        return false;
                    }

                    @Override
                    public boolean sortByName() {
                        return true;
                    }

                    @Override
                    public boolean sortAscending() {
                        return true;
                    }

                    @Override
                    public boolean includeLeftGroups() {
                        return false;
                    }

                });
            }

            @Override
            protected void onPostExecute(List<GroupModel> groupModels) {
                adapter = new GroupListAdapter(
                    activity,
                    groupModels,
                    checkedItemPositions,
                    groupServiceLazy.getValue(),
                    GroupListFragment.this
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
