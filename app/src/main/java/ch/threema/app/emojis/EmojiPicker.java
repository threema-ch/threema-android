/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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
import android.app.Activity;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ch.threema.app.R;
import ch.threema.app.emojireactions.EmojiReactionsGridAdapter;
import ch.threema.app.ui.LockableViewPager;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.EmojiReactionData;

public class EmojiPicker extends LinearLayout implements EmojiSearchWidget.EmojiSearchListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("EmojiPicker");

    private Activity activity;
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
    private List<EmojiReactionData> emojiReactions;
    private boolean isKeyboardAnimated = false; // whether keyboard animation is enabled for this activity

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

    public void init(Activity activity, EmojiService emojiService, boolean isKeyboardAnimated) {
        init(activity, emojiService, isKeyboardAnimated, null);
    }

    public void init(Activity activity, EmojiService emojiService, boolean isKeyboardAnimated, @Nullable List<EmojiReactionData> emojiReactions) {
        this.activity = activity;
        this.emojiService = emojiService;
        this.isKeyboardAnimated = isKeyboardAnimated;
        this.emojiReactions = Optional.ofNullable(emojiReactions)
            .map(reactions -> reactions.stream()
                .filter(reaction -> EmojiUtil.isFullyQualifiedEmoji(reaction.emojiSequence))
                .collect(Collectors.toList()))
            .orElse(null);
        this.emojiPickerView = LayoutInflater.from(getContext()).inflate(R.layout.emoji_picker, this, true);

        initEmojiSearchWidget();

        if (isInReactionsMode()) {
            findViewById(R.id.backspace_button).setVisibility(View.GONE);
        }

        this.recentRemovePopup = new RecentEmojiRemovePopup(getContext(), this.emojiPickerView);
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

    @Override
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
        if (this.emojiDetailPopup != null && this.emojiDetailPopup.isShowing()) {
            this.emojiDetailPopup.dismiss();
        }
        if (this.recentRemovePopup != null && this.recentRemovePopup.isShowing()) {
            this.recentRemovePopup.dismiss();
        }
        setVisibility(GONE);

        for (EmojiPickerListener listener : this.emojiPickerListeners) {
            listener.onEmojiPickerClose();
        }
        this.emojiService.saveRecentEmojis();
    }

    public void onKeyboardShown() {
        if (!isKeyboardAnimated && isShown() && !emojiSearchWidget.isShown()) {
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
        if (isInReactionsMode()) {
            EditTextUtil.hideSoftKeyboard(emojiSearchWidget.searchInput);
            showEmojiPicker();
        } else {
            hide();
        }
    }

    private boolean isInReactionsMode() {
        return emojiReactions != null;
    }

    private void showEmojiSearch() {
        setLayoutParams(searchLayoutParams);
        emojiSearchWidget.show();
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

        if (isInReactionsMode()) {
            AnimationUtil.slideInAnimation(this, true, getResources().getInteger(android.R.integer.config_shortAnimTime));
        } else {
            setVisibility(VISIBLE);
        }

        for (EmojiPickerListener listener : this.emojiPickerListeners) {
            listener.onEmojiPickerOpen();
        }
    }

    private void initEmojiSearchWidget() {
        emojiSearchWidget = findViewById(R.id.emoji_search);
        emojiSearchWidget.init(activity, this, emojiService);
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
                if (!isInReactionsMode()) {
                    onRecentListLongClicked(view, emojiCodeString);
                }
            }
        };

        EmojiReactionsGridAdapter.KeyClickListener reactionsListener = null;
        if (isInReactionsMode()) {
            reactionsListener = emojiCodeString -> {
                emojiKeyListener.onEmojiClick(emojiCodeString);
                emojiService.addToRecentEmojis(emojiCodeString);
                emojiService.saveRecentEmojis();
            };
        }

        pickerHeader = findViewById(R.id.emoji_picker_header);
        viewPager = findViewById(R.id.emoji_pager);
        int currentItem = this.viewPager.getCurrentItem();

        EmojiPagerAdapter emojiPagerAdapter = new EmojiPagerAdapter(
            activity,
            this,
            emojiService,
            keyClickListener,
            reactionsListener,
            emojiReactions
        );

        this.viewPager.setAdapter(emojiPagerAdapter);
        this.viewPager.setOffscreenPageLimit(1);

        final TabLayout tabLayout = emojiPickerView.findViewById(R.id.sliding_tabs);
        tabLayout.removeAllTabs();

        for (EmojiGroup emojiGroup : EmojiManager.emojiGroups) {
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
                // nothing to do
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
                // nothing to do
            }
        });

        setInitialTab(currentItem);
        initEmojiSearchButton();
        initEmojiBackspaceButton();
    }

    private void setInitialTab(int currentItem) {
        // show first regular tab if there are no recent emojis (and no reactions)
        if (currentItem == 0
            && emojiService.hasNoRecentEmojis()
            && (emojiReactions == null || emojiReactions.isEmpty())) {
            this.viewPager.setCurrentItem(1);
        }
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
        return getContext().getString(EmojiManager.getEmojiGroupName(id)).toUpperCase();
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

    public boolean isEmojiSearchShown() {
        return emojiSearchWidget.isShown();
    }

    public interface EmojiPickerListener {
        default void onEmojiPickerOpen() {
        }

        default void onEmojiPickerClose() {
        }
    }

    public interface EmojiKeyListener {
        void onBackspaceClick();

        void onEmojiClick(String emojiCodeString);

        default void onShowPicker() {}
    }
}
