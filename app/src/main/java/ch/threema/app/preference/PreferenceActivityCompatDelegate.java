/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

/*
 * Copyright (c) 2018 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package ch.threema.app.preference;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle.State;
import androidx.preference.Preference;
import ch.threema.app.R;

/**
 * @author [大前良介 (OHMAE Ryosuke)](mailto:ryo@mm2d.net)
 */
public class PreferenceActivityCompatDelegate {
	public interface Connector {
		void onBuildHeaders(@NonNull List<Header> target);

		boolean onIsMultiPane();

		boolean isValidFragment(@Nullable String fragmentName);
	}

	public static final long HEADER_ID_UNDEFINED = -1;

	private static final String HEADERS_TAG = ":android:headers";
	private static final String CUR_HEADER_TAG = ":android:cur_header";
	private static final String BACK_STACK_PREFS = ":android:prefs";

	@NonNull
	private final FragmentActivity mActivity;
	@NonNull
	private final Connector mConnector;
	@NonNull
	private final OnItemClickListener mOnClickListener =
			(parent, view, position, id) -> onListItemClick(position);
	@NonNull
	private final ArrayList<Header> mHeaders = new ArrayList<>();
	private ListAdapter mAdapter;
	private ListView mList;
	private boolean mFinishedStart = false;
	private FrameLayout mListFooter;
	private ViewGroup mPrefsContainer;
	private ViewGroup mHeadersContainer;
	private boolean mSinglePane;
	private Header mCurHeader;
	private final Handler mHandler = new Handler();
	private Fragment mFragment;

	private final Runnable mRequestFocus = new Runnable() {
		public void run() {
			mList.focusableViewAvailable(mList);
		}
	};

	private final Runnable mBuildHeaders = new Runnable() {
		@Override
		public void run() {
			mHeaders.clear();
			mConnector.onBuildHeaders(mHeaders);
			if (mAdapter instanceof BaseAdapter) {
				((BaseAdapter) mAdapter).notifyDataSetChanged();
			}
			if (mCurHeader != null) {
				final Header mappedHeader = findBestMatchingHeader(mCurHeader, mHeaders);
				if (mappedHeader != null) {
					setSelectedHeader(mappedHeader);
				}
			}
		}
	};

	public PreferenceActivityCompatDelegate(
			@NonNull final FragmentActivity activity,
			@NonNull final Connector connector) {
		mActivity = activity;
		mConnector = connector;
	}

	@NonNull
	private Context getContext() {
		return mActivity;
	}

	@NonNull
	private Resources getResources() {
		return mActivity.getResources();
	}

	private boolean isResumed() {
		return mActivity.getLifecycle().getCurrentState() == State.RESUMED;
	}

	@NonNull
	private FragmentManager getFragmentManager() {
		return mActivity.getSupportFragmentManager();
	}

	@Nullable
	private CharSequence getTitle() {
		return mActivity.getTitle();
	}

	private void setTitle(@Nullable final CharSequence title) {
		mActivity.setTitle(title);
	}

	private void setContentView(@LayoutRes final int layoutResID) {
		mActivity.setContentView(layoutResID);
	}

	@Nullable
	private <T extends View> T findViewById(@IdRes final int id) {
		return mActivity.findViewById(id);
	}

