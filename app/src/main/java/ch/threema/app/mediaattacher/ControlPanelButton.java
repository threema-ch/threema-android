/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.app.mediaattacher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.ImageViewCompat;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;

public class ControlPanelButton extends FrameLayout {
	private AppCompatImageView labelImageView;
	private TextView labelTextView;
	private ConstraintLayout container;

	public ControlPanelButton(Context context) {
		this(context, null);
	}

	public ControlPanelButton(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ControlPanelButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.button_media_attach, this);

		this.container = findViewById(R.id.container);
		this.labelImageView = findViewById(R.id.image);
		this.labelTextView = findViewById(R.id.label);

		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ControlPanelButton);
			setLabelIcon(a.getResourceId(R.styleable.ControlPanelButton_labelIcon, R.drawable.ic_image_outline));
			setLabelText(a.getResourceId(R.styleable.ControlPanelButton_labelText, R.string.name));

			@ColorInt int strokeColor = a.getColor(R.styleable.ControlPanelButton_strokeColor, Color.TRANSPARENT);
			@ColorInt int fillColor = a.getColor(R.styleable.ControlPanelButton_fillColor, ConfigUtils.getColorFromAttribute(context, R.attr.attach_button_background));

			int fillColorAlpha = a.getInt(R.styleable.ControlPanelButton_fillColorAlpha, -1);

			setFillAndStrokeColor(fillColor, strokeColor, fillColorAlpha);
			setForegroundColor(a.getColor(R.styleable.ControlPanelButton_foregroundColor, ConfigUtils.getColorFromAttribute(context, R.attr.textColorSecondary)));

			a.recycle();
		}
	}

	private void setForegroundColor(@ColorInt int color) {
		this.labelTextView.setTextColor(color);
		ImageViewCompat.setImageTintList(this.labelImageView, ColorStateList.valueOf(color));
	}

	private void setFillAndStrokeColor(@ColorInt int fillColor, @ColorInt int strokeColor, int fillColorAlpha) {
		try {
			StateListDrawable stateListDrawable = (StateListDrawable) container.getBackground();
			LayerDrawable layerDrawable = (LayerDrawable) stateListDrawable.getCurrent();

			GradientDrawable gradientDrawable = (GradientDrawable) layerDrawable.getDrawable(0);

			gradientDrawable.setColor(fillColor);
			if (fillColorAlpha >= 0) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					gradientDrawable.setAlpha(fillColorAlpha);
				} else {
					gradientDrawable.setColor(fillColor + (fillColorAlpha * 0x1000000));
				}
			}
			gradientDrawable.setStroke(getResources().getDimensionPixelSize(R.dimen.media_attach_button_stroke_width), strokeColor);

		} catch (Exception ignore) {
		}
	}

	public void setLabelIcon(@DrawableRes int labelIcon) {
		this.labelImageView.setImageResource(labelIcon);
	}

	public void setLabelText(@StringRes int labelText) {
		this.labelTextView.setText(labelText);
		setContentDescription(getContext().getString(labelText));
	}
}

