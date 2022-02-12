/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.services.FileService;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.SquareImageView;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;

import static ch.threema.storage.models.data.MessageContentsType.AUDIO;
import static ch.threema.storage.models.data.MessageContentsType.FILE;
import static ch.threema.storage.models.data.MessageContentsType.GIF;
import static ch.threema.storage.models.data.MessageContentsType.IMAGE;
import static ch.threema.storage.models.data.MessageContentsType.VIDEO;
import static ch.threema.storage.models.data.MessageContentsType.VOICE_MESSAGE;

public class MediaGalleryAdapter extends ArrayAdapter<AbstractMessageModel> {
	private static final Logger logger = LoggerFactory.getLogger(MediaGalleryAdapter.class);

	private final List<AbstractMessageModel> values;
	private final FileService fileService;
	private final ThumbnailCache thumbnailCache;
	private final LayoutInflater layoutInflater;
	private final List<Integer> brokenThumbnails = new ArrayList<Integer>();
	@ColorInt private final int foregroundColor;

	public static final int TYPE_NONE = 0;
	public static final int TYPE_IMAGE = 1;
	public static final int TYPE_VIDEO = 2;
	public static final int TYPE_AUDIO = 3;
	public static final int TYPE_FILE = 4;
	public static final int TYPE_MAX_COUNT = TYPE_FILE + 1;

	public MediaGalleryAdapter(
			Context context,
			List<AbstractMessageModel> values,
			FileService fileService,
			ThumbnailCache thumbnailCache) {
		super(context, R.layout.item_media_gallery, values);

		this.values = values;
		this.fileService = fileService;
		this.thumbnailCache = thumbnailCache;
		this.layoutInflater = LayoutInflater.from(context);
		this.foregroundColor = ConfigUtils.getColorFromAttribute(context, R.attr.textColorSecondary);
	}

	private static class MediaGalleryHolder extends AbstractListItemHolder {
		public ImageView imageView;
		public ControllerView playButton;
		public ProgressBar progressBar;
		public TextView topTextView;
		public View textContainerView;
		public int messageId;
	}

	@Override
	public int getItemViewType(int position) {
		final AbstractMessageModel m = this.getItem(position);
		return this.getType(m);
	}

	private int getType(AbstractMessageModel m) {
		if (m != null) {
			if (!m.isStatusMessage()) {
				switch (m.getMessageContentsType()) {
					case IMAGE:
						return TYPE_IMAGE;
					case GIF:
					case VIDEO:
						return TYPE_VIDEO;
					case AUDIO:
					case VOICE_MESSAGE:
						return TYPE_AUDIO;
					case FILE:
						return TYPE_FILE;
				}
			}
		}
		return TYPE_NONE;
	}

	@Override
	public int getViewTypeCount() {
		return TYPE_MAX_COUNT;
	}

	private Bitmap getBitmap(AbstractMessageModel messageModel) {
		Bitmap thumbnail;

		try {
			thumbnail = fileService.getMessageThumbnailBitmap(messageModel, thumbnailCache);
		} catch (Exception e) {
			logger.error("Exception", e);
			thumbnail = null;
		}

		return thumbnail;
	}

