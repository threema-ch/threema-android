/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

package ch.threema.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;

import static ch.threema.app.emojis.EmojiMarkupUtil.MENTION_INDICATOR;

public class MentionSpan extends ReplacementSpan {

	// ReplacementSpan does not support Spans that span multiple lines in a TextView!
	// Hack: Limit label length to 16 chars
	private static final int LABEL_MAX_LENGTH = 16;
	private ContactService contactService;
	private UserService userService;
	private int width = 0;
	private final Paint backgroundPaint;
	private final Paint invertedPaint;
	@ColorInt private final int textColor;
	@ColorInt private final int invertedTextColor;
	private static final int padding = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.mention_padding);
	private static final int radius = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.mention_radius);

	public MentionSpan(@ColorInt int backgroundColor, @ColorInt int invertedColor, @ColorInt int textColor, @ColorInt int invertedTextColor) {
		super();

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		try {
			contactService = serviceManager.getContactService();
			userService = serviceManager.getUserService();
		} catch (Exception e) {
			//
		}

		backgroundPaint = new Paint();
		backgroundPaint.setStyle(Paint.Style.FILL);
		backgroundPaint.setColor(backgroundColor);

		invertedPaint = new Paint();
		invertedPaint.setStyle(Paint.Style.FILL);
		invertedPaint.setColor(invertedColor);

		this.textColor = textColor;
		this.invertedTextColor = invertedTextColor;
	}

	private String getMentionLabelText(CharSequence text, int start, int end) {
		final String identity = text.subSequence(start + 2, end - 1).toString();

		String label = NameUtil.getQuoteName(identity, this.contactService, this.userService);
		if (label != null && label.length() > LABEL_MAX_LENGTH) {
			label = label.substring(0, LABEL_MAX_LENGTH).trim() + "…";
		}
		return label;
	}

	@Override
	public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
		if (!TestUtil.empty(text) && end - start == 11) {
			String labelText = getMentionLabelText(text, start, end);
			if (!TestUtil.empty(labelText)) {
				width = (int) paint.measureText(MENTION_INDICATOR + labelText) + (padding * 2);
				return width;
			}
		}
		return 0;
	}

	@Override
	public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
		if (width != 0 && !TestUtil.empty(text) && end - start == 11) {
			int alpha = paint.getAlpha();
			String identity = text.subSequence(start + 2, end - 1).toString();

			if (identity.equals(ContactService.ALL_USERS_PLACEHOLDER_ID) || identity.equals(userService.getIdentity())) {
				canvas.drawRoundRect(new RectF(x, top + 1, x + width, bottom), radius, radius, invertedPaint);
				paint.setColor(this.invertedTextColor);
				paint.setAlpha(0x78);
			} else {
				canvas.drawRoundRect(new RectF(x, top + 1, x + width, bottom), radius, radius, backgroundPaint);
				paint.setColor(this.textColor);
				paint.setAlpha(0x50);
			}
			canvas.drawText(MENTION_INDICATOR, x + padding, y, paint);
			paint.setAlpha(0xFF);
			canvas.drawText(getMentionLabelText(text, start, end), x + padding + paint.measureText(MENTION_INDICATOR), y, paint);
			paint.setAlpha(alpha);
		}
	}
}
