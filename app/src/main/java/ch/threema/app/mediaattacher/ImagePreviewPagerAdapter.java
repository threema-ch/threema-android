/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.MediaViewerActivity;

public class ImagePreviewPagerAdapter extends FragmentStateAdapter {
    private final MediaAttachViewModel mediaAttachViewModel;
    private List<MediaAttachItem> mediaAttachItems = new ArrayList<>();

    public ImagePreviewPagerAdapter(FragmentActivity mediaSelectionBaseActivity) {
        super(mediaSelectionBaseActivity);

        this.mediaAttachViewModel = new ViewModelProvider(mediaSelectionBaseActivity).get(MediaAttachViewModel.class);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        MediaAttachItem mediaAttachItem = getItem(position);
        if (mediaAttachItem != null) {
            int mediaType = mediaAttachItem.getType();
            Bundle args = new Bundle();
            args.putBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, true);

            PreviewFragment fragment = null;
            if (mediaType == MediaAttachItem.TYPE_IMAGE || mediaType == MediaAttachItem.TYPE_GIF || mediaType == MediaAttachItem.TYPE_WEBP) {
                fragment = new ImagePreviewFragment(mediaAttachItem, mediaAttachViewModel);
            } else if (mediaType == MediaAttachItem.TYPE_VIDEO) {
                fragment = new VideoPreviewFragment(mediaAttachItem, mediaAttachViewModel);
            }

            if (fragment != null) {
                fragment.setArguments(args);
                return fragment;
            } else {
                Toast.makeText(ThreemaApplication.getAppContext(), "Unrecognized Preview Format", Toast.LENGTH_SHORT).show();
            }
        }
        return new DummyPreviewFragment(mediaAttachItem, mediaAttachViewModel);
    }

    public void setMediaItems(List<MediaAttachItem> items) {
        mediaAttachItems = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mediaAttachItems.size();
    }

    @Nullable
    public MediaAttachItem getItem(int position) {
        if (position < mediaAttachItems.size() && position >= 0) {
            return mediaAttachItems.get(position);
        }
        return null;
    }
}
