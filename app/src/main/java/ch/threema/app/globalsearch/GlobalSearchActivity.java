/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.globalsearch;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.ThreemaSearchView;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING;

public class GlobalSearchActivity extends ThreemaToolbarActivity implements ThreemaSearchView.OnQueryTextListener {
	private static final Logger logger = LoggerFactory.getLogger(GlobalSearchActivity.class);
	private static final int QUERY_MIN_LENGTH = 2;
	private static final long QUERY_TIMEOUT_MS = 500;

	private GlobalSearchAdapter chatsAdapter, groupChatsAdapter;
	private RecyclerView chatsRecyclerView, groupChatsRecyclerView;
	private GlobalSearchViewModel chatsViewModel, groupChatsViewModel;
	private TextView emptyTextView;
	private ProgressBar progressBar;
	private DeadlineListService hiddenChatsListService;
	private ContactService contactService;
	private GroupService groupService;

	private String queryText;
	private Handler queryHandler = new Handler();
	private Runnable queryTask = new Runnable() {
		@Override
		public void run() {
			chatsViewModel.onQueryChanged(queryText);
			chatsAdapter.onQueryChanged(queryText);
			groupChatsViewModel.onQueryChanged(queryText);
			groupChatsAdapter.onQueryChanged(queryText);
		}
	};

	@Override
	public boolean onQueryTextSubmit(String query) {
		// Do something
		return true;
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	public boolean onQueryTextChange(String newText) {
		queryText = newText;

		if (chatsViewModel != null && chatsAdapter != null) {

			queryHandler.removeCallbacksAndMessages(null);
			if (queryText != null && queryText.length() >= QUERY_MIN_LENGTH) {
				emptyTextView.setVisibility(View.GONE);
				queryHandler.postDelayed(queryTask, QUERY_TIMEOUT_MS);
			} else {
				emptyTextView.setVisibility(View.VISIBLE);
				chatsViewModel.onQueryChanged(null);
				chatsAdapter.onQueryChanged(null);
				groupChatsViewModel.onQueryChanged(null);
				groupChatsAdapter.onQueryChanged(null);
			}
		}

		return true;
	}

	public int getLayoutResource() {
		return R.layout.activity_global_search;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			contactService = serviceManager.getContactService();
			groupService = serviceManager.getGroupService();
			hiddenChatsListService = serviceManager.getHiddenChatsListService();
		} catch (Exception e) {
			logger.error("Exception", e);
			finish();
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		};

		ConstraintLayout bottomSheetLayout = findViewById(R.id.bottom_sheet);
		final BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
		bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
			@Override
			public void onStateChanged(@NonNull View bottomSheet, int newState) {
				switch (newState) {
					case STATE_HIDDEN:
						finish();
						break;
					case STATE_EXPANDED:
						findViewById(R.id.drag_handle).setVisibility(View.INVISIBLE);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
							getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(GlobalSearchActivity.this, R.attr.attach_status_bar_color_expanded));
						}
						break;
					case STATE_SETTLING:
						findViewById(R.id.drag_handle).setVisibility(View.VISIBLE);
						break;
					case STATE_DRAGGING:
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
							getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(GlobalSearchActivity.this, R.attr.attach_status_bar_color_collapsed));
						}
					default:
						break;
				}
			}

			@Override
			public void onSlide(@NonNull View bottomSheet, float slideOffset) {
				// we don't care about sliding events
			}
		});

		findViewById(R.id.parent_layout).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bottomSheetBehavior.setState(STATE_HIDDEN);
			}
		});

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(GlobalSearchActivity.this, R.attr.attach_status_bar_color_collapsed));
		}

		ThreemaSearchView searchView = findViewById(R.id.search);
		searchView.setOnQueryTextListener(this);

		emptyTextView = findViewById(R.id.empty_text);
		progressBar = findViewById(R.id.progress);

		chatsAdapter = new GlobalSearchChatsAdapter(this, getString(R.string.chats));
		chatsAdapter.setOnClickItemListener((messageModel, view) -> {
			showMessage(messageModel, view);
		});

		groupChatsAdapter = new GlobalSearchGroupChatsAdapter(this, getString(R.string.title_tab_groups));
		groupChatsAdapter.setOnClickItemListener((messageModel, view) -> {
			showMessage(messageModel, view);
		});

		chatsRecyclerView = this.findViewById(R.id.recycler_chats);
		chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
		chatsRecyclerView.setItemAnimator(new DefaultItemAnimator());
		chatsRecyclerView.setAdapter(chatsAdapter);

		groupChatsRecyclerView = this.findViewById(R.id.recycler_groups);
		groupChatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
		groupChatsRecyclerView.setItemAnimator(new DefaultItemAnimator());
		groupChatsRecyclerView.setAdapter(groupChatsAdapter);

		chatsViewModel = new ViewModelProvider(this).get(GlobalSearchChatsViewModel.class);
		chatsViewModel.getMessageModels().observe(this, messageModels -> {
			messageModels = Functional.filter(messageModels, (IPredicateNonNull<AbstractMessageModel>) messageModel -> {
				if (messageModel.getIdentity() != null) {
					return !hiddenChatsListService.has(contactService.getUniqueIdString(messageModel.getIdentity()));
				}
				return true;
			});
			chatsAdapter.setMessageModels(messageModels);
		});

		chatsViewModel.getIsLoading().observe(this, isLoading -> {
			if (isLoading != null) {
				if (isLoading) {
					showProgressBar(true);
				}
			}
		});

		groupChatsViewModel = new ViewModelProvider(this).get(GlobalSearchGroupChatsViewModel.class);
		groupChatsViewModel.getMessageModels().observe(this, messageModels -> {
			messageModels = Functional.filter(messageModels, (IPredicateNonNull<AbstractMessageModel>) messageModel -> {
				if (((GroupMessageModel) messageModel).getGroupId() > 0) {
					return !hiddenChatsListService.has(groupService.getUniqueIdString(((GroupMessageModel) messageModel).getGroupId()));
				}
				return true;
			});
			groupChatsAdapter.setMessageModels(messageModels);
		});

		groupChatsViewModel.getIsLoading().observe(this, isLoading -> {
			if (isLoading != null) {
				if (!isLoading) {
					showProgressBar(false);
				}
			}
		});

		return true;
	}

	private void showMessage(AbstractMessageModel messageModel, View view) {
		if (messageModel == null) {
			return;
		}

		Intent intent = new Intent(this, ComposeMessageActivity.class);

		if (messageModel instanceof GroupMessageModel) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, ((GroupMessageModel) messageModel).getGroupId());
		} else if (messageModel instanceof DistributionListMessageModel) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, ((DistributionListMessageModel) messageModel).getDistributionListId());
		} else {
			intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, messageModel.getIdentity());
		}
		intent.putExtra(ComposeMessageFragment.EXTRA_API_MESSAGE_ID, messageModel.getApiMessageId());
		intent.putExtra(ComposeMessageFragment.EXTRA_SEARCH_QUERY, queryText);

		AnimationUtil.startActivityForResult(this, view, intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);

		finish();
	}

	@UiThread
	synchronized private void showProgressBar(boolean show) {
		if (show) {
			logger.debug("show progress");
			progressBar.setVisibility(View.VISIBLE);
		} else {
			logger.debug("hide progress");
			progressBar.setVisibility(View.GONE);
		}
	}
}
