package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import ch.threema.app.services.avatarcache.AvatarCacheServiceImpl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory

class IdentityAvatarModelLoaderFactory(private val context: Context) :
    ModelLoaderFactory<AvatarCacheServiceImpl.IdentityAvatarConfig, Bitmap> {
    override fun build(unused: MultiModelLoaderFactory): ModelLoader<AvatarCacheServiceImpl.IdentityAvatarConfig, Bitmap> =
        IdentityAvatarLoader(context)

    override fun teardown() {
        // Nothing to do here
    }
}
