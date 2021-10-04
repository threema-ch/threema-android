/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.os.Bundle;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import ch.threema.app.R;
import ch.threema.app.adapters.FilterableListAdapter;
import ch.threema.app.fragments.MemberListFragment;
import ch.threema.app.fragments.UserMemberListFragment;
import ch.threema.app.fragments.WorkUserMemberListFragment;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.ThreemaSearchView;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.SnackbarUtil;
import ch.threema.storage.models.ContactModel;

abstract public class MemberChooseActivity extends ThreemaToolbarActivity implements SearchView.OnQueryTextListener, MemberListFragment.SelectionListener {
	private final static int FRAGMENT_USERS = 0;
	private final static int FRAGMENT_WORK_USERS = 1;
	private final static int NUM_FRAGMENTS = 2;

	private MemberChoosePagerAdapter memberChoosePagerAdapter;
	private MenuItem searchMenuItem;
	private ThreemaSearchView searchView;

	protected ContactService contactService;
	protected ArrayList<String> excludedIdentities = new ArrayList<>();
	protected ArrayList<String> preselectedIdentities = new ArrayList<>();

	private ViewPager viewPager;
	private final ArrayList<Integer> tabs = new ArrayList<>(NUM_FRAGMENTS);
	private Snackbar snackbar;
	private View rootView;

	@Override
	public boolean onQueryTextSubmit(String query) {
		// Do something
		return true;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		int currentItem = viewPager.getCurrentItem();
		Fragment fragment = memberChoosePagerAdapter.getRegisteredFragment(currentItem);

		if (fragment != null) {
			FilterableListAdapter listAdapter = ((MemberListFragment) fragment).getAdapter();

			// adapter can be null if it has not been initialized yet (runs in different thread)
			if (listAdapter == null) return false;
			listAdapter.getFilter().filter(newText);
		}
		return true;
	}

	public int getLayoutResource() {
		return R.layout.activity_member_choose_tabbed;
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		};

