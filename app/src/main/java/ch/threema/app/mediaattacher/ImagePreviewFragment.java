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

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

public class ImagePreviewFragment extends PreviewFragment {
    private SubsamplingScaleImageView scaleImageView;
    private ImageView imageView;

    ImagePreviewFragment(MediaAttachItem mediaItem, MediaAttachViewModel mediaAttachViewModel) {
        super(mediaItem, mediaAttachViewModel);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.rootView = inflater.inflate(R.layout.fragment_image_preview, container, false);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (rootView != null) {
            this.scaleImageView = rootView.findViewById(R.id.scale_image_view);
            this.imageView = rootView.findViewById(R.id.image_view);

            if (mediaAttachItem.getType() == MediaAttachItem.TYPE_GIF || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mediaAttachItem.getType() == MediaAttachItem.TYPE_WEBP)) {
                Glide.with(this).load(mediaAttachItem.getUri())
                    .transition(withCrossFade())
                    .optionalFitCenter()
                    .error(R.drawable.ic_baseline_broken_image_200)
                    .into(imageView);
            } else {
                Glide.with(this)
                    .load(mediaAttachItem.getUri())
                    .transition(withCrossFade())
                    .optionalCenterInside()
                    .error(R.drawable.ic_baseline_broken_image_200)
                    .into(new CustomViewTarget<SubsamplingScaleImageView, Drawable>(scaleImageView) {
                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        }

                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            scaleImageView.setImage(ImageSource.bitmap(((BitmapDrawable) resource).getBitmap()));

                            scaleImageView.setVisibility(View.VISIBLE);
                            imageView.setVisibility(View.GONE);
                        }

                        @Override
                        protected void onResourceCleared(@Nullable Drawable placeholder) {
                        }
                    });
            }

        }
    }
}
