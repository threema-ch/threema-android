/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.app.emojis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.android.material.tabs.TabLayout;

import org.slf4j.Logger;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;
import ch.threema.app.R;
import ch.threema.app.ui.LockableViewPager;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.base.utils.LoggingUtil;

public class EmojiPicker extends LinearLayout implements EmojiSearchWidget.EmojiSearchListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("EmojiPicker");

	private final ArrayList<EmojiPickerListener> emojiPickerListeners = new ArrayList<>();
	private EmojiService emojiService;

	private View emojiPickerView;
	private LockableViewPager viewPager;
	private EmojiKeyListener emojiKeyListener;
	private DiverseEmojiPopup diverseEmojiPopup;
	private EmojiDetailPopup emojiDetailPopup;
	private RecentEmojiRemovePopup recentRemovePopup;
	private RelativeLayout pickerHeader;
	private EmojiSearchWidget emojiSearchWidget;

	private final LinearLayout.LayoutParams searchLayoutParams = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT
	);
	private ViewGroup.LayoutParams pickerLayoutParams = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT
	);

	public final static String RECENT_VIEW_TAG = "0";

	public EmojiPicker(Context context) {
		this(context, null);
	}

	public EmojiPicker(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiPicker(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public void addEmojiPickerListener(EmojiPickerListener listener) {
		this.emojiPickerListeners.add(listener);
	}

	public void removeEmojiPickerListener(EmojiPickerListener listener) {
		this.emojiPickerListeners.remove(listener);
	}

	public void setEmojiKeyListener(EmojiKeyListener listener) {
		this.emojiKeyListener = listener;
	}

	public void init(EmojiService emojiService) {
		this.emojiService = emojiService;

		this.emojiPickerView = LayoutInflater.from(getContext()).inflate(R.layout.emoji_picker, this, true);

		initEmojiSearchWidget();

		this.recentRemovePopup = new RecentEmojiRemovePopup(getContext(),  this.emojiPickerView);
		this.recentRemovePopup.setListener(this::removeEmojiFromRecent);

		this.emojiDetailPopup = new EmojiDetailPopup(getContext(), this.emojiPickerView);
		this.emojiDetailPopup.setListener(emojiSequence -> {
			if (emojiKeyListener != null) {
				emojiKeyListener.onEmojiClick(emojiSequence);
				emojiService.addToRecentEmojis(emojiSequence);
			}
		});

		this.diverseEmojiPopup = new DiverseEmojiPopup(getContext(), this.emojiPickerView);
		this.diverseEmojiPopup.setListener(new DiverseEmojiPopup.DiverseEmojiPopupListener() {
			@Override
			public void onDiverseEmojiClick(String parentEmojiSequence, String emojiSequence) {
				emojiKeyListener.onEmojiClick(emojiSequence);
				emojiService.setDiverseEmojiPreference(parentEmojiSequence, emojiSequence);
				emojiService.addToRecentEmojis(emojiSequence);
			}

			@Override
			public void onOpen() {
				if (viewPager != null) {
					viewPager.lock(true);
				}
			}

			@Override
			public void onClose() {
				if (viewPager != null) {
					viewPager.lock(false);
				}
			}
		});

		initPagerAdapter();
	}

	public boolean isShown() {
		return getVisibility() == VISIBLE;
	}

	public void show(int pickerHeight) {
		pickerLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pickerHeight);
		showEmojiPicker();
	}

	public void hide() {
		if (this.diverseEmojiPopup != null && this.diverseEmojiPopup.isShowing()) {
			this.diverseEmojiPopup.dismiss();
		}
		setVisibility(GONE);

		for (EmojiPickerListener listener : this.emojiPickerListeners) {
			listener.onEmojiPickerClose();
		}
		this.emojiService.saveRecentEmojis();
	}

	public void onKeyboardShown() {
		if (!emojiSearchWidget.isShown() || !emojiSearchWidget.searchInput.hasFocus()) {
			hide();
		}
	}

	public void onKeyboardHidden() {
		if (emojiSearchWidget.isShown()) {
			emojiService.saveRecentEmojis();
		}
	}

	@Override
	public void onShowPicker() {
		// Switch back from emoji search to picker.
		// In order to avoid flickering the displaying is delegated to the enclosing listener
		setVisibility(GONE);
		if (emojiKeyListener != null) {
			emojiKeyListener.onShowPicker();
		}
	}

	@Override
	public void onEmojiClick(@NonNull String emojiSequence) {
		if (emojiKeyListener != null) {
			emojiKeyListener.onEmojiClick(emojiSequence);
		}
	}

	@Override
	public void onHideEmojiSearch() {
		hide();
	}

	private void showEmojiSearch() {
		setLayoutParams(searchLayoutParams);
		emojiSearchWidget.setVisibility(VISIBLE);
		emojiSearchWidget.searchInput.requestFocus();
		EditTextUtil.showSoftKeyboard(emojiSearchWidget.searchInput);
		pickerHeader.setVisibility(GONE);
		viewPager.setVisibility(GONE);
	}

	private void showEmojiPicker() {
		logger.info("Show EmojiPicker. Height = {}", pickerLayoutParams.height);
		refreshRecentView();

		setLayoutParams(pickerLayoutParams);

		pickerHeader.setVisibility(VISIBLE);
		viewPager.setVisibility(VISIBLE);
		emojiSearchWidget.setVisibility(GONE);

		setVisibility(VISIBLE);

		for (EmojiPickerListener listener : this.emojiPickerListeners) {
			listener.onEmojiPickerOpen();
		}
	}

	private void initEmojiSearchWidget() {
		emojiSearchWidget = findViewById(R.id.emoji_search);
		emojiSearchWidget.init(this, emojiService);
	}

	private void initPagerAdapter() {
		EmojiGridAdapter.KeyClickListener keyClickListener = new EmojiGridAdapter.KeyClickListener() {
			@Override
			public void onEmojiKeyClicked(String emojiCodeString) {
				emojiKeyListener.onEmojiClick(emojiCodeString);
				emojiService.addToRecentEmojis(emojiCodeString);
			}

			@Override
			public void onEmojiKeyLongClicked(View view, String emojiCodeString) {
				onEmojiLongClicked(view, emojiCodeString);
			}

			@Override
			public void onRecentLongClicked(View view, String emojiCodeString) {
				onRecentListLongClicked(view, emojiCodeString);
			}
		};

		pickerHeader = findViewById(R.id.emoji_picker_header);
		viewPager = findViewById(R.id.emoji_pager);
		int currentItem = this.viewPager.getCurrentItem();

		EmojiPagerAdapter emojiPagerAdapter = new EmojiPagerAdapter(
			getContext(),
			this,
			emojiService,
			keyClickListener);

		this.viewPager.setAdapter(emojiPagerAdapter);
		this.viewPager.setOffscreenPageLimit(1);

		final TabLayout tabLayout = emojiPickerView.findViewById(R.id.sliding_tabs);
		tabLayout.removeAllTabs();

		for (EmojiGroup emojiGroup : EmojiManager.getEmojiGroups()) {
			tabLayout.addTab(
				tabLayout.newTab()
					.setIcon(emojiGroup.getGroupIcon())
					.setContentDescription(emojiGroup.getGroupName())
			);
		}

		viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
		tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));
		this.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			}

			@Override
			public void onPageSelected(int position) {
				if (position == 0) {
					if (emojiService.syncRecentEmojis()) {
						refreshRecentView();
					}
				}
			}

			@Override
			public void onPageScrollStateChanged(int state) {

			}
		});

		// show first regular tab if there are no recent emojis
		if (currentItem == 0 && emojiService.hasNoRecentEmojis()) {
			this.viewPager.setCurrentItem(1);
		}

		initEmojiSearchButton();
		initEmojiBackspaceButton();
	}

	@SuppressLint("ClickableViewAccessibility")
	private void initEmojiBackspaceButton() {
		LinearLayout backspaceButton = emojiPickerView.findViewById(R.id.backspace_button);
		if (backspaceButton != null) {
			backspaceButton.setOnTouchListener(new OnTouchListener() {
				private Handler handler;

				final Runnable action = new Runnable() {
					@Override
					public void run() {
						emojiKeyListener.onBackspaceClick();
						handler.postDelayed(this, 100);
					}
				};

				@Override
				public boolean onTouch(View v, MotionEvent event) {
					switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN:
							if (handler != null) return true;
							handler = new Handler();
							handler.postDelayed(action, 600);
							break;
						case MotionEvent.ACTION_UP:
							if (handler == null) return true;
							handler.removeCallbacks(action);
							handler = null;
							emojiKeyListener.onBackspaceClick();
							break;
					}
					return false;
				}
			});
		}
	}

	private void initEmojiSearchButton() {
		ImageButton searchButton = emojiPickerView.findViewById(R.id.search_button);
		if (emojiService.isEmojiSearchAvailable()) {
			logger.debug("Emoji search available; prepare search index.");
			emojiService.prepareEmojiSearch();
			searchButton.setVisibility(View.VISIBLE);
			searchButton.setOnClickListener(v -> showEmojiSearch());
		} else {
			searchButton.setVisibility(View.GONE);
			searchButton.setOnClickListener(null);
		}
	}

	private void onRecentListLongClicked(View view, String emojiSequence) {
		recentRemovePopup.show(view, emojiSequence);
	}

	private void onEmojiLongClicked(View view, String emojiSequence) {
		EmojiInfo emojiInfo = EmojiUtil.getEmojiInfo(emojiSequence);
		if (emojiInfo != null && emojiInfo.diversityFlag == EmojiSpritemap.DIVERSITY_PARENT) {
			diverseEmojiPopup.show(view, emojiSequence);
		} else {
			emojiDetailPopup.show(view, emojiSequence);
		}
	}

	public int getNumberOfPages() {
		return EmojiManager.getNumberOfEmojiGroups();
	}

	public String getGroupTitle(int id) {
		return getContext().getString(EmojiManager.getGroupName(id)).toUpperCase();
	}

	private void refreshRecentView() {
		// update recent gridview
		GridView view = emojiPickerView.findViewWithTag(RECENT_VIEW_TAG);

		if (view != null) {
			EmojiGridAdapter emojiGridAdapter = (EmojiGridAdapter) view.getAdapter();
			emojiGridAdapter.notifyDataSetChanged();
		}
	}

	private void removeEmojiFromRecent(String emojiSequence) {
		emojiService.removeRecentEmoji(emojiSequence);
		refreshRecentView();
	}

	public interface EmojiPickerListener {
		void onEmojiPickerOpen();
		void onEmojiPickerClose();
	}

	public interface EmojiKeyListener {
		void onBackspaceClick();
		void onEmojiClick(String emojiCodeString);
		void onShowPicker();
	}
}
