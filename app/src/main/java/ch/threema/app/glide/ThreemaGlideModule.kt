package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import ch.threema.app.services.AvatarCacheServiceImpl.DistributionListAvatarConfig
import ch.threema.app.services.AvatarCacheServiceImpl.GroupAvatarConfig
import ch.threema.app.services.AvatarCacheServiceImpl.IdentityAvatarConfig
import ch.threema.storage.models.AbstractMessageModel
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class ThreemaGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(RequestOptions().format(DecodeFormat.PREFER_ARGB_8888))
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        with(registry) {
            prepend(IdentityAvatarConfig::class.java, Bitmap::class.java, IdentityAvatarModelLoaderFactory(context))
            prepend(GroupAvatarConfig::class.java, Bitmap::class.java, GroupAvatarModelLoaderFactory(context))
            prepend(DistributionListAvatarConfig::class.java, Bitmap::class.java, DistributionListAvatarModelLoaderFactory(context))
            prepend(AbstractMessageModel::class.java, Bitmap::class.java, ThumbnailLoaderFactory())
        }
    }
}
