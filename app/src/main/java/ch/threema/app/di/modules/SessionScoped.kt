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

import ch.threema.app.backuprestore.BackupChatService
import ch.threema.app.di.DependencyContainer
import ch.threema.app.di.isSessionScopeReady
import ch.threema.app.di.repository
import ch.threema.app.di.service
import ch.threema.app.emojis.EmojiService
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.services.ActivityService
import ch.threema.app.services.ApiService
import ch.threema.app.services.AvatarCacheService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.DeviceService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.LocaleService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.MessageService
import ch.threema.app.services.OnPremConfigFetcherProvider
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.SensorService
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.services.UserService
import ch.threema.app.services.WallpaperService
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.services.VoipStateService
import ch.threema.app.webclient.manager.WebClientServiceManager
import ch.threema.app.webclient.services.SessionService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.LicenseCredentials
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseService
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Provides the common components which depend (directly or indirectly) on the master key being unlocked.
 * Under the hood this is backed by [ServiceManager], though this is an implementation detail that is subject to change.
 *
 * To check whether a component from this module is available, use [isSessionScopeReady] or use Koin's `getOrNull`.
 * All components in this module should be annotated with [ch.threema.base.SessionScoped], if possible.
 */
val sessionScopedModule = module {
    factory<ServiceManager?> { get<ServiceManagerProvider>().getServiceManagerOrNull() }
    factory<ServerAddressProvider?> { get<ServerAddressProviderService>().serverAddressProvider }
    factory<SessionService> { get<WebClientServiceManager>().sessionService }

    service<ActivityService> { activityService }
    service<APIConnector> { apiConnector }
    service<ApiService> { apiService }
    service<AvatarCacheService> { avatarCacheService }
    service<BackupChatService> { backupChatService }
    service<BallotService> { ballotService }
    service<BlockedIdentitiesService> { blockedIdentitiesService }
    service<ContactService> { contactService }
    service<ConversationCategoryService> { conversationCategoryService }
    service<ConversationService> { conversationService }
    service<ConversationTagService> { conversationTagService }
    service<DHSessionStoreInterface> { dhSessionStore }
    service<DatabaseService> { databaseService }
    service<DeviceService> { deviceService }
    service<DistributionListService> { distributionListService }
    service<EmojiService> { emojiService }
    service<ExcludedSyncIdentitiesService> { excludedSyncIdentitiesService }
    service<FileService> { fileService }
    service<GroupCallManager> { groupCallManager }
    service<GroupFlowDispatcher> { groupFlowDispatcher }
    service<GroupProfilePictureUploader> { groupProfilePictureUploader }
    service<GroupService> { groupService }
    service<IdentityStore> { identityStore }
    service<LicenseService<LicenseCredentials>> { licenseService }
    service<LifetimeService> { lifetimeService }
    service<LocaleService> { localeService }
    service<LockAppService> { lockAppService }
    service<MessageService> { messageService }
    service<MultiDeviceManager> { multiDeviceManager }
    service<NotificationService> { notificationService }
    service<OkHttpClient> { okHttpClient }
    service<OnPremConfigFetcherProvider> { onPremConfigFetcherProvider }
    service<PreferenceService> { preferenceService }
    service<ProfilePictureRecipientsService> { profilePicRecipientsService }
    service<SensorService> { sensorService }
    service<ServerAddressProviderService> { serverAddressProviderService }
    service<ServerConnection> { connection }
    service<SynchronizeContactsService> { synchronizeContactsService }
    service<TaskCreator> { taskCreator }
    service<TaskManager> { taskManager }
    service<ThreemaSafeService> { threemaSafeService }
    service<UserService> { userService }
    service<VoipStateService> { voipStateService }
    service<WallpaperService> { wallpaperService }
    service<WebClientServiceManager> { webClientServiceManager }

    repository<ContactModelRepository> { contacts }
    repository<EditHistoryRepository> { editHistory }
    repository<EmojiReactionsRepository> { emojiReaction }
    repository<GroupModelRepository> { groups }

    factoryOf(::DependencyContainer)
}
