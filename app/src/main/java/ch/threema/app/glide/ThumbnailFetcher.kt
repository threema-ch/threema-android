package ch.threema.app.glide

import android.graphics.Bitmap
import ch.threema.app.services.FileService
import ch.threema.storage.models.AbstractMessageModel
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class is used to get the thumbnails from the database or create placeholders. The results of the loaded bitmaps will be cached by glide (if possible).
 */
class ThumbnailFetcher(
    private val fileService: FileService,
    private val messageModel: AbstractMessageModel,
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val thumbnail: Bitmap? = try {
            fileService.getMessageThumbnailBitmap(messageModel, null)
        } catch (_: Exception) {
            null
        }

        callback.onDataReady(thumbnail)
    }

    override fun cleanup() {
        // Nothing to cleanup
    }

    override fun cancel() {
        // Nothing to do here
    }

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}
