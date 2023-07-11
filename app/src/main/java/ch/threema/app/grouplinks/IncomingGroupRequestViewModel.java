/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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
import ch.threema.storage.factories.IncomingGroupJoinRequestModelFactory;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;

public class IncomingGroupRequestViewModel extends ViewModel {

	private MutableLiveData<List<IncomingGroupJoinRequestModel>> groupJoinRequestModels;
	private IncomingGroupJoinRequestModelFactory repository;
	private final SparseBooleanArray checkedItems = new SparseBooleanArray();
	GroupId groupId;

	public IncomingGroupRequestViewModel(GroupId groupId) {
		super();
		this.groupId = groupId;
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			this.repository = serviceManager.getDatabaseServiceNew().getIncomingGroupJoinRequestModelFactory();
			if (this.repository != null) {
				groupJoinRequestModels = new MutableLiveData<List<IncomingGroupJoinRequestModel>>() {
					@Override
					public List<IncomingGroupJoinRequestModel> getValue() {
						return repository.getAllRequestsForGroup(groupId);
					}
				};
			}
		}
	}

	MutableLiveData<List<IncomingGroupJoinRequestModel>> getRequests() {
		return groupJoinRequestModels;
	}

	public FutureTask<Result<List<IncomingGroupJoinRequestModel>, Exception>> deleteIncomingGroupJoinRequests(
		List<IncomingGroupJoinRequestModel> incomingGroupJoinRequests
	) {

		FutureTask<Result<List<IncomingGroupJoinRequestModel>, Exception>> deletionTask = new FutureTask<>(() -> {
			for (IncomingGroupJoinRequestModel incomingGroupRequestViewModel : incomingGroupJoinRequests) {
				try {
					repository.delete(incomingGroupRequestViewModel);
					onDataChanged();
				}
				catch (SQLException e) {
					return Result.failure(e);
				}
			}
			return Result.success(incomingGroupJoinRequests);
		});

		ExecutorService executor = Executors.newFixedThreadPool(2);
		executor.execute(deletionTask);

		return deletionTask;
	}

	public void onDataChanged() {
		getRequests().postValue(repository.getAllRequestsForGroup(this.groupId));
	}

	void toggleChecked(int pos) {
		if (checkedItems.get(pos, false)) {
			checkedItems.delete(pos);
		}
		else {
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
		if (checkedItems.size() == Objects.requireNonNull(groupJoinRequestModels.getValue()).size()) {
			clearCheckedItems();
			return false;
		} else {
			for (int i = 0; i < Objects.requireNonNull(groupJoinRequestModels.getValue()).size(); i++) {
				checkedItems.put(i, true);
			}
			onDataChanged();
			return true;
		}
	}

	public List<IncomingGroupJoinRequestModel> getCheckedItems() {
		List<IncomingGroupJoinRequestModel> items = new ArrayList<>(checkedItems.size());
		for (int i = 0; i < checkedItems.size(); i++) {
			items.add(Objects.requireNonNull(groupJoinRequestModels.getValue()).get(checkedItems.keyAt(i)));
		}
		return items;
	}
}
