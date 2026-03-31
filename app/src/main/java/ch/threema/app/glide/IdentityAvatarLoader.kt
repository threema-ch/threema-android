package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.app.services.UserService
import ch.threema.app.services.avatarcache.AvatarCacheServiceImpl
import ch.threema.data.repositories.ContactModelRepository
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import org.koin.core.component.KoinComponent

class IdentityAvatarLoader(
    private val context: Context,
) : ModelLoader<AvatarCacheServiceImpl.IdentityAvatarConfig, Bitmap>, KoinComponent {
    private val userService: UserService? by injectNullableNonBinding()
    private val contactService: ContactService? by injectNullableNonBinding()
    private val preferenceService: PreferenceService? by injectNullableNonBinding()
    private val fileService: FileService? by injectNullableNonBinding()
    private val contactModelRepository: ContactModelRepository? by injectNullableNonBinding()

    override fun buildLoadData(
        config: AvatarCacheServiceImpl.IdentityAvatarConfig,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<Bitmap>? {
        return ModelLoader.LoadData(
            ObjectKey(config),
            IdentityAvatarFetcher(
                context = context,
                userService = userService ?: return null,
                contactService = contactService ?: return null,
                contactModelRepository = contactModelRepository ?: return null,
                preferenceService = preferenceService ?: return null,
                fileService = fileService ?: return null,
                identityAvatarConfig = config,
            ),
        )
    }

    override fun handles(model: AvatarCacheServiceImpl.IdentityAvatarConfig) = true
}
