/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.util.Consumer;
import ch.threema.app.R;
import ch.threema.app.ui.ThumbDatePopupBackground;

public class RecyclerViewUtil {
    /**
     * Style for a popup displayed next to a thumbscroller
     */
    public static final Consumer<TextView> thumbScrollerPopupStyle = popupView -> {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
            popupView.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.setMarginEnd(20);
        popupView.setLayoutParams(layoutParams);
        popupView.setBackground(new ThumbDatePopupBackground(popupView.getContext()));
        popupView.setElevation(6);
        popupView.setEllipsize(TextUtils.TruncateAt.END);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(true);
        popupView.setTextColor(ConfigUtils.getColorFromAttribute(popupView.getContext(), R.attr.colorOnSecondaryContainer));
        popupView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    };
}
