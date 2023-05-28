/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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

package ch.threema.app.ui;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.widget.Toast;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.paging.PageKeyedDataSource;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.work.WorkDirectory;
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory;
import ch.threema.domain.protocol.api.work.WorkDirectoryContact;
import ch.threema.domain.protocol.api.work.WorkDirectoryFilter;

public class DirectoryDataSource extends PageKeyedDataSource<WorkDirectory, WorkDirectoryContact> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DirectoryDataSource");
	public static final int MIN_SEARCH_STRING_LENGTH = 3;
	public static final String WILDCARD_SEARCH_ALL = "*";

	private PreferenceService preferenceService;
	private APIConnector apiConnector;
	private IdentityStore identityStore;
	private final boolean sortByFirstName;
	private static String queryText;
	private static List<WorkDirectoryCategory> queryCategories = new ArrayList<>();

	public DirectoryDataSource() {
		super();

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		try {
			this.preferenceService = serviceManager.getPreferenceService();
			this.apiConnector = serviceManager.getAPIConnector();
			this.identityStore = serviceManager.getIdentityStore();
		} catch (Exception e) {
			logger.error("Exception", e);
		}

		this.sortByFirstName = preferenceService.isContactListSortingFirstName();
	}

	public void setQueryText(String query) {
		queryText = query;
	}

	public void setQueryCategories(List<WorkDirectoryCategory> categories) {
		queryCategories = categories;
	}

	@Override
	public void loadInitial(@NonNull LoadInitialParams<WorkDirectory> params, @NonNull LoadInitialCallback<WorkDirectory, WorkDirectoryContact> callback) {
		logger.debug("loadInitial");

		if (queryCategories.size() > 0) {
			if (TestUtil.empty(queryText)) {
				queryText = WILDCARD_SEARCH_ALL;
			}
		} else {
			if (queryText == null || queryText.length() < MIN_SEARCH_STRING_LENGTH) {
				// return empty result
				callback.onResult(new ArrayList<WorkDirectoryContact>(), null, null);
				return;
			}
		}

		logger.debug("Fetching query {} #categories {}", queryText, queryCategories.size());

		fetchInitialData(callback);
	}

	@Override
	public void loadBefore(@NonNull LoadParams<WorkDirectory> params, @NonNull LoadCallback<WorkDirectory, WorkDirectoryContact> callback) {
		fetchData(params.key.previousFilter, callback);
	}

	@Override
	public void loadAfter(@NonNull LoadParams<WorkDirectory> params, @NonNull LoadCallback<WorkDirectory, WorkDirectoryContact> callback) {
		logger.debug("*** loadAfter: " + params.key.nextFilter.getPage());
		fetchData(params.key.nextFilter, callback);
	}

	@SuppressLint("StaticFieldLeak")
	private void fetchData(final WorkDirectoryFilter workDirectoryFilter, final LoadCallback<WorkDirectory, WorkDirectoryContact> callback) {
		if (workDirectoryFilter == null) {
			// no more data
			return;
		}

		new AsyncTask<Void, Void, WorkDirectory>() {
			@Override
			protected WorkDirectory doInBackground(Void... voids) {
				WorkDirectory workDirectory;

				try {
					workDirectory = apiConnector.fetchWorkDirectory(
						preferenceService.getLicenseUsername(),
						preferenceService.getLicensePassword(),
						identityStore,
						workDirectoryFilter
					);
				} catch (Exception e) {
					logger.error("Exception", e);
					RuntimeUtil.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(ThreemaApplication.getAppContext(), "Unable to fetch directory: " + e.getMessage(), Toast.LENGTH_LONG).show();
						}
					});
					return null;
				}

				return workDirectory;
			}

			@Override
			protected void onPostExecute(WorkDirectory workDirectory) {
				if (workDirectory != null) {
					callback.onResult(workDirectory.workContacts, workDirectory);
				}
			}
		}.execute();
	}

	@SuppressLint("StaticFieldLeak")
	private void fetchInitialData(final LoadInitialCallback<WorkDirectory, WorkDirectoryContact> callback) {
		new AsyncTask<Void, Void, WorkDirectory>() {
			@Override
			protected WorkDirectory doInBackground(Void... voids) {
				WorkDirectory workDirectory;

				WorkDirectoryFilter workDirectoryFilter = new WorkDirectoryFilter();
				for (WorkDirectoryCategory queryCategory : queryCategories) {
					workDirectoryFilter.addCategory(queryCategory);
				}
		        workDirectoryFilter.page(0);
				workDirectoryFilter.sortBy(sortByFirstName ? WorkDirectoryFilter.SORT_BY_FIRST_NAME : WorkDirectoryFilter.SORT_BY_LAST_NAME, true);
				workDirectoryFilter.query(queryText);

				try {
					workDirectory = apiConnector.fetchWorkDirectory(
						preferenceService.getLicenseUsername(),
						preferenceService.getLicensePassword(),
						identityStore,
						workDirectoryFilter
					);
				} catch (Exception e) {
					logger.error("Exception", e);
					RuntimeUtil.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(ThreemaApplication.getAppContext(), "Unable to fetch directory: " + e.getMessage(), Toast.LENGTH_LONG).show();
						}
					});
					return null;
				}

				return workDirectory;
			}

			@Override
			protected void onPostExecute(WorkDirectory workDirectory) {
				if (workDirectory != null) {
					logger.debug("Fetch results {}", workDirectory.workContacts);
					callback.onResult(workDirectory.workContacts, workDirectory, workDirectory);
				}
			}
		}.execute();
	}
}
