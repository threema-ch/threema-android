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

package ch.threema.app.emojis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import ch.threema.app.R;
import ch.threema.app.emojireactions.EmojiReactionsGridAdapter;
import ch.threema.data.models.EmojiReactionData;

public class EmojiPagerAdapter extends PagerAdapter {

    private final Context context;
    private final EmojiGridAdapter.KeyClickListener listener;
    private final EmojiReactionsGridAdapter.KeyClickListener reactionsListener;
    private final EmojiPicker emojiPicker;
    private final EmojiService emojiService;
    private final LayoutInflater layoutInflater;
    private final List<EmojiReactionData> emojiReactions;

    EmojiPagerAdapter(
        Context context,
        EmojiPicker emojiPicker,
        EmojiService emojiService,
        EmojiGridAdapter.KeyClickListener listener,
        EmojiReactionsGridAdapter.KeyClickListener reactionsListener,
        List<EmojiReactionData> emojiReactions
    ) {
        this.context = context;
        this.listener = listener;
        this.reactionsListener = reactionsListener;
        this.emojiPicker = emojiPicker;
        this.emojiService = emojiService;
        this.emojiReactions = emojiReactions;
        this.layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return emojiPicker.getNumberOfPages();
    }

    @NonNull
    @SuppressLint("StaticFieldLeak")
    @Override
    public Object instantiateItem(@NonNull final ViewGroup container, final int position) {
        final View layout;
        final GridView recentsGridView;

        if (emojiReactions != null && position == 0) {
            layout = layoutInflater.inflate(R.layout.emoji_reactions_picker_gridview, null);
            recentsGridView = layout.findViewById(R.id.emoji_gridview);
        } else {
            layout = layoutInflater.inflate(R.layout.emoji_picker_gridview, null);
            recentsGridView = (GridView) layout;
        }

        new AsyncTask<Void, Void, EmojiGridAdapter>() {
            @Override
            protected EmojiGridAdapter doInBackground(Void... params) {
                return new EmojiGridAdapter(
                    context,
                    position,
                    emojiService,
                    listener);
            }

            @Override
            protected void onPostExecute(EmojiGridAdapter adapter) {
                container.addView(layout);
                recentsGridView.setAdapter(adapter);
            }
        }.execute();

        // tag this view for efficient refreshing
        recentsGridView.setTag(Integer.toString(position));
        recentsGridView.setOnItemClickListener((adapterView, view, i, l) -> {
            // this listener is used for hardware keyboards only.
            EmojiInfo item = (EmojiInfo) adapterView.getAdapter().getItem(i);
            listener.onEmojiKeyClicked(item.emojiSequence);
        });

        if (position == 0) {
            setupEmojiReactions(layout, emojiReactions);
        }

        return layout;
    }

    @SuppressLint("StaticFieldLeak")
    private void setupEmojiReactions(@NonNull View layout, @Nullable List<EmojiReactionData> emojiReactions) {
        if (emojiReactions == null) {
            return;
        }

        GridView reactionsGridView = layout.findViewById(R.id.reactions_gridview);
        TextView reactionsTitle = layout.findViewById(R.id.reactions_title);
        TextView recentsTitle = layout.findViewById(R.id.recents_title);

        if (emojiReactions.isEmpty()) {
            reactionsTitle.setVisibility(View.GONE);
            recentsTitle.setVisibility(View.GONE);
            reactionsGridView.setVisibility(View.GONE);
        } else {
            emojiService.syncRecentEmojis();
            recentsTitle.setVisibility(emojiService.hasNoRecentEmojis() ? View.GONE : View.VISIBLE);
            new AsyncTask<Void, Void, EmojiReactionsGridAdapter>() {
                @Override
                protected EmojiReactionsGridAdapter doInBackground(Void... params) {
                    return new EmojiReactionsGridAdapter(
                        context,
                        emojiReactions,
                        reactionsListener);
                }

                @Override
                protected void onPostExecute(EmojiReactionsGridAdapter adapter) {
                    reactionsGridView.setAdapter(adapter);
                }
            }.execute();
        }
    }

    @Override
    public void destroyItem(ViewGroup container, int position, @NonNull Object view) {
        container.removeView((View) view);
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return emojiPicker.getGroupTitle(position);
    }
}
