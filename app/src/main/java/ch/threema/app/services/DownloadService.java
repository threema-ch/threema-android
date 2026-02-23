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
