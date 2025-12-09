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

package ch.threema.app.di

import ch.threema.app.crashreporting.CrashReportingHelper
import ch.threema.app.drafts.DraftManager
import ch.threema.app.emojis.EmojiService
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.qrcodes.QrCodeGenerator
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
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
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.SensorService
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.services.UserService
import ch.threema.app.services.WallpaperService
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.services.VoipStateService
import ch.threema.app.webclient.manager.WebClientServiceManager
import ch.threema.app.webclient.services.SessionService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * This container can be used in Java activities (and potentially other Java classes), to facilitate dependency injection.
 * Must not be used in Kotlin, use Koin directly instead.
 * Must only be used in activities, as activities are guaranteed to be destroyed when the master key gets locked. If used in other components,
 * it might happen that services are leaked.
 */
class DependencyContainer : KoinComponent {
    private var serviceManagerInstance: ServiceManager? = null

    @Deprecated("Avoid accessing the service manager directly, use Koin instead")
    val serviceManager: ServiceManager?
        get() {
            val instance = serviceManagerInstance
            if (instance != null) {
                return instance
            }
            serviceManagerInstance = getOrNull()
            return serviceManagerInstance
        }

    val apiConnector: APIConnector by inject()
    val ballotService: BallotService by inject()
    val blockedIdentitiesService: BlockedIdentitiesService by inject()
    val contactModelRepository: ContactModelRepository by inject()
    val contactService: ContactService by inject()
    val conversationCategoryService: ConversationCategoryService by inject()
    val conversationService: ConversationService by inject()
    val crashReportingHelper: CrashReportingHelper by inject()
    val databaseService: DatabaseService by inject()
    val deviceService: DeviceService by inject()
    val dhSessionStore: DHSessionStoreInterface by inject()
    val distributionListService: DistributionListService by inject()
    val draftManager: DraftManager by inject()
    val emojiService: EmojiService by inject()
    val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService by inject()
    val fileService: FileService by inject()
    val groupCallManager: GroupCallManager by inject()
    val groupFlowDispatcher: GroupFlowDispatcher by inject()
    val groupModelRepository: GroupModelRepository by inject()
    val groupService: GroupService by inject()
    val identityStore: IdentityStore by inject()
    val licenseService: LicenseService<*> by inject()
    val lifetimeService: LifetimeService by inject()
    val localeService: LocaleService by inject()
    val lockAppService: LockAppService by inject()
    val messageService: MessageService by inject()
    val multiDeviceManager: MultiDeviceManager by inject()
    val notificationPreferenceService: NotificationPreferenceService by inject()
    val notificationService: NotificationService by inject()
    val preferenceService: PreferenceService by inject()
    val profilePictureRecipientsService: ProfilePictureRecipientsService by inject()
    val qrCodeGenerator: QrCodeGenerator by inject()
    val ringtoneService: RingtoneService by inject()
    val sensorService: SensorService by inject()
    val serverAddressProviderService: ServerAddressProviderService by inject()
    val serverConnection: ServerConnection by inject()
    val sessionService: SessionService by inject()
    val synchronizeContactsService: SynchronizeContactsService by inject()
    val taskCreator: TaskCreator by inject()
    val taskManager: TaskManager by inject()
    val threemaSafeMDMConfig: ThreemaSafeMDMConfig by inject()
    val threemaSafeService: ThreemaSafeService by inject()
    val userService: UserService by inject()
    val voipStateService: VoipStateService by inject()
    val wallpaperService: WallpaperService by inject()
    val webClientServiceManager: WebClientServiceManager by inject()
}
