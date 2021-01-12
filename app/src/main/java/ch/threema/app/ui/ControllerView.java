/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatImageView;
import ch.threema.app.R;

public class ControllerView extends FrameLayout {
	private static final Logger logger = LoggerFactory.getLogger(ControllerView.class);

	private ProgressBar progressBarIndeterminate;
	private CircularProgressBar progressBarDeterminate;
	private AppCompatImageView icon;
	private int status, progressMax = 100;

	public final static int STATUS_NONE = 0;
	public final static int STATUS_PROGRESSING = 1;
	public final static int STATUS_READY_TO_DOWNLOAD = 2;
	public final static int STATUS_READY_TO_PLAY = 3;
	public final static int STATUS_PLAYING = 4;
	public final static int STATUS_READY_TO_RETRY = 5;
	public final static int STATUS_PROGRESSING_NO_CANCEL = 6;
	public final static int STATUS_BROKEN = 7;
	public final static int STATUS_TRANSCODING = 8;

	private OnVisibilityChangedListener visibilityChangedListener;

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
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		progressBarIndeterminate = this.findViewById(R.id.progress);
		progressBarDeterminate = this.findViewById(R.id.progress_determinate);
		icon = this.findViewById(R.id.icon);

		setBackgroundImage(null);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int padding = getMeasuredWidth() / 6;

		icon.setPadding(padding, padding, padding, padding);
/*
		int size = getResources().getDimensionPixelSize(R.dimen.avatar_size_small);
		setMeasuredDimension(size, size);
*/
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
		icon.setVisibility(INVISIBLE);
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
	public void setBroken() {
		logger.debug("setBroken");
		if (status != STATUS_BROKEN) {
			setImageResource(R.drawable.ic_close);
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
		setImageResource(R.drawable.ic_refresh_white_36dp);
		setContentDescription(getContext().getString(R.string.retry));
		status = STATUS_READY_TO_RETRY;
	}

	public void setReadyToDownload() {
		logger.debug("setReadyToDownload");
		setImageResource(R.drawable.ic_file_download_white_36dp);
		setContentDescription(getContext().getString(R.string.download));
		status = STATUS_READY_TO_DOWNLOAD;
	}

	public void setImageResource(@DrawableRes int resource) {
		logger.debug("setImageResource");
		reset();
		icon.setImageResource(resource);
		icon.setColorFilter(Color.WHITE);
		icon.requestLayout();
	}

	public void setBackgroundImage(Bitmap bitmap) {
		logger.debug("setBackgroundImage");
		if (bitmap == null) {
			setBackgroundResource(R.drawable.circle_status_icon_color);
		} else {
			setBackground(new BitmapDrawable(getResources(), bitmap));
		}
	}

	public int getStatus() {
		return status;
	}

	@Override
	public void setVisibility(int visibility) {
		super.setVisibility(visibility);
	}

	protected void onVisibilityChanged(@NonNull View view, int visibility) {
		super.onVisibilityChanged(view, visibility);

		if (visibilityChangedListener != null) {
			visibilityChangedListener.visibilityChanged(visibility);
		}
	}

	public void setVisibilityListener(OnVisibilityChangedListener listener) {
		this.visibilityChangedListener = listener;
	}

	public interface OnVisibilityChangedListener {
		void visibilityChanged(int visibility);
	}
}
