package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import ch.threema.app.di.injectNonBinding
import ch.threema.app.services.AvatarCacheServiceImpl
import ch.threema.app.services.FileService
import ch.threema.data.repositories.GroupModelRepository
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import org.koin.core.component.KoinComponent

class GroupAvatarLoader(
    private val context: Context,
) : ModelLoader<AvatarCacheServiceImpl.GroupAvatarConfig, Bitmap>, KoinComponent {
    private val groupModelRepository: GroupModelRepository? by injectNonBinding()
    private val fileService: FileService? by injectNonBinding()

    override fun buildLoadData(
        config: AvatarCacheServiceImpl.GroupAvatarConfig,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<Bitmap>? {
        return ModelLoader.LoadData(
            ObjectKey(config),
            GroupAvatarFetcher(
                context = context,
                groupModelRepository = groupModelRepository ?: return null,
                fileService = fileService ?: return null,
                config = config,
            ),
        )
    }

    override fun handles(model: AvatarCacheServiceImpl.GroupAvatarConfig) = true
}
