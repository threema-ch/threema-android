package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import ch.threema.app.services.AvatarCacheServiceImpl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory

class DistributionListAvatarModelLoaderFactory(private val context: Context) :
    ModelLoaderFactory<AvatarCacheServiceImpl.DistributionListAvatarConfig, Bitmap> {
    override fun build(unused: MultiModelLoaderFactory): ModelLoader<AvatarCacheServiceImpl.DistributionListAvatarConfig, Bitmap> =
        DistributionListAvatarLoader(context)

    override fun teardown() {
        // Nothing to do here
    }
}
