package ch.threema.app.glide

import android.graphics.Bitmap
import ch.threema.storage.models.AbstractMessageModel
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory

class ThumbnailLoaderFactory : ModelLoaderFactory<AbstractMessageModel, Bitmap> {
    override fun build(unused: MultiModelLoaderFactory): ModelLoader<AbstractMessageModel, Bitmap> =
        ThumbnailLoader()

    override fun teardown() {
        // Nothing to do here
    }
}
