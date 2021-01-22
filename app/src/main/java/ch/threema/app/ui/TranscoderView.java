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

package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.MessageService;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;

public class TranscoderView extends FrameLayout {
	private static final Logger logger = LoggerFactory.getLogger(TranscoderView.class);

	public static final int PROGRESS_MAX = 100;

	private ProgressBar transcodeProgress;
	private AbstractMessageModel messageModel;
	private MessageService messageService;

	public TranscoderView(Context context) {
		super(context);
		init(context);
	}

	public TranscoderView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public TranscoderView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.conversation_list_item_transcoder_view, this);

		try {
			messageService = ThreemaApplication.getServiceManager().getMessageService();
		} catch (ThreemaException e) {
			logger.debug("Unable to get MessageService", e);
		}
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		transcodeProgress = this.findViewById(R.id.transcode_progress);
		this.findViewById(R.id.cancel_button).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (messageModel != null && messageService != null) {
					messageService.cancelVideoTranscoding(messageModel);
				}
			}
		});

		transcodeProgress.setMax(PROGRESS_MAX);
		transcodeProgress.setProgress(0);
		transcodeProgress.setIndeterminate(true);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		final View parent = ((View) getParent());
		final ViewTreeObserver observer = parent.getViewTreeObserver();
		observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				ImageView imageView = parent.findViewById(R.id.attachment_image_view);
				if (imageView != null) {
					getLayoutParams().height = imageView.getMeasuredHeight();
					getLayoutParams().width = imageView.getMeasuredWidth();
					requestLayout();
				}
				ViewTreeObserver obs = parent.getViewTreeObserver();
				obs.removeOnGlobalLayoutListener(this);
			}
		});
	}

	public void setProgress(int progress) {
		if (progress > PROGRESS_MAX) {
			progress = PROGRESS_MAX;
		}

		if (progress > 0) {
			transcodeProgress.setIndeterminate(false);
			transcodeProgress.setProgress(progress);
		} else {
			transcodeProgress.setIndeterminate(true);
		}
	}

	public void setMessageModel(AbstractMessageModel messageModel) {
		this.messageModel = messageModel;
	}
}
