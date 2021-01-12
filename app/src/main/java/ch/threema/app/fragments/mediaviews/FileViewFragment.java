/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.fragments.mediaviews;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.TestUtil;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class FileViewFragment extends MediaViewFragment {
	private WeakReference<GifImageView> imageViewRef;
	private WeakReference<ImageView> previewViewRef;
	private WeakReference<TextView> filenameViewRef;
	private boolean uiVisibilityStatus = false;

	public FileViewFragment() { super(); }

	@Override
	protected int getFragmentResourceId() {
		return R.layout.fragment_media_viewer_file;
	}

	@Override
	public boolean inquireClose() {
		return true;
	}

	@Override
	protected void showThumbnail(Bitmap thumbnail, boolean isGeneric, String filename) {
		if (imageViewRef.get() != null) {
			this.setOnClickListener(null);

			if (thumbnail != null && !thumbnail.isRecycled()) {
				if (isGeneric) {
					if (!TestUtil.empty(filename)) {
						filenameViewRef.get().setText(filename);
						filenameViewRef.get().setVisibility(View.VISIBLE);
					}
				}
				previewViewRef.get().setImageBitmap(thumbnail);
				previewViewRef.get().setVisibility(View.VISIBLE);
			} else {
				previewViewRef.get().setVisibility(View.INVISIBLE);
			}
			imageViewRef.get().setVisibility(View.INVISIBLE);
		}
	}

	@Override
	protected void hideThumbnail() {
		this.previewViewRef.get().setVisibility(View.INVISIBLE);
	}

	@Override
	protected void created(Bundle savedInstanceState) {
		this.imageViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.gif_view));
		this.previewViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.preview_image));
		this.filenameViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.filename_view));

		this.imageViewRef.get().setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				showUi(uiVisibilityStatus);
				uiVisibilityStatus = !uiVisibilityStatus;
			}
		});
	}

	@Override
	protected void handleDecryptingFile() {
		//on decoding, do nothing!
	}

	@Override
	protected void handleDecryptFailure() {
		//
	}

	@Override
	protected void handleDecryptedFile(final File file) {
		if (this.isAdded() && getContext() != null) {
			if (FileUtil.isAnimGif(getContext().getContentResolver(), Uri.fromFile(file))) {
				try {
					GifDrawable gifDrawable = new GifDrawable(getContext().getContentResolver(), Uri.fromFile(file));
					this.imageViewRef.get().setImageDrawable(gifDrawable);
					this.imageViewRef.get().setVisibility(View.VISIBLE);
					gifDrawable.start();
					this.previewViewRef.get().setVisibility(View.GONE);
				} catch (IOException e) {
					//
				}
			} else {
				this.previewViewRef.get().setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						((MediaViewerActivity) getActivity()).viewMediaInGallery();
					}
				});
			}
		}
	}
}
