/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.ListFragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.search.SearchBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ContactModel;

abstract public class MemberChooseActivity extends ThreemaToolbarActivity implements SearchView.OnQueryTextListener, MemberListFragment.SelectionListener {
    private final static int FRAGMENT_USERS = 0;
    private final static int FRAGMENT_WORK_USERS = 1;
    private final static int NUM_FRAGMENTS = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MODE_NEW_GROUP, MODE_ADD_TO_GROUP, MODE_NEW_DISTRIBUTION_LIST, MODE_PROFILE_PIC_RECIPIENTS})
    public @interface MemberChooseMode {
    }

    protected final static int MODE_NEW_GROUP = 1;
    protected final static int MODE_ADD_TO_GROUP = 2;
    protected final static int MODE_NEW_DISTRIBUTION_LIST = 3;
    protected final static int MODE_PROFILE_PIC_RECIPIENTS = 4;
    private static final String BUNDLE_QUERY_TEXT = "query";

    private MemberChoosePagerAdapter memberChoosePagerAdapter;
    private MenuItem searchMenuItem;
    private ThreemaSearchView searchView;

    protected ContactService contactService;
    protected ArrayList<String> excludedIdentities = new ArrayList<>();
    protected ArrayList<String> preselectedIdentities = new ArrayList<>();

    private TabLayout tabLayout;
    private final ArrayList<Integer> tabs = new ArrayList<>(NUM_FRAGMENTS);
    private Snackbar snackbar;
    private View rootView;
    private SearchBar searchBar;
    private ExtendedFloatingActionButton floatingActionButton;
    private String queryText;

    @Override
    public boolean onQueryTextSubmit(String query) {
        // Do something
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        int itemCount = memberChoosePagerAdapter.getCount();

        // apply filter to all adapters
        for (int currentItem = 0; currentItem < itemCount; currentItem++) {
            Fragment fragment = memberChoosePagerAdapter.getRegisteredFragment(currentItem);
            if (fragment != null) {
                FilterableListAdapter listAdapter = ((MemberListFragment) fragment).getAdapter();
                // adapter can be null if it has not been initialized yet (runs in different thread)
                if (listAdapter != null) {
                    listAdapter.getFilter().filter(newText);
                }
            }
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
        }
        ;

        // add notice, if desired
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            searchBar = (SearchBar) getToolbar();
            searchBar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (searchView.isIconified()) {
                        goHome();
                    } else {
                        searchView.setIconified(true);
                    }
                }
            });
            searchBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchView.setIconified(false);
                }
            });
            ConfigUtils.adjustSearchBarTextViewMargin(this, searchBar);

            if (getNotice() != 0) {
                final View noticeLayout = findViewById(R.id.notice_layout);
                final TextView noticeText = findViewById(R.id.notice_text);
                noticeText.setText(getNotice());
                noticeLayout.setVisibility(View.VISIBLE);

                findViewById(R.id.close_button).setOnClickListener(v -> AnimationUtil.collapse(noticeLayout, null, true));
            }
        }

        this.floatingActionButton = findViewById(R.id.floating);

        if (getMode() == MODE_PROFILE_PIC_RECIPIENTS) {
            floatingActionButton.hide();
        } else {
            this.floatingActionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    menuNext(getSelectedContacts());
                }
            });
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
        if (searchBar != null) {
            searchBar.setHint(subtitle);
        }
        if (searchView != null) {
            searchView.setQueryHint(getString(subtitle));
        }
    }

    protected void initList() {
        tabLayout = findViewById(R.id.sliding_tabs);
        tabs.clear();

        ViewPager viewPager = findViewById(R.id.pager);
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
                .setIcon(R.drawable.ic_work_outline)
                .setContentDescription(R.string.title_tab_work_users));
            tabs.add(FRAGMENT_WORK_USERS);
        }

        tabLayout.addTab(tabLayout.newTab()
            .setIcon(R.drawable.ic_person_outline)
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
                if (searchView != null) {
                    if (searchMenuItem != null) {
                        CharSequence query = searchView.getQuery();
                        if (TestUtil.isBlankOrNull(query)) {
                            invalidateOptionsMenu();
                            if (searchMenuItem.isActionViewExpanded()) {
                                searchMenuItem.collapseActionView();
                                onQueryTextChange(null);
                            }
                            searchView.setQuery("", false);
                            queryText = null;
                        } else {
                            searchMenuItem.getActionView().post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!searchMenuItem.isActionViewExpanded()) {
                                        searchMenuItem.expandActionView();
                                    }
                                    searchView.setQuery(query, true);
                                }
                            });
                            queryText = query.toString();
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            queryText = savedInstanceState.getString(BUNDLE_QUERY_TEXT, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.action_compose_message_search, menu);

        this.searchMenuItem = menu.findItem(R.id.menu_action_search);
        this.searchView = (ThreemaSearchView) this.searchMenuItem.getActionView();
        if (ConfigUtils.isLandscape(this)) {
            this.searchView.setMaxWidth(Integer.MAX_VALUE);
        }

        if (this.searchView != null) {
            ConfigUtils.adjustSearchViewPadding(searchView);
            this.searchView.setQueryHint(getString(R.string.title_select_contacts));
            this.searchView.setOnQueryTextListener(this);
            // Hide the hint of the search bar when the search view is opened to prevent it from
            // appearing on some devices
            this.searchView.setOnSearchClickListener(v -> {
                if (this.searchBar != null) {
                    this.searchBar.setHint("");
                }
            });
            // Show the hint of the search bar again when the search view is closed
            this.searchView.setOnCloseListener(() -> {
                if (this.searchBar != null) {
                    this.searchBar.setHint(R.string.title_select_contacts);
                }
                return false;
            });
            if (!TestUtil.isEmptyOrNull(queryText)) {
                this.searchMenuItem.expandActionView();
                this.searchView.setIconified(false);
                this.searchView.setQuery(queryText, true);
            }
        } else {
            this.searchMenuItem.setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                goHome();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void goHome() {
        if (getMode() != MODE_PROFILE_PIC_RECIPIENTS) {
            finish();
        } else {
            RuntimeUtil.runOnUiThread(() -> {
                if (searchView != null) {
                    new WindowInsetsControllerCompat(getWindow(), searchView).hide(WindowInsetsCompat.Type.ime());
                }
                menuNext(getSelectedContacts());
            });
        }
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

        @NonNull
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
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
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
                snackbar.getView().getLayoutParams().width = AppBarLayout.LayoutParams.MATCH_PARENT;
            }
            snackbar.setText(getMemberNames());
            if (!snackbar.isShown()) {
                snackbar.show();
            }
            if (getMode() == MODE_NEW_GROUP || getMode() == MODE_ADD_TO_GROUP || getMode() == MODE_NEW_DISTRIBUTION_LIST) {
                if (!floatingActionButton.isShown()) {
                    floatingActionButton.show();
                }
            }
        } else {
            if (snackbar != null && snackbar.isShown()) {
                snackbar.dismiss();
            }
            if (getMode() == MODE_NEW_GROUP) {
                if (!floatingActionButton.isShown()) {
                    floatingActionButton.show();
                }
            } else {
                if (floatingActionButton.isShown()) {
                    floatingActionButton.hide();
                }
            }
        }
    }

    private String getMemberNames() {
        StringBuilder builder = new StringBuilder();
        for (ContactModel contactModel : getSelectedContacts()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(NameUtil.getDisplayNameOrNickname(contactModel, true));
        }
        return builder.toString();
    }

    public void onQueryResultChanged(ListFragment listFragment, int count) {
        int tabPosition = memberChoosePagerAdapter.registeredFragments.indexOfValue(listFragment);
        TabLayout.Tab tab = tabLayout.getTabAt(tabPosition);

        if (tab != null) {
            if (count > 0) {
                tab.getOrCreateBadge().setNumber(count);
                tab.getBadge().setBackgroundColor(ConfigUtils.getColorFromAttribute(this, R.attr.colorPrimary));
                tab.getBadge().setVisible(true);
            } else {
                if (tab.getBadge() != null) {
                    tab.getBadge().setVisible(false);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (searchView != null) {
            CharSequence query = searchView.getQuery();
            outState.putString(BUNDLE_QUERY_TEXT, TextUtils.isEmpty(query) ? null : query.toString());
        }
        super.onSaveInstanceState(outState);
    }

    @MemberChooseMode
    protected abstract int getMode();

    @MainThread
    protected abstract void initData(Bundle savedInstanceState);

    protected abstract @StringRes int getNotice();

    protected abstract void menuNext(List<ContactModel> selectedContacts);
}