		// add notice, if desired
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			Toolbar toolbar = getToolbar();
			if (toolbar != null) {
				actionBar.setTitle(null);
			}
			if (getNotice() != 0) {
				final TextView noticeText = findViewById(R.id.notice_text);
				final LinearLayout noticeLayout = findViewById(R.id.notice_layout);
				noticeText.setText(getNotice());
				noticeLayout.setVisibility(View.VISIBLE);

				ImageView closeButton = findViewById(R.id.close_button);
				closeButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						AnimationUtil.collapse(noticeLayout);
					}
				});
			}
		}

		this.rootView = findViewById(R.id.coordinator);

		try {
			this.contactService = serviceManager.getContactService();
		} catch (Exception e) {
			LogUtil.exception(e, this);
			return false;
		}
		return true;
	}

	@MainThread
	protected void updateToolbarTitle(@StringRes int title, @StringRes int subtitle) {
		getToolbar().setTitle(title);
		getToolbar().setSubtitle(subtitle);
	}

	protected void initList() {
		final TabLayout tabLayout = findViewById(R.id.sliding_tabs);
		tabs.clear();

		viewPager = findViewById(R.id.pager);
		if (viewPager == null || tabLayout == null) {
			finish();
			return;
		}

		tabLayout.clearOnTabSelectedListeners();
		tabLayout.removeAllTabs();

		viewPager.clearOnPageChangeListeners();
		viewPager.setAdapter(null);
		viewPager.removeAllViews();

		if (ConfigUtils.isWorkBuild()) {
			tabLayout.addTab(tabLayout.newTab()
				.setIcon(ConfigUtils.getThemedDrawable(this, R.drawable.ic_work_outline))
				.setContentDescription(R.string.title_tab_work_users));
			tabs.add(FRAGMENT_WORK_USERS);
		}

		tabLayout.addTab(tabLayout.newTab()
			.setIcon(ConfigUtils.getThemedDrawable(this, R.drawable.ic_person_outline))
			.setContentDescription(R.string.title_tab_users));

		tabs.add(FRAGMENT_USERS);

		// keeps inactive tabs from being destroyed causing all kinds of problems with lingering AsyncTasks on the the adapter
		viewPager.setVisibility(View.VISIBLE);
		viewPager.setOffscreenPageLimit(1);

		tabLayout.setVisibility(tabs.size() > 1 ? View.VISIBLE : View.GONE);
		tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

		memberChoosePagerAdapter = new MemberChoosePagerAdapter(getSupportFragmentManager());
		viewPager.setAdapter(memberChoosePagerAdapter);
		viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
		tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));
		viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				if (searchMenuItem != null) {
					searchMenuItem.collapseActionView();
					if (searchView != null) {
						searchView.setQuery("", false);
					}
				}
				invalidateOptionsMenu();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_member_choose, menu);

		if (!getAddNextButton()) {
			MenuItem checkItem = menu.findItem(R.id.menu_next);
			checkItem.setVisible(false);
		}

		this.searchMenuItem = menu.findItem(R.id.menu_search_messages);
		this.searchView = (ThreemaSearchView) this.searchMenuItem.getActionView();

		if (this.searchView != null) {
			this.searchView.setQueryHint(getString(R.string.hint_filter_list));
			this.searchView.setOnQueryTextListener(this);
		} else {
			this.searchMenuItem.setVisible(false);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				if (getAddNextButton()) {
					finish();
					return true;
				}
				/* fallthrough */
			case R.id.menu_next:
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (searchView != null) {
							new WindowInsetsControllerCompat(getWindow(), searchView).hide(WindowInsetsCompat.Type.ime());
						}
						menuNext(getSelectedContacts());
					}
				});
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected List<ContactModel> getSelectedContacts() {
		Set<ContactModel> contacts = new HashSet<>();
		MemberListFragment fragment;

		for (int i = 0; i < NUM_FRAGMENTS; i++) {
			fragment = (MemberListFragment) memberChoosePagerAdapter.getRegisteredFragment(i);
			if (fragment != null) {
				contacts.addAll(fragment.getSelectedContacts());
			}
		}

		return new ArrayList<>(contacts);
	}

	public class MemberChoosePagerAdapter extends FragmentPagerAdapter {
		// these globals are not persistent across orientation changes (at least in Android <= 4.1)!
		SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();

		public MemberChoosePagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {

			Fragment fragment = null;

			switch (tabs.get(position)) {
				case FRAGMENT_USERS:
					fragment = new UserMemberListFragment();
					break;
				case FRAGMENT_WORK_USERS:
					fragment = new WorkUserMemberListFragment();
					break;
			}

			if (fragment != null) {
				Bundle args = new Bundle();
				args.putStringArrayList(MemberListFragment.BUNDLE_ARG_EXCLUDED, excludedIdentities);
				args.putStringArrayList(MemberListFragment.BUNDLE_ARG_PRESELECTED, preselectedIdentities);
				fragment.setArguments(args);
			}

			return fragment;
		}

		@Override
		public int getCount() {
			return tabs.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (tabs.get(position)) {
				case FRAGMENT_USERS:
					return getString(R.string.title_tab_users).toUpperCase();
				case FRAGMENT_WORK_USERS:
					return getString(R.string.title_tab_work_users).toUpperCase();
			}
			return null;
		}

		@NonNull
		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			Fragment fragment = (Fragment) super.instantiateItem(container, position);
			registeredFragments.put(position, fragment);
			return fragment;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			registeredFragments.remove(position);
			super.destroyItem(container, position, object);
		}

		public Fragment getRegisteredFragment(int position) {
			return registeredFragments.get(position);
		}
	}

	@Override
	public void onSelectionChanged() {
		List<ContactModel> contacts = getSelectedContacts();

		if (contacts.size() > 0) {
			if (snackbar == null) {
				snackbar = SnackbarUtil.make(rootView, "", Snackbar.LENGTH_INDEFINITE, 4);
				snackbar.setBackgroundTint(ConfigUtils.getColorFromAttribute(this, R.attr.colorAccent));
				snackbar.getView().getLayoutParams().width = AppBarLayout.LayoutParams.MATCH_PARENT;
			}
			snackbar.setTextColor(ConfigUtils.getColorFromAttribute(this, R.attr.colorOnSecondary));
			snackbar.setText(getMemberNames());
			if (!snackbar.isShown()) {
				snackbar.show();
			}
		} else {
			if (snackbar != null && snackbar.isShown()) {
				snackbar.dismiss();
			}
		}
	}

	private String getMemberNames() {
		StringBuilder builder = new StringBuilder();
		for(ContactModel contactModel: getSelectedContacts()) {
			if(builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(NameUtil.getDisplayNameOrNickname(contactModel, true));
		}
		return builder.toString();
	}


	protected abstract boolean getAddNextButton();

	@MainThread
	protected abstract void initData(Bundle savedInstanceState);
	protected abstract @StringRes int getNotice();
	protected abstract void menuNext(List<ContactModel> selectedContacts);
}
