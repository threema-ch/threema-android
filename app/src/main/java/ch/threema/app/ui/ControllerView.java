/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.slf4j.Logger;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatImageView;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;

public class ControllerView extends MaterialCardView {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ControllerView");

	private CircularProgressIndicator progressBarIndeterminate, progressBarDeterminate;
	private AppCompatImageView icon;
	private @ColorInt int foregroundColor;
	private int status, progressMax = 100;

	public final static int STATUS_NONE = 0;
	public final static int STATUS_PROGRESSING = 1;
	public final static int STATUS_READY_TO_DOWNLOAD = 2;
	public final static int STATUS_READY_TO_PLAY = 3;
	public final static int STATUS_PLAYING = 4;
	public final static int STATUS_READY_TO_RETRY = 5;
	public final static int STATUS_PROGRESSING_NO_CANCEL = 6;
	public final static int STATUS_TRANSCODING = 8;

	public ControllerView(Context context) {
		super(context);
		init(context);
	}

	public ControllerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public ControllerView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.conversation_list_item_controller_view, this);

		this.setShapeAppearanceModel(ShapeAppearanceModel.builder(context, 0, R.style.ShapeAppearance_Material3_Corner_Medium).build());
		this.setStrokeWidth(0);
		this.foregroundColor = ConfigUtils.getColorFromAttribute(context, R.attr.colorOnBackground);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		progressBarIndeterminate = this.findViewById(R.id.progress);
		progressBarDeterminate = this.findViewById(R.id.progress_determinate);
		icon = this.findViewById(R.id.icon);

		setBackgroundImage(null);
	}

	private void reset() {
		if (getVisibility() != VISIBLE) {
			setVisibility(VISIBLE);
		}
		if (progressBarIndeterminate.getVisibility() == VISIBLE) {
			progressBarIndeterminate.setVisibility(INVISIBLE);
		}
		if (progressBarDeterminate.getVisibility() == VISIBLE) {
			progressBarDeterminate.setVisibility(INVISIBLE);
		}
		if (icon.getVisibility() != VISIBLE) {
			icon.setVisibility(VISIBLE);
		}
	}

	public void setNeutral() {
		logger.debug("setNeutral");
		reset();
		status = STATUS_NONE;
	}

	public void setHidden() {
		logger.debug("setHidden");
		setVisibility(INVISIBLE);
		status = STATUS_NONE;
	}

	@UiThread
	public void setPlay() {
		logger.debug("setPlay");
		if (status != STATUS_READY_TO_PLAY) {
			setImageResource(R.drawable.ic_play);
			setContentDescription(getContext().getString(R.string.play));
			status = STATUS_READY_TO_PLAY;
		}
	}

	@UiThread
	public void setPause() {
		logger.debug("setPause");
		setImageResource(R.drawable.ic_pause);
		setContentDescription(getContext().getString(R.string.pause));
		status = STATUS_PLAYING;
	}

	public void setTranscoding() {
		setHidden();
		status = STATUS_TRANSCODING;
	}

	public void setProgressing() {
		setProgressing(true);
	}

	@UiThread
	public void setProgressing(boolean cancelable) {
		logger.debug("setProgressing cancelable = " + cancelable);
		if (progressBarIndeterminate.getVisibility() != VISIBLE) {
			reset();
			if (cancelable) {
				if (status != STATUS_PROGRESSING) {
					setImageResource(R.drawable.ic_close);
					status = STATUS_PROGRESSING;
				}
			} else {
				if (status != STATUS_PROGRESSING_NO_CANCEL) {
					icon.setVisibility(INVISIBLE);
					status = STATUS_PROGRESSING_NO_CANCEL;
				}
			}
			progressBarIndeterminate.setVisibility(VISIBLE);
		} else {
			setVisibility(VISIBLE);
			if (cancelable) {
				status = STATUS_PROGRESSING;
			} else {
				status = STATUS_PROGRESSING_NO_CANCEL;
			}
		}
		requestLayout();
	}

	public void setProgressingDeterminate(int max) {
		logger.debug("setProgressingDeterminate max = " + max);
		setBackgroundImage(null);
		setImageResource(R.drawable.ic_close);
		setContentDescription(getContext().getString(R.string.cancel));
		progressBarDeterminate.setMax(max);
		progressBarDeterminate.setProgress(0);
		progressBarDeterminate.setVisibility(VISIBLE);
		status = STATUS_PROGRESSING;
		progressMax = max;
	}

	public void setProgress(int progress) {
		logger.debug("setProgress progress = " + progress);
		if (progressBarDeterminate.getVisibility() != VISIBLE) {
			setProgressingDeterminate(progressMax);
		}
		progressBarDeterminate.setProgress(progress);
	}

	public void setRetry() {
		logger.debug("setRetry");
		setImageResource(R.drawable.outline_refresh_24);
		setContentDescription(getContext().getString(R.string.retry));
		status = STATUS_READY_TO_RETRY;
	}

	public void setReadyToDownload() {
		logger.debug("setReadyToDownload");
		setImageResource(R.drawable.outline_file_download_24);
		setContentDescription(getContext().getString(R.string.download));
		status = STATUS_READY_TO_DOWNLOAD;
	}

	public void setImageResource(@DrawableRes int resource) {
		logger.debug("setImageResource");
		reset();
		icon.setColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN);
		icon.setScaleType(ImageView.ScaleType.CENTER);
		icon.setImageResource(resource);
	}

	public void setBackgroundImage(Bitmap bitmap) {
		logger.debug("setBackgroundImage");
		if (bitmap != null) {
			icon.clearColorFilter();
			icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
			icon.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
		}
	}

	public int getStatus() {
		return status;
	}
}
