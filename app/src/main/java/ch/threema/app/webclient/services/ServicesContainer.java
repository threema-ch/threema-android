/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.webclient.services;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.storage.DatabaseServiceNew;

/**
 * Contains all necessary services used by the web client.
 */
@AnyThread
public class ServicesContainer {
    // App context
    @NonNull
    public final Context appContext;

    // Services
    @NonNull
    public final LifetimeService lifetime;
    @NonNull
    public final ContactService contact;
    @NonNull
    public final GroupService group;
    @NonNull
    public final DistributionListService distributionList;
    @NonNull
    public final ConversationService conversation;
    @NonNull
    public final ConversationTagService conversationTag;
    @NonNull
    public final MessageService message;
    @NonNull
    public final NotificationService notification;
    @NonNull
    public final DatabaseServiceNew database;
    @NonNull
    public final BlockedIdentitiesService blockedIdentitiesService;
    @NonNull
    public final PreferenceService preference;
    @NonNull
    public final UserService user;
    @NonNull
    public final DeadlineListService hiddenChat;
    @NonNull
    public final FileService file;
    @NonNull
    public final SynchronizeContactsService synchronizeContacts;
    @NonNull
    public final LicenseService license;
    @NonNull
    public final SessionWakeUpService sessionWakeUp;
    @NonNull
    public final WakeLockService wakeLock;
    @NonNull
    public final BatteryStatusService batteryStatus;
    @NonNull
    public final APIConnector apiConnector;
    @NonNull
    public final ContactModelRepository contactModelRepository;

    public ServicesContainer(
        @NonNull final Context appContext,
        @NonNull final LifetimeService lifetime,
        @NonNull final ContactService contact,
        @NonNull final GroupService group,
        @NonNull final DistributionListService distributionList,
        @NonNull final ConversationService conversation,
        @NonNull final ConversationTagService conversationTag,
        @NonNull final MessageService message,
        @NonNull final NotificationService notification,
        @NonNull final DatabaseServiceNew database,
        @NonNull final BlockedIdentitiesService blockedIdentitiesService,
        @NonNull final PreferenceService preference,
        @NonNull final UserService user,
        @NonNull final DeadlineListService hiddenChat,
        @NonNull final FileService file,
        @NonNull final SynchronizeContactsService synchronizeContacts,
        @NonNull final LicenseService license,
        @NonNull final APIConnector apiConnector,
        @NonNull final ContactModelRepository contactModelRepository
    ) {
        this.appContext = appContext;
        this.lifetime = lifetime;
        this.contact = contact;
        this.group = group;
        this.distributionList = distributionList;
        this.conversation = conversation;
        this.conversationTag = conversationTag;
        this.message = message;
        this.notification = notification;
        this.database = database;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.preference = preference;
        this.user = user;
        this.hiddenChat = hiddenChat;
        this.file = file;
        this.synchronizeContacts = synchronizeContacts;
        this.license = license;
        this.sessionWakeUp = SessionWakeUpServiceImpl.getInstance();
        this.apiConnector = apiConnector;
        this.contactModelRepository = contactModelRepository;

        // Initialize wakelock service
        this.wakeLock = new WakeLockServiceImpl(appContext, lifetime);

        // Initialize battery status service
        this.batteryStatus = new BatteryStatusServiceImpl(appContext);
    }
}
