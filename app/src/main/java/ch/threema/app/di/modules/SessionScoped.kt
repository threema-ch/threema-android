/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.di.modules

import ch.threema.app.di.DependencyContainer
import ch.threema.app.di.repository
import ch.threema.app.di.service
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.services.WallpaperService
import ch.threema.app.webclient.manager.WebClientServiceManager
import ch.threema.localcrypto.MasterKeyManager
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Provides all the components which are tied to the [ServiceManager]'s lifecycle, i.e., they are only available while the master key is unlocked.
 */
val sessionScopedModule = module {
    factory<ServiceManager> { get<ServiceManagerProvider>().getServiceManager() }
    factory { get<ServerAddressProviderService>().serverAddressProvider }
    factory { get<MasterKeyManager>().masterKeyProvider }
    factory { get<WebClientServiceManager>().sessionService }

    service { apiConnector }
    service { apiService }
    service { avatarCacheService }
    service { backupChatService }
    service { ballotService }
    service { blockedIdentitiesService }
    service { connection }
    service { contactService }
    service { conversationCategoryService }
    service { conversationService }
    service { conversationTagService }
    service { databaseService }
    service { deviceService }
    service { dhSessionStore }
    service { distributionListService }
    service { emojiService }
    service { encryptedPreferenceStore }
    service<ExcludedSyncIdentitiesService> { excludedSyncIdentitiesService }
    service { fileService }
    service { groupCallManager }
    service { groupCallManager }
    service { groupFlowDispatcher }
    service { groupService }
    service { identityStore }
    service { licenseService }
    service { lifetimeService }
    service { localeService }
    service { lockAppService }
    service { messageService }
    service { multiDeviceManager }
    service { notificationPreferenceService }
    service { notificationService }
    service { okHttpClient }
    service { preferenceService }
    service { profilePicRecipientsService }
    service { qrCodeService }
    service { ringtoneService }
    service { screenLockService }
    service { sensorService }
    service { serverAddressProviderService }
    service { synchronizeContactsService }
    service { taskCreator }
    service { taskManager }
    service { threemaSafeService }
    service { userService }
    service { voipStateService }
    service<WallpaperService> { wallpaperService }
    service { webClientServiceManager }

    repository { contacts }
    repository { editHistory }
    repository { emojiReaction }
    repository { groups }

    factoryOf(::DependencyContainer)
}
