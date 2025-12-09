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

package ch.threema.app.managers

import ch.threema.app.listeners.AppIconListener
import ch.threema.app.listeners.BallotListener
import ch.threema.app.listeners.BallotVoteListener
import ch.threema.app.listeners.ChatListener
import ch.threema.app.listeners.ContactCountListener
import ch.threema.app.listeners.ContactListener
import ch.threema.app.listeners.ContactSettingsListener
import ch.threema.app.listeners.ContactTypingListener
import ch.threema.app.listeners.ConversationListener
import ch.threema.app.listeners.DistributionListListener
import ch.threema.app.listeners.EditMessageListener
import ch.threema.app.listeners.GroupListener
import ch.threema.app.listeners.MessageDeletedForAllListener
import ch.threema.app.listeners.MessageListener
import ch.threema.app.listeners.MessagePlayerListener
import ch.threema.app.listeners.NewSyncedContactsListener
import ch.threema.app.listeners.PreferenceListener
import ch.threema.app.listeners.ProfileListener
import ch.threema.app.listeners.QRCodeScanListener
import ch.threema.app.listeners.SMSVerificationListener
import ch.threema.app.listeners.ServerMessageListener
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.listeners.ThreemaSafeListener
import ch.threema.app.listeners.VoipCallListener
import ch.threema.app.managers.ListenerManager.TypedListenerManager

class ListenerProvider {
    val conversationListeners: TypedListenerManager<ConversationListener>
        get() = ListenerManager.conversationListeners

    val contactListeners: TypedListenerManager<ContactListener>
        get() = ListenerManager.contactListeners

    val contactTypingListeners: TypedListenerManager<ContactTypingListener>
        get() = ListenerManager.contactTypingListeners

    val distributionListListeners: TypedListenerManager<DistributionListListener>
        get() = ListenerManager.distributionListListeners

    val groupListeners: TypedListenerManager<GroupListener>
        get() = ListenerManager.groupListeners

    val messageListeners: TypedListenerManager<MessageListener>
        get() = ListenerManager.messageListeners

    val messageDeletedForAllListener: TypedListenerManager<MessageDeletedForAllListener>
        get() = ListenerManager.messageDeletedForAllListener

    val preferenceListeners: TypedListenerManager<PreferenceListener>
        get() = ListenerManager.preferenceListeners

    val serverMessageListeners: TypedListenerManager<ServerMessageListener>
        get() = ListenerManager.serverMessageListeners

    val synchronizeContactsListeners: TypedListenerManager<SynchronizeContactsListener>
        get() = ListenerManager.synchronizeContactsListeners

    val contactSettingsListeners: TypedListenerManager<ContactSettingsListener>
        get() = ListenerManager.contactSettingsListeners

    val ballotListeners: TypedListenerManager<BallotListener>
        get() = ListenerManager.ballotListeners

    val ballotVoteListeners: TypedListenerManager<BallotVoteListener>
        get() = ListenerManager.ballotVoteListeners

    val smsVerificationListeners: TypedListenerManager<SMSVerificationListener>
        get() = ListenerManager.smsVerificationListeners

    val appIconListeners: TypedListenerManager<AppIconListener>
        get() = ListenerManager.appIconListeners

    val profileListeners: TypedListenerManager<ProfileListener>
        get() = ListenerManager.profileListeners

    val voipCallListeners: TypedListenerManager<VoipCallListener>
        get() = ListenerManager.voipCallListeners

    val threemaSafeListeners: TypedListenerManager<ThreemaSafeListener>
        get() = ListenerManager.threemaSafeListeners

    val chatListener: TypedListenerManager<ChatListener>
        get() = ListenerManager.chatListener

    val messagePlayerListener: TypedListenerManager<MessagePlayerListener>
        get() = ListenerManager.messagePlayerListener

    val newSyncedContactListener: TypedListenerManager<NewSyncedContactsListener>
        get() = ListenerManager.newSyncedContactListener

    val qrCodeScanListener: TypedListenerManager<QRCodeScanListener>
        get() = ListenerManager.qrCodeScanListener

    val contactCountListener: TypedListenerManager<ContactCountListener>
        get() = ListenerManager.contactCountListener

    val editMessageListener: TypedListenerManager<EditMessageListener>
        get() = ListenerManager.editMessageListener
}
