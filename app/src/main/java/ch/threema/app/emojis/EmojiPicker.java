/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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
import android.widget.LinearLayout;

import com.google.android.material.tabs.TabLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.viewpager.widget.ViewPager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.LockableViewPager;

public class EmojiPicker extends LinearLayout {
	private static final Logger logger = LoggerFactory.getLogger(EmojiPicker.class);

	private Context context;
	private View emojiPickerView;
	private LockableViewPager viewPager;
	private EmojiRecent emojiRecent;
	private ArrayList<EmojiPickerListener> emojiPickerListeners = new ArrayList<>();
	private EmojiKeyListener emojiKeyListener;
	private PreferenceService preferenceService;
	private DiverseEmojiPopup diverseEmojiPopup;
	private EmojiDetailPopup emojiDetailPopup;
	private RecentEmojiRemovePopup recentRemovePopup;
	private HashMap<String, String> diversePrefs;

	public final static String RECENT_VIEW_TAG = "0";

	public EmojiPicker(Context context) {
		this(context, null);
	}

	public EmojiPicker(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiPicker(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		this.preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
		this.diversePrefs = preferenceService.getDiverseEmojiPrefs2();
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

	public void init(Context context) {
		this.context = context;
		this.emojiRecent = new EmojiRecent();
		this.emojiPickerView = LayoutInflater.from(getContext()).inflate(R.layout.emoji_picker, this, true);

		this.recentRemovePopup = new RecentEmojiRemovePopup(context,  this.emojiPickerView);
		this.recentRemovePopup.setListener(this::removeEmojiFromRecent);

		this.emojiDetailPopup = new EmojiDetailPopup(context, this.emojiPickerView);
		this.emojiDetailPopup.setListener(emojiSequence -> {
			if (emojiKeyListener != null) {
				emojiKeyListener.onEmojiClick(emojiSequence);
				addEmojiToRecent(emojiSequence);
			}
		});

		this.diverseEmojiPopup = new DiverseEmojiPopup(context, this.emojiPickerView);
		this.diverseEmojiPopup.setListener(new DiverseEmojiPopup.DiverseEmojiPopupListener() {
			@Override
			public void onDiverseEmojiClick(String parentEmojiSequence, String emojiSequence) {
				emojiKeyListener.onEmojiClick(emojiSequence);

				diversePrefs.remove(parentEmojiSequence);
				diversePrefs.put(parentEmojiSequence, emojiSequence);
				addEmojiToRecent(emojiSequence);
				preferenceService.setDiverseEmojiPrefs2(diversePrefs);
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

	public void show(int height) {
		final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
		setLayoutParams(params);

		logger.info("Show EmojiPicker. Height = " + height);

		setVisibility(VISIBLE);

		for (EmojiPickerListener listener : this.emojiPickerListeners) {
			listener.onEmojiPickerOpen();
		}
	}

	public void hide() {
		if (this.diverseEmojiPopup != null && this.diverseEmojiPopup.isShowing()) {
			this.diverseEmojiPopup.dismiss();
		}
		setVisibility(GONE);

		for (EmojiPickerListener listener : this.emojiPickerListeners) {
			listener.onEmojiPickerClose();
		}
		this.emojiRecent.saveToPrefs();
	}

	@SuppressLint("ClickableViewAccessibility")
	private EmojiPagerAdapter initPagerAdapter() {
		EmojiGridAdapter.KeyClickListener keyClickListener = new EmojiGridAdapter.KeyClickListener() {
			@Override
			public void onEmojiKeyClicked(String emojiCodeString) {
				emojiKeyListener.onEmojiClick(emojiCodeString);
				addEmojiToRecent(emojiCodeString);
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

		this.viewPager = emojiPickerView.findViewById(R.id.emoji_pager);
		int currentItem = this.viewPager.getCurrentItem();

		EmojiPagerAdapter emojiPagerAdapter = new EmojiPagerAdapter(
			context,
			this,
			this.emojiRecent,
			this.diversePrefs,
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
					if (emojiRecent.syncRecents()) {
						refreshRecentView();
					}
				}
			}

			@Override
			public void onPageScrollStateChanged(int state) {

			}
		});

		// show first regular tab if there are no recent emojis
		if (currentItem == 0 && emojiRecent.getNumberOfRecentEmojis() == 0) {
			this.viewPager.setCurrentItem(1);
		}

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

		return emojiPagerAdapter;
	}

	private void onRecentListLongClicked(View view, String emojiSequence) {
		recentRemovePopup.show(view, emojiSequence);
	}

	private void onEmojiLongClicked(View view, String emojiSequence) {
		EmojiInfo emojiInfo = EmojiUtil.getEmojiInfo(emojiSequence);
		if (emojiInfo != null && emojiInfo.diversityFlag == EmojiSpritemap.DIVERSITY_PARENT) {
			diverseEmojiPopup.show(view, emojiSequence, diversePrefs);
		} else {
			emojiDetailPopup.show(view, emojiSequence);
		}
	}

	public int getNumberOfPages() {
		return EmojiManager.getNumberOfEmojiGroups();
	}

	public String getGroupTitle(int id) {
		return context.getString(EmojiManager.getGroupName(id)).toUpperCase();
	}

	public void refreshRecentView() {
		// update recent gridview
		GridView view = emojiPickerView.findViewWithTag(RECENT_VIEW_TAG);

		if (view != null) {
			EmojiGridAdapter emojiGridAdapter = (EmojiGridAdapter) view.getAdapter();
			emojiGridAdapter.notifyDataSetChanged();
		}
	}

	public void addEmojiToRecent(String emojiSequence) {
		emojiRecent.add(emojiSequence);
	}

	public void removeEmojiFromRecent(String emojiSequence) {
		emojiRecent.remove(emojiSequence);
		refreshRecentView();
	}

	public interface EmojiPickerListener {
		void onEmojiPickerOpen();
		void onEmojiPickerClose();
	}

	public interface EmojiKeyListener {
		void onBackspaceClick();
		void onEmojiClick(String emojiCodeString);
	}
}