	public void onCreate(@Nullable final Bundle savedInstanceState) {
		setContentView(R.layout.pref_content);
		mList = findViewById(R.id.list);
		mList.setOnItemClickListener(mOnClickListener);
		if (mFinishedStart) {
			setListAdapter(mAdapter);
		}
		mHandler.post(mRequestFocus);
		mFinishedStart = true;
		mListFooter = findViewById(R.id.list_footer);
		mPrefsContainer = findViewById(R.id.prefs_frame);
		mHeadersContainer = findViewById(R.id.headers);
		mSinglePane = !mConnector.onIsMultiPane();

		if (savedInstanceState != null) {
			final ArrayList<Header> headers = savedInstanceState.getParcelableArrayList(HEADERS_TAG);
			if (headers != null) {
				mHeaders.addAll(headers);
				final int curHeader = savedInstanceState.getInt(CUR_HEADER_TAG,
						(int) HEADER_ID_UNDEFINED);
				if (curHeader >= 0 && curHeader < mHeaders.size()) {
					setSelectedHeader(mHeaders.get(curHeader));
				} else if (!mSinglePane) {
					switchToHeader(onGetInitialHeader());
				}
			} else {
				showBreadCrumbs(getTitle());
			}
		} else {
			mConnector.onBuildHeaders(mHeaders);
			if (!mSinglePane && mHeaders.size() > 0) {
				switchToHeader(onGetInitialHeader());
			}
		}
		if (mHeaders.size() > 0) {
			setListAdapter(new HeaderAdapter(getContext(), mHeaders));
			if (!mSinglePane) {
				mList.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
			}
		}
		if (mSinglePane) {
			if (mCurHeader != null) {
				mHeadersContainer.setVisibility(View.GONE);
			} else {
				mPrefsContainer.setVisibility(View.GONE);
			}
		} else if (mHeaders.size() > 0 && mCurHeader != null) {
			setSelectedHeader(mCurHeader);
		}
	}

	public void onDestroy() {
		mHandler.removeCallbacks(mBuildHeaders);
		mHandler.removeCallbacks(mRequestFocus);
	}

	public void onSaveInstanceState(@NonNull final Bundle outState) {
		if (mHeaders.size() > 0) {
			outState.putParcelableArrayList(HEADERS_TAG, mHeaders);
			if (mCurHeader != null) {
				final int index = mHeaders.indexOf(mCurHeader);
				if (index >= 0) {
					outState.putInt(CUR_HEADER_TAG, index);
				}
			}
		}
	}

	public void onRestoreInstanceState(@NonNull final Bundle state) {
		if (!mSinglePane) {
			if (mCurHeader != null) {
				setSelectedHeader(mCurHeader);
			}
		}
	}

	public boolean onBackPressed() {
		final FragmentManager manager = getFragmentManager();

		if (!mSinglePane) {
			return false;
		}
/*
		if (manager.getBackStackEntryCount() > 0) {
			manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		}
*/
		if (mCurHeader == null) {
			return false;
		}

		if (mFragment != null) {
			manager.beginTransaction()
					.remove(mFragment)
					.commitAllowingStateLoss();
			mFragment = null;
		}
		mCurHeader = null;
		mPrefsContainer.setVisibility(View.GONE);
		mHeadersContainer.setVisibility(View.VISIBLE);
		showBreadCrumbs(getTitle());
		mList.clearChoices();
		return true;
	}

	private void setListAdapter(final ListAdapter adapter) {
		mAdapter = adapter;
		mList.setAdapter(adapter);
	}

	public int getSelectedItemPosition() {
		return mList.getSelectedItemPosition();
	}

	public boolean hasHeaders() {
		return mHeadersContainer != null && mHeadersContainer.getVisibility() == View.VISIBLE;
	}

	@NonNull
	public List<Header> getHeaders() {
		return mHeaders;
	}

	public boolean isMultiPane() {
		return !mSinglePane;
	}

	@NonNull
	private Header onGetInitialHeader() {
		for (int i = 0; i < mHeaders.size(); i++) {
			final Header h = mHeaders.get(i);
			if (h.fragment != null) {
				return h;
			}
		}
		throw new IllegalStateException("Must have at least one header with a fragment");
	}

	public void invalidateHeaders() {
		mHandler.removeCallbacks(mBuildHeaders);
		mHandler.post(mBuildHeaders);
	}

	public void loadHeadersFromResource(
			@XmlRes final int resId,
			@NonNull final List<Header> target) {
		HeaderLoader.loadFromResource(getContext(), resId, target);
	}

