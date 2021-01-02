/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.utils.ConfigUtils;

public class EmojiConversationTextView extends androidx.appcompat.widget.AppCompatTextView {
	protected final EmojiMarkupUtil emojiMarkupUtil;
	private boolean isFade = false;
	private boolean ignoreMarkup = false;

	public EmojiConversationTextView(Context context) {
		this(context, null);
	}

	public EmojiConversationTextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiConversationTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		emojiMarkupUtil = EmojiMarkupUtil.getInstance();
	}

	@Override
	public void setText(@Nullable CharSequence text, BufferType type) {
		if (emojiMarkupUtil != null) {
			super.setText(emojiMarkupUtil.addTextSpans(getContext(), text, this, this.ignoreMarkup, true), type);
		} else {
			super.setText(text, type);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (isFade) {
			getPaint().clearShadowLayer();
			getPaint().setShader(
				new LinearGradient(0,
					getHeight(),
					0,
					getHeight() - (getTextSize() * 3),
					Color.TRANSPARENT,
					ConfigUtils.getColorFromAttribute(getContext(), android.R.attr.textColorPrimary),
					Shader.TileMode.CLAMP));
		}
		super.onDraw(canvas);
	}

	@Override
	public void invalidateDrawable(@NonNull Drawable drawable) {
		if (drawable instanceof EmojiDrawable) {
			invalidate();
		} else {
			super.invalidateDrawable(drawable);
		}
	}

	public void setFade(boolean isFade) {
		this.isFade = isFade;
	}

	public void setIgnoreMarkup(boolean ignoreMarkup) {
		this.ignoreMarkup = ignoreMarkup;
	}
}
