/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.base.ProgressListener;
import ch.threema.domain.protocol.blob.BlobScope;

public interface DownloadService {

    /**
     * @param blobScopeMarkAsDone If this field is <strong>not</strong> {@code null}, the blob will
     *                            automatically be marked as "done" on the server. Of course only if
     *                            the preceding download succeeded.
     */
    @WorkerThread
    @Nullable
    byte[] download(
        int id,
        @Nullable byte[] blobId,
        @NonNull BlobScope blobScopeDownload,
        @Nullable BlobScope blobScopeMarkAsDone,
        @Nullable ProgressListener progressListener
    );

    void complete(int id, byte[] blobId);

    boolean cancel(int id);

    boolean isDownloading(int blobId);

    boolean isDownloading();

    void error(int id);
}
