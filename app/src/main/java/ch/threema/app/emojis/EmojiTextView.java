/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

public class EmojiTextView extends AppCompatTextView {

    public EmojiTextView(Context context) {
        this(context, null);
    }

    public EmojiTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setText(@Nullable CharSequence text, BufferType type) {
        super.setText(EmojiMarkupUtil.getInstance().addTextSpans(getContext(), text, this, true, true, false, true), type);
    }

    /**
     * Set text of the EmojiTextView to one single emoji.
     * If the text contains none or more than one emoji, or the provided emojiSequence is not known to EmojiMarkupUtil,
     * it will be substituted by the Unicode replacement character
     *
     * @param emojiSequence Text to set
     * @return the text that was actually set
     */
    public CharSequence setSingleEmojiSequence(@Nullable CharSequence emojiSequence) {
        if (EmojiUtil.isFullyQualifiedEmoji(emojiSequence)) {
            CharSequence emojifiedSequence = EmojiMarkupUtil.getInstance().addTextSpans(getContext(), emojiSequence, this, true, true, false, true);
            super.setText(emojifiedSequence, BufferType.SPANNABLE);
            return getText();
        }
        super.setText(EmojiUtil.REPLACEMENT_CHARACTER, BufferType.NORMAL);
        return getText();
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        if (drawable instanceof EmojiDrawable) {
            invalidate();
        } else {
            super.invalidateDrawable(drawable);
        }
    }
}
