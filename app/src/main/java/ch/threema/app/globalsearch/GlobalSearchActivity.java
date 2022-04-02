/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;

import org.slf4j.Logger;

import androidx.annotation.IdRes;
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
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING;

public class GlobalSearchActivity extends ThreemaToolbarActivity implements ThreemaSearchView.OnQueryTextListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GlobalSearchActivity");
	private static final int QUERY_MIN_LENGTH = 2;
	private static final long QUERY_TIMEOUT_MS = 500;

	public static final int FILTER_CHATS = 0x1;
	public static final int FILTER_GROUPS = 0x2;
	public static final int FILTER_INCLUDE_ARCHIVED = 0x4;

	private GlobalSearchAdapter chatsAdapter;
	private GlobalSearchViewModel chatsViewModel;
	private TextView emptyTextView;
	private ProgressBar progressBar;
	private DeadlineListService hiddenChatsListService;
	private ContactService contactService;
	private GroupService groupService;

	private int filterFlags = FILTER_CHATS | FILTER_GROUPS | FILTER_INCLUDE_ARCHIVED;
	private String queryText;
	private final Handler queryHandler = new Handler();
	private final Runnable queryTask = new Runnable() {
		@Override
		public void run() {
			chatsViewModel.onQueryChanged(queryText, filterFlags);
			chatsAdapter.onQueryChanged(queryText);
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
				queryHandler.postDelayed(queryTask, QUERY_TIMEOUT_MS);
			} else {
				chatsViewModel.onQueryChanged(null, filterFlags);
				chatsAdapter.onQueryChanged(null);
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
						getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(GlobalSearchActivity.this, R.attr.attach_status_bar_color_expanded));
						break;
					case STATE_SETTLING:
						findViewById(R.id.drag_handle).setVisibility(View.VISIBLE);
						break;
					case STATE_DRAGGING:
						getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(GlobalSearchActivity.this, R.attr.attach_status_bar_color_collapsed));
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

		getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(GlobalSearchActivity.this, R.attr.attach_status_bar_color_collapsed));

		ThreemaSearchView searchView = findViewById(R.id.search);
		searchView.setOnQueryTextListener(this);

		emptyTextView = findViewById(R.id.empty_text);
		progressBar = findViewById(R.id.progress);

		chatsAdapter = new GlobalSearchAdapter(this);
		chatsAdapter.setOnClickItemListener(this::showMessage);

		setupChip(R.id.chats, FILTER_CHATS);
		setupChip(R.id.groups, FILTER_GROUPS);
		setupChip(R.id.archived, FILTER_INCLUDE_ARCHIVED);

		RecyclerView chatsRecyclerView = this.findViewById(R.id.recycler_chats);
		chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
		chatsRecyclerView.setItemAnimator(new DefaultItemAnimator());
		chatsRecyclerView.setAdapter(chatsAdapter);

		chatsViewModel = new ViewModelProvider(this).get(GlobalSearchViewModel.class);
		chatsViewModel.getMessageModels().observe(this, messageModels -> {
			if (messageModels.size() > 0) {
				messageModels = Functional.filter(messageModels, (IPredicateNonNull<AbstractMessageModel>) messageModel -> {
					if (messageModel instanceof GroupMessageModel) {
						if (((GroupMessageModel) messageModel).getGroupId() > 0) {
							return !hiddenChatsListService.has(groupService.getUniqueIdString(((GroupMessageModel) messageModel).getGroupId()));
						}
					} else {
						if (messageModel.getIdentity() != null) {
							return !hiddenChatsListService.has(contactService.getUniqueIdString(messageModel.getIdentity()));
						}
					}
					return true;
				});
			}

			if (messageModels.size() == 0) {
				if (queryText != null && queryText.length() >= QUERY_MIN_LENGTH) {
					emptyTextView.setText(R.string.search_no_matches);
				} else {
					emptyTextView.setText(R.string.global_search_empty_view_text);
				}
				emptyTextView.setVisibility(View.VISIBLE);
			} else {
				emptyTextView.setVisibility(View.GONE);
			}
			chatsAdapter.setMessageModels(messageModels);
		});

		chatsViewModel.getIsLoading().observe(this, isLoading -> {
			if (isLoading != null) {
				showProgressBar(isLoading);
			}
		});
		return true;
	}

	private void setupChip(@IdRes int id, int flag) {
		// https://github.com/material-components/material-components-android/issues/1419
		Chip chip = findViewById(id);
		chip.setChecked(true);
		chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (isChecked) {
				filterFlags |= flag;
			} else {
				filterFlags  &= ~flag;
			}
			chatsViewModel.onQueryChanged(queryText, filterFlags);
		});
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
