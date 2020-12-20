/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.LayoutRes;
import ch.threema.app.R;
import ch.threema.app.mediaattacher.MediaAttachItem;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.LocaleUtil;

public class VideoPopup extends DimmingPopupWindow {

	private static final Logger logger = LoggerFactory.getLogger(ImagePopup.class);

	private Context context;
	private PlayerView videoView;
	private TextView filenameTextView, dateTextView;
	private View topLayout;
	private View parentView;
	private ContentResolver contentResolver;
	private SimpleExoPlayer player;
	private int screenWidth;
	private int screenHeight;
	private int borderSize;

	final int[] location = new int[2];


	public VideoPopup(Context context, View parentView, int screenWidth, int screenHeight) {
		super(context);
		init(context, parentView, screenWidth, screenHeight, 0, 0);
	}

	public VideoPopup(Context context, View parentView, int screenWidth, int screenHeight, int innerBorder) {
		super(context);
		init(context, parentView, screenWidth, screenHeight, innerBorder, 0);
	}

	public VideoPopup(Context context, View parentView, int screenWidth, int screenHeight, int innerBorder, @LayoutRes int layout) {
		super(context);
		init(context, parentView, screenWidth, screenHeight, innerBorder, layout);
	}

	private void init(Context context, View parentView, int screenWidth, int screenHeight, int innerBorder, @LayoutRes int layout) {
		this.context = context;
		this.parentView = parentView;
		this.contentResolver = context.getContentResolver();
		this.screenHeight = screenHeight;
		this.screenWidth = screenWidth;
		this.borderSize = context.getResources().getDimensionPixelSize(R.dimen.image_popup_screen_border_width);

		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (layout == 0) {
			topLayout = layoutInflater.inflate(R.layout.popup_video, null, true);
		} else {
			topLayout = layoutInflater.inflate(layout, null, true);
		}

		this.videoView = topLayout.findViewById(R.id.video_view);
		this.filenameTextView = topLayout.findViewById(R.id.filename_view);
		this.dateTextView = topLayout.findViewById(R.id.date_view);

		setContentView(topLayout);

		if (innerBorder != 0) {
			ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) videoView.getLayoutParams();
			marginParams.setMargins(innerBorder, innerBorder, innerBorder, innerBorder);
			videoView.setLayoutParams(marginParams);

			marginParams = (ViewGroup.MarginLayoutParams) filenameTextView.getLayoutParams();
			marginParams.setMargins(innerBorder, innerBorder -
				context.getResources().getDimensionPixelSize(R.dimen.image_popup_text_size) -
				context.getResources().getDimensionPixelSize(R.dimen.image_popup_text_margin_bottom), 0, 0);
			filenameTextView.setLayoutParams(marginParams);
		}

		if (screenHeight > screenWidth) {
			// portrait
			setWidth(screenWidth - borderSize);
			setHeight(FrameLayout.LayoutParams.WRAP_CONTENT);
		} else {
			// landscape
			setWidth(FrameLayout.LayoutParams.WRAP_CONTENT);
			setHeight(screenHeight - borderSize);
		}

		setBackgroundDrawable(new BitmapDrawable());
		setAnimationStyle(0);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			setElevation(10);
		}
		setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
	}

	public void show(final View view, final MediaAttachItem mediaAttachItem) {

		this.filenameTextView.setText(mediaAttachItem.getBucketName() + "/" + mediaAttachItem.getDisplayName());
		this.dateTextView.setText(LocaleUtil.formatTimeStampString(context, mediaAttachItem.getDateTaken(), false));

		getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);

				if (mediaAttachItem != null) {
					logger.debug(("mediaAttachItem orientation is " + mediaAttachItem.getOrientation()));
					player = new SimpleExoPlayer.Builder(getContext()).build();
					videoView.setPlayer(player);
					DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, Util.getUserAgent(context, "threema"));
					MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
						.createMediaSource(mediaAttachItem.getUri());
					player.setPlayWhenReady(true);
					player.prepare(videoSource);
				}

				AnimationUtil.getViewCenter(view, getContentView(), location);
				AnimationUtil.popupAnimateIn(getContentView());
			}
		});

		showAtLocation(parentView, Gravity.CENTER, 0, 0);
		dimBackground();

		topLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
	}

	@Override
	public void dismiss() {
		AnimationUtil.popupAnimateOut(getContentView(), new Runnable() {
			@Override
			public void run() {
				VideoPopup.super.dismiss();
				if (player != null) {
					player.release();
					player = null;
				}
			}
		});
	}
}
