/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING;
import static ch.threema.app.services.MessageServiceImpl.FILTER_CHATS;
import static ch.threema.app.services.MessageServiceImpl.FILTER_GROUPS;
import static ch.threema.app.services.MessageServiceImpl.FILTER_INCLUDE_ARCHIVED;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.ui.ThreemaSearchView;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.GroupMessageModel;

public class GlobalSearchActivity extends ThreemaToolbarActivity implements ThreemaSearchView.OnQueryTextListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GlobalSearchActivity");
	private static final int QUERY_MIN_LENGTH = 2;
	private static final long GLOBAL_SEARCH_QUERY_TIMEOUT_MS = 500;

	private GlobalSearchAdapter chatsAdapter;
	private GlobalSearchViewModel chatsViewModel;
	private TextView emptyTextView;
	private ThreemaSearchView searchView;
	private CircularProgressIndicator progressBar;
	private DeadlineListService hiddenChatsListService;
	private ContactService contactService;
	private GroupService groupService;

	private @MessageService.MessageFilterFlags int filterFlags = FILTER_CHATS | FILTER_GROUPS | FILTER_INCLUDE_ARCHIVED;
	private String queryText;
	private final Handler queryHandler = new Handler();
	private final Runnable queryTask = new Runnable() {
		@Override
		public void run() {
			chatsViewModel.onQueryChanged(queryText, filterFlags, false, false);
			chatsAdapter.onQueryChanged(queryText);
		}
	};

	@Override
	public boolean onQueryTextSubmit(String query) {
		// Do something
		return true;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		queryText = newText;

		if (chatsViewModel != null && chatsAdapter != null) {

			queryHandler.removeCallbacksAndMessages(null);
			if (queryText != null && queryText.length() >= QUERY_MIN_LENGTH) {
				queryHandler.postDelayed(queryTask, GLOBAL_SEARCH_QUERY_TIMEOUT_MS);
			} else {
				chatsViewModel.onQueryChanged(null, filterFlags, false, false);
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
		final float cornerSize = getResources().getDimensionPixelSize(R.dimen.bottomsheet_corner_size);

		final BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
		bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
			@Override
			public void onStateChanged(@NonNull View bottomSheet, int newState) {
				Drawable background = bottomSheetLayout.getBackground();

				switch (newState) {
					case STATE_HIDDEN:
						finish();
						break;
					case STATE_EXPANDED:
						findViewById(R.id.drag_handle).setVisibility(View.INVISIBLE);
						if (background instanceof MaterialShapeDrawable) {
							MaterialShapeDrawable materialShapeDrawable = (MaterialShapeDrawable) background;
							getWindow().setStatusBarColor(materialShapeDrawable.getResolvedTintColor());
							ShapeAppearanceModel shapeAppearanceModel = materialShapeDrawable.getShapeAppearanceModel().toBuilder()
								.setAllCornerSizes(0)
								.build();
							materialShapeDrawable.setShapeAppearanceModel(shapeAppearanceModel);
						} else {
							getWindow().setStatusBarColor(getResources().getColor(R.color.attach_status_bar_color_expanded));
						}
						break;
					case STATE_SETTLING:
						findViewById(R.id.drag_handle).setVisibility(View.VISIBLE);
						break;
					case STATE_DRAGGING:
						getWindow().setStatusBarColor(getResources().getColor(R.color.attach_status_bar_color_collapsed));
						if (background instanceof MaterialShapeDrawable) {
							MaterialShapeDrawable materialShapeDrawable = (MaterialShapeDrawable) background;
							materialShapeDrawable.setShapeAppearanceModel(
								materialShapeDrawable.getShapeAppearanceModel().toBuilder()
									.setTopLeftCornerSize(cornerSize)
									.setTopRightCornerSize(cornerSize)
									.build()
							);
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

		findViewById(R.id.parent_layout).setOnClickListener(v -> bottomSheetBehavior.setState(STATE_HIDDEN));

		getWindow().setStatusBarColor(getResources().getColor(R.color.attach_status_bar_color_collapsed));

		searchView = findViewById(R.id.search);
		searchView.setOnQueryTextListener(this);

		emptyTextView = findViewById(R.id.empty_text);
		progressBar = findViewById(R.id.progress);

		chatsAdapter = new GlobalSearchAdapter(
			this,
			Glide.with(this),
			R.layout.item_global_search,
			17
		);
		chatsAdapter.setOnClickItemListener((messageModel, view, position) -> showMessage(messageModel, view));

		setupChip(R.id.chats, FILTER_CHATS, true);
		setupChip(R.id.groups, FILTER_GROUPS, true);
		setupChip(R.id.archived, FILTER_INCLUDE_ARCHIVED, true);

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

	private void setupChip(@IdRes int id, int flag, boolean checked) {
		// https://github.com/material-components/material-components-android/issues/1419
		Chip chip = findViewById(id);
		chip.setChecked(checked);
		chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (isChecked) {
				filterFlags |= flag;
			} else {
				filterFlags  &= ~flag;
			}
			chatsViewModel.onQueryChanged(queryText, filterFlags, false, false);
		});
	}

	private void showMessage(AbstractMessageModel messageModel, View view) {
		if (messageModel == null) {
			return;
		}

		if (searchView != null) {
			EditTextUtil.hideSoftKeyboard(searchView);
		}

		startActivityForResult(IntentDataUtil.getJumpToMessageIntent(this, messageModel), ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);

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

	@Override
	public void finish() {
		try {
			super.finish();
			overridePendingTransition(0, 0);
		} catch (Exception ignored) {}
	}
}
