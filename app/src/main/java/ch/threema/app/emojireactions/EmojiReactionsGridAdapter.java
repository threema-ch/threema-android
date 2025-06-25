/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.emojireactions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import androidx.annotation.ColorInt;
import androidx.core.content.res.ResourcesCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiItemView;
import ch.threema.app.emojis.EmojiManager;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.LongToast;
import ch.threema.data.models.EmojiReactionData;

public class EmojiReactionsGridAdapter extends BaseAdapter {
    private final int emojiItemSize;
    private final int emojiItemPaddingSize;
    @ColorInt
    private final int diverseHintColor;
    private final List<ReactionEntry> emojis;
    final Context context;

    private final KeyClickListener keyClickListener;

    public EmojiReactionsGridAdapter(Context context,
                                     List<EmojiReactionData> emojiReactions,
                                     KeyClickListener listener) {
        this.context = context;
        this.keyClickListener = listener;
        this.diverseHintColor = context.getResources().getColor(R.color.emoji_picker_hint);
        if (EmojiManager.getInstance(context).spritemapInSampleSize == 1) {
            this.emojiItemSize = context.getResources().getDimensionPixelSize(R.dimen.emoji_picker_item_size);
            this.emojiItemPaddingSize = (emojiItemSize - context.getResources().getDimensionPixelSize(R.dimen.emoji_picker_emoji_size)) / 2;
        } else {
            this.emojiItemSize = 44;
            this.emojiItemPaddingSize = (emojiItemSize - 32) / 2;
        }
        this.emojis = new ArrayList<>();
        UserService userService = ThreemaApplication.requireServiceManager().getUserService();

        for (EmojiReactionData reaction : emojiReactions) {
            boolean isSender = reaction.senderIdentity.equals(userService.getIdentity());
            Optional<ReactionEntry> existing = this.emojis.stream().filter(e -> e.emojiSequence.equals(reaction.emojiSequence)).findFirst();

            if (existing.isPresent()) {
                existing.get().isSender = existing.get().isSender || isSender;
            } else {
                this.emojis.add(new ReactionEntry(reaction.emojiSequence, isSender));
            }
        }
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ReactionEntry item = getItem(position);

        final EmojiItemView view;
        if (convertView instanceof EmojiItemView) {
            view = (EmojiItemView) convertView;
        } else {
            final EmojiItemView emojiItemView = new EmojiItemView(context);
            Drawable background = ResourcesCompat.getDrawable(context.getResources(), R.drawable.selector_emoji_reactions_grid_item, null);
            emojiItemView.setBackground(background);
            emojiItemView.setPadding(emojiItemPaddingSize, emojiItemPaddingSize, emojiItemPaddingSize, emojiItemPaddingSize);
            emojiItemView.setLayoutParams(new AbsListView.LayoutParams(emojiItemSize, emojiItemSize));
            view = emojiItemView;
        }

        if (view.setEmoji(item.emojiSequence, false, diverseHintColor) != null) {
            view.setContentDescription(item.emojiSequence);
            view.setOnClickListener(v -> keyClickListener.onEmojiReactionClicked(item.emojiSequence));
            view.post(() -> view.setSelected(item.isSender));
        } else {
            view.setOnClickListener(v -> LongToast.makeText(context, R.string.reaction_cannot_be_displayed, Toast.LENGTH_LONG).show());
        }

        return view;
    }

    @Override
    public int getCount() {
        return this.emojis.size();
    }

    @Override
    public ReactionEntry getItem(int position) {
        return this.emojis.get(position);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public interface KeyClickListener {
        void onEmojiReactionClicked(String emojiCodeString);
    }

    public static class ReactionEntry {
        final String emojiSequence;
        boolean isSender;

        public ReactionEntry(String emojiSequence, boolean isSender) {
            this.emojiSequence = emojiSequence;
            this.isSender = isSender;
        }
    }
}
