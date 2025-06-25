/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import ch.threema.app.R;
import ch.threema.app.ui.CheckableFrameLayout;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.base.utils.LoggingUtil;

public class MediaAttachAdapter extends RecyclerView.Adapter<MediaAttachAdapter.MediaGalleryHolder> {
    private static final Logger logger = LoggingUtil.getThreemaLogger("MediaAttachAdapter");

    private final Context context;
    private List<MediaAttachItem> mediaAttachItems;
    private final MediaAttachAdapter.ItemClickListener clickListener;
    private final MediaAttachViewModel mediaAttachViewModel;
    private final int columnCount;

    public MediaAttachAdapter(Context context, MediaAttachAdapter.ItemClickListener clickListener, int columnCount) {
        this.context = context;
        this.mediaAttachItems = new ArrayList<>();
        this.clickListener = clickListener;
        this.columnCount = columnCount;
        this.mediaAttachViewModel = new ViewModelProvider((MediaSelectionBaseActivity) context).get(MediaAttachViewModel.class);
    }

    public interface ItemClickListener {
        void onItemLongClick(View view, int position, MediaAttachItem mediaAttachItem);

        void onItemChecked(int count);
    }

    public static class MediaGalleryHolder extends RecyclerView.ViewHolder {
        ShapeableImageView imageView;
        FrameLayout mediaFrame;
        CheckableFrameLayout contentView;
        LinearLayout animatedFormatLabelContainer;
        ImageView animatedFormatLabelIconView;
        LinearLayout videoIndicator;
        ImageView loadErrorIndicator;
        TextView videoDuration;
        int itemId;

        public MediaGalleryHolder(@NonNull View itemView) {
            super(itemView);
            contentView = (CheckableFrameLayout) itemView;
            mediaFrame = itemView.findViewById(R.id.media_frame);
            imageView = itemView.findViewById(R.id.thumbnail_view);
            animatedFormatLabelContainer = itemView.findViewById(R.id.animated_format_label_container);
            animatedFormatLabelIconView = itemView.findViewById(R.id.animated_format_label_icon);
            videoIndicator = itemView.findViewById(R.id.video_marker_container);
            loadErrorIndicator = itemView.findViewById(R.id.load_error_indicator);
            videoDuration = itemView.findViewById(R.id.video_duration_text);
        }
    }

