/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import ch.threema.app.di.injectNonBinding
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.AvatarCacheServiceImpl
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.app.services.UserService
import ch.threema.data.repositories.ContactModelRepository
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import org.koin.core.component.KoinComponent

class IdentityAvatarLoader(
    private val context: Context,
) : ModelLoader<AvatarCacheServiceImpl.IdentityAvatarConfig, Bitmap>, KoinComponent {
    private val userService: UserService? by injectNonBinding()
    private val contactService: ContactService? by injectNonBinding()
    private val preferenceService: PreferenceService? by injectNonBinding()
    private val fileService: FileService? by injectNonBinding()
    private val contactModelRepository: ContactModelRepository? by injectNonBinding()

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
