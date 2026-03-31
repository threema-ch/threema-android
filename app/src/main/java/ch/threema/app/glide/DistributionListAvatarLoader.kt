package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.avatarcache.AvatarCacheServiceImpl
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import org.koin.core.component.KoinComponent

class DistributionListAvatarLoader(
    private val context: Context,
) :
    ModelLoader<AvatarCacheServiceImpl.DistributionListAvatarConfig, Bitmap>, KoinComponent {
    private val distributionListService: DistributionListService? by injectNullableNonBinding()

    override fun buildLoadData(
        config: AvatarCacheServiceImpl.DistributionListAvatarConfig,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<Bitmap>? {
        return ModelLoader.LoadData(
            ObjectKey(config),
            DistributionListAvatarFetcher(
                context = context,
                distributionListService = distributionListService ?: return null,
                distributionListConfig = config,
            ),
        )
    }

    override fun handles(model: AvatarCacheServiceImpl.DistributionListAvatarConfig) = true
}