    @NonNull
    @Override
    public MediaAttachAdapter.MediaGalleryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.item_media_attach_gallery, parent, false);
        return new MediaAttachAdapter.MediaGalleryHolder(itemView);
    }

    @Override
    public void onViewRecycled(@NonNull MediaGalleryHolder holder) {
        super.onViewRecycled(holder);
        //cancel pending loads when item is out of view
        Glide.with(context).clear(holder.imageView);
    }

    /**
     * This method is called for every media attach item that is scrolled into view.
     */
    @Override
    public void onBindViewHolder(@NonNull MediaGalleryHolder holder, int position) {
        if (!mediaAttachItems.isEmpty()) {
            final MediaAttachItem mediaAttachItem = mediaAttachItems.get(position);
            // required item ID to check on recycling
            holder.itemId = mediaAttachItem.getId();
            CheckableFrameLayout contentView = holder.contentView;
            ShapeableImageView imageView = holder.imageView;
            LinearLayout gifIndicator = holder.animatedFormatLabelContainer;
            ImageView gifIcon = holder.animatedFormatLabelIconView;
            LinearLayout videoIndicator = holder.videoIndicator;
            ImageView loadErrorIndicator = holder.loadErrorIndicator;
            TextView videoDuration = holder.videoDuration;

            if (position == 0) {
                ShapeAppearanceModel leftShapeAppearanceModel = new ShapeAppearanceModel.Builder()
                    .setTopLeftCornerSize(context.getResources().getDimensionPixelSize(R.dimen.media_attach_button_radius))
                    .build();
                holder.imageView.setShapeAppearanceModel(leftShapeAppearanceModel);
            }

            if (position == columnCount - 1) {
                ShapeAppearanceModel rightShapeAppearanceModel = new ShapeAppearanceModel.Builder()
                    .setTopRightCornerSize(context.getResources().getDimensionPixelSize(R.dimen.media_attach_button_radius))
                    .build();
                holder.imageView.setShapeAppearanceModel(rightShapeAppearanceModel);
            }

            try {
                Glide.with(context).load(mediaAttachItem.getUri())
                    .transition(withCrossFade())
                    .centerInside()
                    .optionalCenterInside()
                    .addListener(new RequestListener<>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            logger.error("Glide Loading Exception ", e);
                            loadErrorIndicator.setVisibility(View.VISIBLE);
                            gifIndicator.setVisibility(View.GONE);
                            videoIndicator.setVisibility(View.GONE);
                            //redraw setChecked state in case holder is being recycled and reset click listeners
                            contentView.setChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().containsKey(mediaAttachItem.getId()));
                            contentView.setOnClickListener(null);
                            contentView.setOnLongClickListener(null);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                            loadErrorIndicator.setVisibility(View.GONE);
                            contentView.setOnClickListener(view -> {
                                toggleItemChecked(mediaAttachItem);
                                contentView.toggle();
                            });

                            contentView.setOnLongClickListener(view -> {
                                clickListener.onItemLongClick(view, holder.getAdapterPosition(), mediaAttachItem);
                                return true;
                            });

                            contentView.setContentDescription(context.getString(R.string.attach_picture) + ": " + mediaAttachItem.getDisplayName());

                            if (mediaAttachItem.getType() == MediaAttachItem.TYPE_GIF) {
                                gifIndicator.setVisibility(View.VISIBLE);
                                gifIcon.setImageResource(R.drawable.ic_gif_24dp);
                                contentView.setContentDescription(context.getString(R.string.attach_gif) + ": " + mediaAttachItem.getDisplayName());
                            } else if (mediaAttachItem.getType() == MediaAttachItem.TYPE_WEBP) {
                                gifIndicator.setVisibility(View.VISIBLE);
                                gifIcon.setImageResource(R.drawable.ic_webp);
                                contentView.setContentDescription("WebP: " + mediaAttachItem.getDisplayName());
                            } else {
                                gifIndicator.setVisibility(View.GONE);
                            }

                            if (mediaAttachItem.getType() == MediaAttachItem.TYPE_VIDEO) {
                                videoDuration.setText(StringConversionUtil.getDurationString(mediaAttachItem.getDuration()));
                                videoIndicator.setVisibility(View.VISIBLE);
                                contentView.setContentDescription(context.getString(R.string.attach_video) + ": " + mediaAttachItem.getDisplayName());
                            } else {
                                videoIndicator.setVisibility(View.GONE);
                            }

                            contentView.setChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().containsKey(mediaAttachItem.getId()));

                            return false;
                        }
                    })
                    .into(imageView);

            } catch (RejectedExecutionException e) {
                logger.error("thumbnail task failed " + e);
            }
        }
    }

    @Override
    public int getItemCount() {
        return mediaAttachItems.size();
    }

    public List<MediaAttachItem> getMediaAttachItems() {
        return mediaAttachItems;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    public void clearSelection() {
        HashMap<Integer, MediaAttachItem> selectedMediaAttachItems = mediaAttachViewModel.getSelectedMediaItemsHashMap();
        List<Integer> changedItems = new ArrayList<>();

        for (int i = 0; i < mediaAttachItems.size(); i++) {
            if (selectedMediaAttachItems.containsKey(mediaAttachItems.get(i).getId())) {
                changedItems.add(i);
            }
        }

        mediaAttachViewModel.clearSelection();

        for (Integer changedItem : changedItems) {
            notifyItemChanged(changedItem);
        }
    }

    public void setMediaAttachItems(List<MediaAttachItem> items) {
        mediaAttachItems = items;
        notifyDataSetChanged();
    }

    private void toggleItemChecked(MediaAttachItem mediaAttachItem) {
        if (mediaAttachViewModel.getSelectedMediaItemsHashMap().containsKey(mediaAttachItem.getId())) {
            mediaAttachViewModel.removeSelectedMediaItem(mediaAttachItem.getId());
        } else {
            mediaAttachViewModel.addSelectedMediaItem(mediaAttachItem.getId(), mediaAttachItem);
        }
        clickListener.onItemChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().size());
    }
}