	@SuppressLint("StaticFieldLeak")
	private void loadThumbnailBitmap(final int position, MediaGalleryHolder holder, final AbstractMessageModel messageModel) {
		//do nothing!
		holder.imageView.setImageBitmap(null);
		synchronized (brokenThumbnails) {
			if(this.brokenThumbnails.contains(messageModel.getId())) {
				return;
			}
		}

		//load new one by async task
		try {
			new AsyncTask<MediaGalleryHolder, Void, Bitmap>() {
				private MediaGalleryHolder holder;

				@Override
				protected Bitmap doInBackground(MediaGalleryHolder... params) {
					this.holder = params[0];


					if (position != holder.position) {
						cancel(true);
						return null;
					}
					return MediaGalleryAdapter.this.getBitmap(messageModel);
				}

				@Override
				protected void onPostExecute(Bitmap thumbnail) {
					if (position == holder.position) {
						if (holder.imageView != null) {
							boolean broken = false;

							if (thumbnail != null && !thumbnail.isRecycled()) {
								holder.textContainerView.setVisibility(View.GONE);
								holder.imageView.setImageBitmap(thumbnail);
								holder.imageView.clearColorFilter();
								holder.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
							} else {
								if (messageModel.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE) {
									holder.imageView.setScaleType(ImageView.ScaleType.CENTER);
									holder.imageView.setImageResource(R.drawable.ic_keyboard_voice_outline);
									holder.imageView.setColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN);
									holder.topTextView.setText(StringConversionUtil.secondsToString(
										messageModel.getType() == MessageType.FILE ?
											messageModel.getFileData().getDurationSeconds():
											messageModel.getAudioData().getDuration(), false));
									holder.textContainerView.setVisibility(View.VISIBLE);
								} else if (messageModel.getType() == MessageType.FILE) {
									// try default avatar for mime type
									thumbnail = fileService.getDefaultMessageThumbnailBitmap(getContext(), messageModel, null, messageModel.getFileData().getMimeType());
									holder.topTextView.setText(messageModel.getFileData().getFileName());
									holder.textContainerView.setVisibility(View.VISIBLE);
									if (thumbnail != null) {
										holder.imageView.setScaleType(ImageView.ScaleType.CENTER);
										holder.imageView.setImageBitmap(thumbnail);
										holder.imageView.setColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN);
									} else {
										broken = true;
									}
								} else {
									holder.textContainerView.setVisibility(View.GONE);
									broken = true;
								}
							}
							updateBrokenThumbnailFlags(messageModel.getId(), broken);
						}
					}
				}
			}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, holder);
		}

		catch (RejectedExecutionException x) {
			//thread pool is full load by non thread
			Bitmap thumbnail = this.getBitmap(messageModel);

			if (thumbnail != null && !thumbnail.isRecycled()) {
				holder.imageView.setImageBitmap(thumbnail);
			}
		}
	}

	private void updateBrokenThumbnailFlags(Integer id, boolean broken) {
		synchronized (brokenThumbnails) {
			if(broken) {
				this.brokenThumbnails.add(id);
			}
			else {
				this.brokenThumbnails.remove(id);
			}
		}
	}

	@NonNull
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View itemView = convertView;
		MediaGalleryHolder holder;

		if (convertView == null) {
			holder = new MediaGalleryHolder();

			// This a new view we inflate the new layout
			itemView = layoutInflater.inflate(R.layout.item_media_gallery, parent, false);

			SquareImageView imageView = itemView.findViewById(R.id.image_view);
			ControllerView playButton = itemView.findViewById(R.id.play_button);
			ProgressBar progressBar = itemView.findViewById(R.id.progress_decoding);
			TextView topTextView = itemView.findViewById(R.id.text_filename);
			View textContainerView = itemView.findViewById(R.id.filename_container);

			holder.imageView = imageView;
			holder.playButton = playButton;
			holder.progressBar = progressBar;
			holder.topTextView = topTextView;
			holder.textContainerView = textContainerView;
			holder.messageId = 0;

			itemView.setTag(holder);
		} else {
			holder = (MediaGalleryHolder) itemView.getTag();
		}

		final AbstractMessageModel messageModel = values.get(position);

		holder.position = position;

		if (holder.messageId != messageModel.getId()) {
			// do not load contents again if it's unchanged
			this.loadThumbnailBitmap(position, holder, messageModel);
			if (this.brokenThumbnails.contains(messageModel.getId())) {
				holder.playButton.setBroken();
				holder.playButton.setVisibility(View.VISIBLE);
			} else {
				if (this.getType(messageModel) == TYPE_VIDEO || (this.getType(messageModel) == TYPE_FILE && FileUtil.isVideoFile(messageModel.getFileData()))) {
					holder.playButton.setPlay();
					holder.playButton.setVisibility(View.VISIBLE);
				} else {
					holder.playButton.setHidden();
				}
			}
			holder.progressBar.setVisibility(View.GONE);
		}
		holder.messageId = messageModel.getId();

		return itemView;
	}
}
