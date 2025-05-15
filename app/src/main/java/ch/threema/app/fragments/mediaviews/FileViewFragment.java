/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class FileViewFragment extends MediaViewFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("FileViewFragment");
    private WeakReference<ImageView> previewViewRef;
    private WeakReference<TextView> filenameViewRef;
    private WeakReference<TextView> mimeTypeViewRef;

    public FileViewFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    protected int getFragmentResourceId() {
        return R.layout.fragment_media_viewer_file;
    }

    @Override
    protected void created(Bundle savedInstanceState) {
        this.previewViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.preview_image));
        this.filenameViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.filename_view));
        this.mimeTypeViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.file_type));
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
            this.previewViewRef.get().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ((MediaViewerActivity) requireActivity()).viewMediaInGallery();
                }
            });
        }
    }

    @Override
    protected void handleMimeCategory(@NonNull MimeUtil.MimeCategory category) {
        // Set mime category icon
        if (this.previewViewRef != null && this.previewViewRef.get() != null) {
            this.previewViewRef.get().setImageDrawable(ResourcesCompat.getDrawable(getResources(), IconUtil.getMimeCategoryIcon(category), requireActivity().getTheme()));
        }
        // Set mime description
        if (this.mimeTypeViewRef != null && this.mimeTypeViewRef.get() != null) {
            Integer mimeDescription = MimeUtil.getMimeDescription(category);
            if (mimeDescription != null) {
                this.mimeTypeViewRef.get().setText(mimeDescription);
            } else {
                this.mimeTypeViewRef.get().setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void handleFileName(@Nullable String filename) {
        if (filenameViewRef != null && filenameViewRef.get() != null) {
            if (filename != null) {
                filenameViewRef.get().setText(filename);
                filenameViewRef.get().setVisibility(View.VISIBLE);
            } else {
                filenameViewRef.get().setVisibility(View.GONE);
            }
        }
    }
}
