/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.activities.ballot;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.ActionBar;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;

import org.slf4j.Logger;

import java.util.List;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.adapters.ballot.BallotOverviewListAdapter;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ballot.BallotModel;

public class BallotChooserActivity extends ThreemaToolbarActivity implements ListView.OnItemClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("BallotChooserActivity");

	private BallotService ballotService;
	private ContactService contactService;
	private GroupService groupService;
	private String myIdentity;

	private BallotOverviewListAdapter listAdapter = null;
	private ListView listView;

	private final BallotListener ballotListener = new BallotListener() {
		@Override
		public void onClosed(BallotModel ballotModel) {}

		@Override
		public void onModified(BallotModel ballotModel) {
			RuntimeUtil.runOnUiThread(() -> updateList());
		}

		@Override
		public void onCreated(BallotModel ballotModel) {
			RuntimeUtil.runOnUiThread(() -> updateList());
		}

		@Override
		public void onRemoved(BallotModel ballotModel) {
			RuntimeUtil.runOnUiThread(() -> updateList());
		}

		@Override
		public boolean handle(BallotModel ballotModel) {
			return true;
		}
	};

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(!this.requireInstancesOrExit()) {
			return;
		}

		listView = this.findViewById(android.R.id.list);
		listView.setOnItemClickListener(this);
		listView.setDividerHeight(0);

		// add text view if list is empty
		EmptyView emptyView = new EmptyView(this);
		emptyView.setup(R.string.ballot_no_ballots_yet);
		((ViewGroup) listView.getParent()).addView(emptyView);
		listView.setEmptyView(emptyView);
		final AppBarLayout appBarLayout = findViewById(R.id.appbar);
		appBarLayout.setLiftable(true);
		listView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				boolean isAtTop = firstVisibleItem == 0 && (view.getChildCount() == 0 || view.getChildAt(0).getTop() == 0);
				appBarLayout.setLifted(!isAtTop);
			}
		});

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.ballot_copy);
		} else {
			setTitle(R.string.ballot_copy);
		}

		this.setupList();
		this.updateList();

	}

	@Override
	protected void onResume() {
		super.onResume();
		ListenerManager.ballotListeners.add(this.ballotListener);
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_list_toolbar;
	}

	@Override
	protected void onDestroy() {
		ListenerManager.ballotListeners.remove(this.ballotListener);
		super.onDestroy();
	}

	private void setupList() {
		final ListView listView = this.listView;

		if (listView != null) {
			listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
		}
	}

	private void updateList() {
		if(!this.requiredInstances()) {
			return;
		}

		try {
			List<BallotModel> ballots = this.ballotService.getBallots(new BallotService.BallotFilter() {
				@Override
				public MessageReceiver<?> getReceiver() {
					return null;
				}

				@Override
				public BallotModel.State[] getStates() {
					return null;
				}

				@Override
				public boolean filter(BallotModel ballotModel) {
					return true;
				}
			});

			if (ballots != null) {
				this.listAdapter = new BallotOverviewListAdapter(
					this,
					ballots,
					this.ballotService,
					this.contactService,
					Glide.with(this)
				);

				listView.setAdapter(this.listAdapter);
			}
		} catch (NotAllowedException e) {
			logger.error("Exception", e);
			finish();
			return;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (this.listAdapter == null) {
			return;
		}

		BallotModel b = listAdapter.getItem(position);

		if(b != null) {
			Intent resultIntent = this.getIntent();
			//append ballot
			IntentDataUtil.append(b, this.getIntent());

			setResult(RESULT_OK, resultIntent);
			finish();
		}
	}

	protected boolean checkInstances() {
		return !TestUtil.empty(this.myIdentity) && TestUtil.required(
				this.ballotService,
				this.contactService,
				this.groupService);
	}

	protected void instantiate() {
		if(serviceManager != null) {
			try {
				this.ballotService = serviceManager.getBallotService();
				this.contactService = serviceManager.getContactService();
				this.groupService = serviceManager.getGroupService();
				this.myIdentity = serviceManager.getUserService().getIdentity();
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}

	private boolean requireInstancesOrExit() {
		if(!this.requiredInstances()) {
			logger.error("Required instances failed");
			this.finish();
			return false;
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				setResult(RESULT_CANCELED);
				finish();
				break;
		}

		return true;
	}

	@Override
	protected boolean enableOnBackPressedCallback() {
		return true;
	}

	@Override
	protected void handleOnBackPressed() {
		setResult(RESULT_CANCELED);
		finish();
	}
}
