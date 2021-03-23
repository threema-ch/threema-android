/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import ch.threema.app.R;
import ch.threema.app.adapters.DirectoryAdapter;
import ch.threema.app.asynctasks.AddContactAsyncTask;
import ch.threema.app.dialogs.MultiChoiceSelectorDialog;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.DirectoryDataSourceFactory;
import ch.threema.app.ui.DirectoryHeaderItemDecoration;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.ThreemaSearchView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.client.work.WorkDirectoryCategory;
import ch.threema.client.work.WorkDirectoryContact;
import ch.threema.client.work.WorkOrganization;

public class DirectoryActivity extends ThreemaToolbarActivity implements ThreemaSearchView.OnQueryTextListener, MultiChoiceSelectorDialog.SelectorDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(DirectoryActivity.class);

	private static final int API_DIRECTORY_PAGE_SIZE = 3;
	private static final long QUERY_TIMEOUT = 1000; // ms
	private static final String DIALOG_TAG_CATEGORY_SELECTOR = "cs";
	public static final String EXTRA_ANIMATE_OUT = "anim";

	private ContactService contactService;
	private boolean sortByFirstName;

	private DirectoryAdapter directoryAdapter;
	private DirectoryDataSourceFactory directoryDataSourceFactory;
	private EmptyRecyclerView recyclerView;
	private ChipGroup chipGroup;

	private List<WorkDirectoryCategory> categoryList = new ArrayList<>();
	private List<WorkDirectoryCategory> checkedCategories = new ArrayList<>();
	private TextView categoriesHeaderTextView;

	private String queryText;

	@ColorInt int categorySpanColor;
	@ColorInt int categorySpanTextColor;

	private Handler queryHandler = new Handler();
	private Runnable queryTask = new Runnable() {
		@Override
		public void run() {
			directoryDataSourceFactory.postLiveData.getValue().setQueryText(queryText);
			directoryDataSourceFactory.postLiveData.getValue().invalidate();
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
		queryHandler.removeCallbacks(queryTask);
		queryHandler.postDelayed(queryTask, QUERY_TIMEOUT);

		return true;
	}

	public int getLayoutResource() {
		return R.layout.activity_directory;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		};

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			Toolbar toolbar = getToolbar();
			if (toolbar != null) {
				actionBar.setTitle(null);
				toolbar.setTitle(R.string.directory_title);
			}
		}

		try {
			this.contactService = serviceManager.getContactService();
		} catch (Exception e) {
			LogUtil.exception(e, this);
			return false;
		}

		if (preferenceService == null) {
			return false;
		}

		if (!ConfigUtils.isWorkDirectoryEnabled()) {
			Toast.makeText(this, getString(R.string.disabled_by_policy_short), Toast.LENGTH_LONG).show();
			return false;
		}

		categoryList = preferenceService.getWorkDirectoryCategories();

		if (categoryList.size() > 0) {
			if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
				ConfigUtils.themeImageView(this, findViewById(R.id.category_selector_button));
			}
		} else {
			findViewById(R.id.category_selector_button).setVisibility(View.GONE);
		}

		WorkOrganization workOrganization = preferenceService.getWorkOrganization();
		if (workOrganization != null) {
			logger.info("Organization: " + workOrganization.getName());
			getToolbar().setTitle(workOrganization.getName());
		}

		ThreemaSearchView searchView = findViewById(R.id.search);
		searchView.setOnQueryTextListener(this);

		sortByFirstName = preferenceService.isContactListSortingFirstName();

		chipGroup = findViewById(R.id.chip_group);

		categorySpanColor = ConfigUtils.getColorFromAttribute(this, R.attr.mention_background);
		categorySpanTextColor = ConfigUtils.getColorFromAttribute(this, R.attr.mention_text_color);

		recyclerView = this.findViewById(R.id.recycler);
		recyclerView.setHasFixedSize(true);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.setItemAnimator(new DefaultItemAnimator());

		EmptyView emptyView = new EmptyView(this, getResources().getDimensionPixelSize(R.dimen.directory_search_bar_height)
															+ ConfigUtils.getActionBarSize(this));
		emptyView.setup(R.string.directory_empty_view_text);
		((ViewGroup) recyclerView.getParent().getParent()).addView(emptyView);
		recyclerView.setEmptyView(emptyView);

		DirectoryHeaderItemDecoration headerItemDecoration = new DirectoryHeaderItemDecoration(getResources().getDimensionPixelSize(R.dimen.directory_header_height), true, getSectionCallback());
		recyclerView.addItemDecoration(headerItemDecoration);

		directoryAdapter = new DirectoryAdapter(this, preferenceService, contactService, categoryList);
		directoryAdapter.setOnClickItemListener(new DirectoryAdapter.OnClickItemListener() {
			@Override
			public void onClick(WorkDirectoryContact workDirectoryContact, int position) {
				launchContact(workDirectoryContact, position);
			}

			@Override
			public void onAdd(WorkDirectoryContact workDirectoryContact, final int position) {
				addContact(workDirectoryContact, new Runnable() {
					@Override
					public void run() {
						directoryAdapter.notifyItemChanged(position);
					}
				});
			}
		});

		// initial page size
		PagedList.Config config = new PagedList.Config.Builder().setPageSize(API_DIRECTORY_PAGE_SIZE).build();
		directoryDataSourceFactory = new DirectoryDataSourceFactory();

		LiveData<PagedList<WorkDirectoryContact>> contacts = new LivePagedListBuilder(directoryDataSourceFactory, config).build();
		contacts.observe(this, workDirectoryContacts -> directoryAdapter.submitList(workDirectoryContacts));

		recyclerView.setAdapter(directoryAdapter);

		findViewById(R.id.category_selector_button).setOnClickListener(this::selectCategories);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				this.finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void openContact(String identity) {
		Intent intent = new Intent(DirectoryActivity.this, ComposeMessageActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
		IntentDataUtil.append(identity, intent);
		startActivity(intent);
		overridePendingTransition(R.anim.slide_in_right_short, R.anim.slide_out_left_short);
	}

	private void launchContact(final WorkDirectoryContact workDirectoryContact, final int position) {
		if (contactService.getByIdentity(workDirectoryContact.threemaId) == null) {
			addContact(workDirectoryContact, new Runnable() {
				@Override
				public void run() {
					openContact(workDirectoryContact.threemaId);
					directoryAdapter.notifyItemChanged(position);
				}
			});
		} else {
			openContact(workDirectoryContact.threemaId);
		}
	}

	private void addContact(final WorkDirectoryContact workDirectoryContact, Runnable runAfter) {
		new AddContactAsyncTask(
			workDirectoryContact.firstName,
			workDirectoryContact.lastName,
			workDirectoryContact.threemaId,
			true,
			runAfter).execute();
	}

	private DirectoryHeaderItemDecoration.HeaderCallback getSectionCallback() {
		return new DirectoryHeaderItemDecoration.HeaderCallback() {
			@Override
			public boolean isHeader(int position) {
				if (position < 0) {
					return false;
				}

				if (position == 0) {
					return true;
				}

				PagedList<WorkDirectoryContact> list = directoryAdapter.getCurrentList();

				if (position > list.size()) {
					return false;
				}

				return !list.get(position).getInitial(sortByFirstName).equals(list.get(position - 1).getInitial(sortByFirstName));
			}

			@Override
			public CharSequence getHeaderText(int position) {
				PagedList<WorkDirectoryContact> list = directoryAdapter.getCurrentList();

				return position >= 0 ? list.get(position).getInitial(sortByFirstName) : "TODO";
			}
		};
	}

	public void selectCategories(View view) {
		String[] categoryNames = new String[categoryList.size()];
		boolean[] categoryChecked = new boolean[categoryList.size()];

		int i = 0;
		for (WorkDirectoryCategory category : categoryList) {
			categoryNames[i] = category.getName();
			categoryChecked[i] = checkedCategories.contains(category);
			i++;
		}

		MultiChoiceSelectorDialog.newInstance(getString(R.string.work_select_categories), categoryNames, categoryChecked).show(getSupportFragmentManager(), DIALOG_TAG_CATEGORY_SELECTOR);
	}

	@UiThread
	private void updateSelectedCategories() {
		int activeCategories = 0;

		chipGroup.removeAllViews();

		for (WorkDirectoryCategory checkedCategory: checkedCategories) {
			if (!TextUtils.isEmpty(checkedCategory.name)) {
				activeCategories++;

				Chip chip = new Chip(this);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					chip.setTextAppearance(R.style.TextAppearance_Chip_Ballot);
				} else {
					chip.setTextSize(14);
				}

				ColorStateList foregroundColor, backgroundColor;
				if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
					foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(this, R.attr.textColorPrimary));
					backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(this, R.attr.colorAccent));
				} else {
					foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(this, R.attr.colorAccent));
					backgroundColor = foregroundColor.withAlpha(0x1A);
				}

				chip.setTextColor(foregroundColor);
				chip.setChipBackgroundColor(backgroundColor);
				chip.setText(checkedCategory.name);
				chip.setCloseIconVisible(true);
				chip.setTag(checkedCategory.id);
				chip.setCloseIconTint(foregroundColor);
				chip.setOnCloseIconClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String categoryId = (String) v.getTag();

						if (!TextUtils.isEmpty(categoryId)) {
							for (WorkDirectoryCategory checkedCategory : checkedCategories) {
								if (categoryId.equals(checkedCategory.getId())) {
									checkedCategories.remove(checkedCategory);
									chipGroup.removeView(v);
									updateDirectory();
									break;
								}
							}
						}
					}
				});

				chipGroup.addView(chip);
			}
		}

		chipGroup.setVisibility(activeCategories == 0 ? View.GONE : View.VISIBLE);

		updateDirectory();
	}

	private void updateDirectory() {
		directoryDataSourceFactory.postLiveData.getValue().setQueryCategories(checkedCategories);
		directoryDataSourceFactory.postLiveData.getValue().invalidate();
	}

	@Override
	public void onYes(String tag, boolean[] checkedItems) {
		checkedCategories.clear();

		for(int i = 0; i < checkedItems.length; i++) {
			if (checkedItems[i]) {
				checkedCategories.add(categoryList.get(i));
			}
		}

/* TODO only update if selected items have changed
		if (!Arrays.equals(this.checkedCategories, checkedItems)) {
			this.checkedCategories = checkedItems; */
			updateSelectedCategories();
//		}
	}

	@Override
	public void onCancel(String tag) { }

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if (recyclerView != null) {
			recyclerView.removeItemDecorationAt(0);
			DirectoryHeaderItemDecoration headerItemDecoration = new DirectoryHeaderItemDecoration(getResources().getDimensionPixelSize(R.dimen.directory_header_height), true, getSectionCallback());
			recyclerView.addItemDecoration(headerItemDecoration);
		}

		ConfigUtils.adjustToolbar(this, getToolbar());
	}

	@Override
	public void onBackPressed() {
		this.finish();
	}

	@Override
	public void finish() {
		boolean animateOut = getIntent().getBooleanExtra(EXTRA_ANIMATE_OUT, false);

		super.finish();
		if (animateOut) {
			overridePendingTransition(R.anim.slide_in_left_short, R.anim.slide_out_right_short);
		}
	}
}