	public void setListFooter(@NonNull final View view) {
		mListFooter.removeAllViews();
		mListFooter.addView(view, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT));
	}

	private void onListItemClick(final int position) {
		if (!isResumed()) {
			return;
		}
		if (mAdapter != null) {
			final Object item = mAdapter.getItem(position);
			if (item instanceof Header) onHeaderClick((Header) item);
		}
	}

	private void onHeaderClick(@NonNull final Header header) {
		if (header.fragment != null) {
			switchToHeader(header);
		} else if (header.intent != null) {
			getContext().startActivity(header.intent);
		}
	}

	public void switchToHeader(@NonNull final Header header) {
		if (mCurHeader == header) {
			getFragmentManager().popBackStack(BACK_STACK_PREFS, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		} else {
			if (header.fragment == null) {
				throw new IllegalStateException("can't switch to header that has no fragment");
			}
			mHandler.post(() -> {
				switchToHeaderInner(header.fragment, header.fragmentArguments);
				setSelectedHeader(header);
			});
		}
	}

	private void switchToHeaderInner(
			@NonNull final String fragmentName,
			@Nullable final Bundle args) {
		final FragmentManager fragmentManager = getFragmentManager();
		fragmentManager.popBackStack(BACK_STACK_PREFS, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		if (!mConnector.isValidFragment(fragmentName)) {
			throw new IllegalArgumentException("Invalid fragment for this activity: " + fragmentName);
		}
		mFragment = Fragment.instantiate(getContext(), fragmentName, args);
		fragmentManager.beginTransaction()
				.setTransition(FragmentTransaction.TRANSIT_NONE)
				.replace(R.id.prefs, mFragment)
				.commitAllowingStateLoss();

		if (mSinglePane && mPrefsContainer.getVisibility() == View.GONE) {
			mPrefsContainer.setVisibility(View.VISIBLE);
			mHeadersContainer.setVisibility(View.GONE);
		}
	}

	private void setSelectedHeader(@NonNull final Header header) {
		mCurHeader = header;
		final int index = mHeaders.indexOf(header);
		if (index >= 0) {
			mList.setItemChecked(index, true);
		} else {
			mList.clearChoices();
		}
		showBreadCrumbs(header);
	}

	private void showBreadCrumbs(@NonNull final Header header) {
		final Resources resources = getResources();
		CharSequence title = header.getBreadCrumbTitle(resources);
		if (title == null) title = header.getTitle(resources);
		if (title == null) title = getTitle();
		showBreadCrumbs(title);
	}

	private void showBreadCrumbs(@Nullable final CharSequence title) {
		setTitle(title);
	}

	public void startPreferenceFragment(@NonNull final Preference pref) {
		final Fragment fragment = Fragment.instantiate(getContext(), pref.getFragment(), pref.getExtras());
		getFragmentManager().beginTransaction()
				.replace(R.id.prefs, fragment)
				.setBreadCrumbTitle(pref.getTitle())
				.setTransition(FragmentTransaction.TRANSIT_NONE)
				.addToBackStack(BACK_STACK_PREFS)
				.commitAllowingStateLoss();
	}

	@Nullable
	private Header findBestMatchingHeader(
			@NonNull final Header current,
			@NonNull final ArrayList<Header> from) {
		final ArrayList<Header> matches = new ArrayList<>();
		for (final Header oh : from) {
			if (current == oh || (current.id != HEADER_ID_UNDEFINED && current.id == oh.id)) {
				matches.clear();
				matches.add(oh);
				break;
			}
			if (current.fragment != null) {
				if (current.fragment.equals(oh.fragment)) {
					matches.add(oh);
				}
			} else if (current.intent != null) {
				if (current.intent.equals(oh.intent)) {
					matches.add(oh);
				}
			} else if (current.title != null) {
				if (current.title.equals(oh.title)) {
					matches.add(oh);
				}
			}
		}
		if (matches.size() == 1) {
			return matches.get(0);
		}
		for (final Header oh : matches) {
			if (current.fragmentArguments != null && current.fragmentArguments.equals(oh.fragmentArguments)) {
				return oh;
			}
			if (current.extras != null && current.extras.equals(oh.extras)) {
				return oh;
			}
			if (current.title != null && current.title.equals(oh.title)) {
				return oh;
			}
		}
		return null;
	}
}
