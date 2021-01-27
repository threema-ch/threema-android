/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import ch.threema.app.R;
import ch.threema.app.activities.SendMediaActivity;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.draggablegrid.BaseDynamicGridAdapter;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.StringConversionUtil;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

public class SendMediaGridAdapter extends BaseDynamicGridAdapter {
	private static final Logger logger = LoggerFactory.getLogger(SendMediaGridAdapter.class);

	private final List<MediaItem> items;
	private final Context context;
	private final LayoutInflater layoutInflater;
	private final int itemWidth;
	private final ClickListener clickListener;

	public static final int VIEW_TYPE_NORMAL = 0;
	public static final int VIEW_TYPE_ADD = 1;

	public SendMediaGridAdapter(
			Context context,
			List<MediaItem> items,
			int itemWidth,
			ClickListener clickListener) {

		super(context, items, context.getResources().getInteger(R.integer.gridview_num_columns));

		this.context = context;
		this.items = items;
		this.itemWidth = itemWidth;
		this.layoutInflater = LayoutInflater.from(context);
		this.clickListener = clickListener;
	}

	public static class SendMediaHolder extends AbstractListItemHolder {
		public ImageView imageView, deleteView, brokenView;
		public LinearLayout qualifierView;
		public int itemType;
	}

	@Override
	public int getItemViewType(int position) {
		return position == items.size() ? VIEW_TYPE_ADD : VIEW_TYPE_NORMAL;
	}

	@Override
	public int getCount() {
		return Math.min(items.size() + 1, SendMediaActivity.MAX_SELECTABLE_IMAGES);
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View itemView;
		int itemType = getItemViewType(position);
		SendMediaHolder holder = new SendMediaHolder();

		if (itemType == VIEW_TYPE_ADD) {
			itemView = layoutInflater.inflate(R.layout.item_send_media_add, parent, false);
		} else {
			itemView = layoutInflater.inflate(R.layout.item_send_media, parent, false);
		}

		AbsListView.LayoutParams params = new AbsListView.LayoutParams(this.itemWidth, this.itemWidth);
		itemView.setLayoutParams(params);

		holder.imageView = itemView.findViewById(R.id.image_view);
		holder.qualifierView = itemView.findViewById(R.id.qualifier_view);
		holder.deleteView = itemView.findViewById(R.id.delete_view);
		holder.brokenView = itemView.findViewById(R.id.broken_view);
		holder.position = position;
		holder.itemType = itemType;

		itemView.setTag(holder);

		if (itemType == VIEW_TYPE_NORMAL) {
			final MediaItem item = items.get(position);

			holder.deleteView.setOnClickListener(v -> clickListener.onDeleteKeyClicked(item));
			holder.brokenView.setVisibility(View.GONE);

			Glide.with(context).load(item.getUri())
				.transition(withCrossFade())
				.addListener(new RequestListener<Drawable>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
						holder.brokenView.setVisibility(View.VISIBLE);
						return false;
					}

					@Override
					public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
						if (item.getType() == MediaItem.TYPE_VIDEO_CAM || item.getType() == MediaItem.TYPE_VIDEO) {
							holder.qualifierView.setVisibility(View.VISIBLE);

							AppCompatImageView imageView = holder.qualifierView.findViewById(R.id.video_icon);
							imageView.setImageResource(R.drawable.ic_videocam_black_24dp);

							TextView durationView = holder.qualifierView.findViewById(R.id.video_duration_text);
							if (item.getDurationMs() > 0) {
								durationView.setText(StringConversionUtil.getDurationString(item.getDurationMs()));
								durationView.setVisibility(View.VISIBLE);
							} else {
								durationView.setVisibility(View.GONE);
							}
						} else if (item.getType() == MediaItem.TYPE_GIF) {
							holder.qualifierView.setVisibility(View.VISIBLE);

							AppCompatImageView imageView = holder.qualifierView.findViewById(R.id.video_icon);
							imageView.setImageResource(R.drawable.ic_gif_24dp);

							holder.qualifierView.findViewById(R.id.video_duration_text).setVisibility(View.GONE);
						} else {
							holder.qualifierView.setVisibility(View.GONE);
						}
						return false;
					}
				})
				.into(holder.imageView);

			rotateAndFlipImageView(holder.imageView, item);
		}
		return itemView;
	}

	private void rotateAndFlipImageView(ImageView imageView, MediaItem item) {
		imageView.setRotation(item.getRotation());

		if (item.getFlip() == BitmapUtil.FLIP_NONE) {
			imageView.setScaleY(1);
			imageView.setScaleX(1);
		}

		if ((item.getFlip() & BitmapUtil.FLIP_HORIZONTAL) == BitmapUtil.FLIP_HORIZONTAL) {
			imageView.setScaleX(-1);
		}
		if ((item.getFlip() & BitmapUtil.FLIP_VERTICAL) == BitmapUtil.FLIP_VERTICAL) {
			imageView.setScaleY(-1);
		}
	}

	@Override
	public void reorderItems(int originalPosition, int newPosition) {
		if (newPosition < items.size()) {
			super.reorderItems(originalPosition, newPosition);
		}
	}

	public interface ClickListener {
		void onDeleteKeyClicked(MediaItem item);
	}
}
