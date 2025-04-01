/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.app.grouplinks;

import android.database.SQLException;
import android.util.SparseBooleanArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.base.Result;
import ch.threema.domain.models.GroupId;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.models.group.GroupInviteModel;

public class GroupLinkViewModel extends ViewModel {

    private final GroupId groupApiId;
    private MutableLiveData<List<GroupInviteModel>> groupInviteModels;
    private GroupInviteModelFactory repository;
    private final SparseBooleanArray checkedItems = new SparseBooleanArray();

    public GroupLinkViewModel(GroupId groupId) {
        super();
        this.groupApiId = groupId;
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            this.repository = serviceManager.getDatabaseServiceNew().getGroupInviteModelFactory();
            if (this.repository != null) {
                groupInviteModels = new MutableLiveData<List<GroupInviteModel>>() {
                    @NonNull
                    @Override
                    public List<GroupInviteModel> getValue() {
                        return repository.getByGroupApiId(groupId);
                    }
                };
            }
        }
    }

    MutableLiveData<List<GroupInviteModel>> getGroupInviteModels() {
        return groupInviteModels;
    }

    public FutureTask<Result<List<GroupInviteModel>, Exception>> removeGroupInviteModels(final List<GroupInviteModel> groupInviteModels) {
        FutureTask<Result<List<GroupInviteModel>, Exception>> deletionTask = new FutureTask<>(() -> {
            for (GroupInviteModel groupInviteModel : groupInviteModels) {
                try {
                    repository.delete(groupInviteModel);
                } catch (SQLException e) {
                    return Result.failure(e);
                }
                onDataChanged();
            }
            return Result.success(groupInviteModels);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(deletionTask);
        return deletionTask;
    }

    public boolean updateGroupInviteModel(GroupInviteModel groupInviteModel) {
        return repository.update(groupInviteModel);
    }

    public void onDataChanged() {
        groupInviteModels.postValue(repository.getByGroupApiId(groupApiId));
    }

    void toggleChecked(int pos) {
        if (checkedItems.get(pos, false)) {
            checkedItems.delete(pos);
        } else {
            checkedItems.put(pos, true);
        }
        onDataChanged();
    }

    int getCheckedItemsCount() {
        return checkedItems.size();
    }

    void clearCheckedItems() {
        checkedItems.clear();
        onDataChanged();
    }

    boolean isChecked(int pos) {
        return checkedItems.get(pos);
    }

    boolean selectAll() {
        if (checkedItems.size() == Objects.requireNonNull(groupInviteModels.getValue()).size()) {
            clearCheckedItems();
            return false;
        } else {
            for (int i = 0; i < Objects.requireNonNull(groupInviteModels.getValue()).size(); i++) {
                checkedItems.put(i, true);
            }
            onDataChanged();
            return true;
        }
    }

    public List<GroupInviteModel> getCheckedItems() {
        List<GroupInviteModel> items = new ArrayList<>(checkedItems.size());
        for (int i = 0; i < checkedItems.size(); i++) {
            items.add(Objects.requireNonNull(groupInviteModels.getValue()).get(checkedItems.keyAt(i)));
        }
        return items;
    }
}
