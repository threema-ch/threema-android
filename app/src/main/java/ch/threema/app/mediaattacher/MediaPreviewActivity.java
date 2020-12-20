/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.ui.MediaItem;

/**
 * The full-screen media preview that's shown when long-pressing on an item in the media attacher.
 */
public class MediaPreviewActivity extends FragmentActivity {
	public static String EXTRA_PARCELABLE_MEDIA_ITEM = "media_item";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_media_preview);

		Intent intent = getIntent();
		MediaAttachItem mediaAttachItem = intent.getParcelableExtra(EXTRA_PARCELABLE_MEDIA_ITEM);

		if (mediaAttachItem != null) {
			int mimeType = mediaAttachItem.getType();
			Bundle args = new Bundle();
			args.putBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, true);

			Fragment fragment = null;
			if (mimeType == MediaItem.TYPE_IMAGE || mimeType == MediaItem.TYPE_GIF) {
				fragment = new ImagePreviewFragment(mediaAttachItem);
			} else if (mimeType == MediaItem.TYPE_VIDEO) {
				fragment = new VideoPreviewFragment(mediaAttachItem);
			}

			if (fragment != null) {
				fragment.setArguments(args);
				getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, fragment).commit();
			} else {
				Toast.makeText(ThreemaApplication.getAppContext(), "Unrecognized Preview Format", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(R.anim.medium_fade_in, R.anim.medium_fade_out);
	}
}
