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

package ch.threema.app.services

import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.GroupUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.domain.types.ConversationUID
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.protobuf.d2d.sync.MdD2DSync
import java.lang.ref.WeakReference

private val logger = getThreemaLogger("ConversationCategoryServiceImpl")

class ConversationCategoryServiceImpl(
    preferenceService: PreferenceService,
    preferenceStore: PreferenceStore,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskCreator: TaskCreator,
) : ConversationCategoryService {
    private val privateChatsCache = PrivateChatsCache(preferenceService, preferenceStore)

    /* Contact related methods */

    @Synchronized
    override fun markContactChatAsPrivate(contactModel: ContactModel) {
        val identity = contactModel.identity
        val uniqueIdentifier = UniqueIdentifier.fromContactIdentity(identity)
        if (privateChatsCache.isPrivateChat(uniqueIdentifier)) {
            logger.warn("Chat with {} is already marked as private", identity)
            return
        }

        privateChatsCache.addPrivateChat(uniqueIdentifier)
        reflectContact(
            identity = identity,
            isPrivateChat = true,
        )
    }

    @Synchronized
    override fun removePrivateMarkFromContactChat(contactModel: ContactModel) {
        removePrivateMarkFromContactChat(contactModel.identity)
    }

    @Synchronized
    override fun removePrivateMarkFromContactChat(contactModel: ch.threema.storage.models.ContactModel) {
        removePrivateMarkFromContactChat(contactModel.identity)
    }

    @Synchronized
    private fun removePrivateMarkFromContactChat(identity: Identity) {
        val uniqueIdentifier = UniqueIdentifier.fromContactIdentity(identity)
        if (!privateChatsCache.isPrivateChat(uniqueIdentifier)) {
            logger.warn("Chat with {} hasn't been marked as private", identity)
            return
        }

        privateChatsCache.removePrivateChat(uniqueIdentifier)
        reflectContact(
            identity = identity,
            isPrivateChat = false,
        )
    }

    /* Group related methods */

    @Synchronized
    override fun markGroupChatAsPrivate(groupDatabaseId: GroupDatabaseId) {
        val uniqueIdentifier = UniqueIdentifier.fromGroupDatabaseId(groupDatabaseId)
        if (privateChatsCache.isPrivateChat(uniqueIdentifier)) {
            logger.warn("Group chat with db id {} is already marked as private", groupDatabaseId)
            return
        }

        privateChatsCache.addPrivateChat(uniqueIdentifier)
        reflectGroup(
            groupDatabaseId = groupDatabaseId,
            isPrivateChat = true,
        )
    }

    @Synchronized
    override fun removePrivateMarkFromGroupChat(groupDatabaseId: GroupDatabaseId) {
        val uniqueIdentifier = UniqueIdentifier.fromGroupDatabaseId(groupDatabaseId)
        if (!privateChatsCache.isPrivateChat(uniqueIdentifier)) {
            logger.warn("Group chat with db id {} hasn't been marked as private", groupDatabaseId)
            return
        }

        privateChatsCache.removePrivateChat(uniqueIdentifier)
        reflectGroup(
            groupDatabaseId = groupDatabaseId,
            isPrivateChat = false,
        )
    }

    @Synchronized
    override fun isPrivateGroupChat(groupDatabaseId: GroupDatabaseId): Boolean {
        return privateChatsCache.isPrivateChat(UniqueIdentifier.fromGroupDatabaseId(groupDatabaseId))
    }

    /* General methods */

    @Synchronized
    override fun isPrivateChat(uniqueIdString: ConversationUID): Boolean {
        return privateChatsCache.isPrivateChat(UniqueIdentifier(uniqueIdString))
    }

    @Synchronized
    override fun getConversationCategory(uniqueIdString: ConversationUID): MdD2DSync.ConversationCategory {
        return if (privateChatsCache.isPrivateChat(UniqueIdentifier(uniqueIdString))) {
            MdD2DSync.ConversationCategory.PROTECTED
        } else {
            MdD2DSync.ConversationCategory.DEFAULT
        }
    }

    @Synchronized
    override fun markAsPrivate(messageReceiver: MessageReceiver<*>): Boolean {
        if (isPrivateChat(messageReceiver.uniqueIdString)) {
            // Nothing to do as the chat is already private
            return false
        }

        if (messageReceiver is ContactMessageReceiver) {
            val contactModel = messageReceiver.contactModel
            if (contactModel != null) {
                markContactChatAsPrivate(contactModel)
            } else {
                logger.error("Cannot mark contact conversation as private because contact model is null")
            }
        } else if (messageReceiver is GroupMessageReceiver) {
            markGroupChatAsPrivate(messageReceiver.group.id.toLong())
        } else {
            // TODO(ANDR-2718) or TODO(ANDR-3010): This change needs to be reflected when
            //  distribution lists are supported in MD.
            persistPrivateChat(messageReceiver.getUniqueIdString())
        }
        return true
    }

    @Synchronized
    override fun removePrivateMark(messageReceiver: MessageReceiver<*>): Boolean {
        if (!isPrivateChat(messageReceiver.uniqueIdString)) {
            // Nothing to do as the chat isn't private
            return false
        }

        if (messageReceiver is ContactMessageReceiver) {
            val contactModel = messageReceiver.contactModel
            if (contactModel != null) {
                removePrivateMarkFromContactChat(contactModel)
            } else {
                logger.error("Cannot mark contact conversation as non-private because contact model is null")
            }
        } else if (messageReceiver is GroupMessageReceiver) {
            removePrivateMarkFromGroupChat(messageReceiver.group.id.toLong())
        } else {
            // TODO(ANDR-2718) or TODO(ANDR-3010): This change needs to be reflected when
            //  distribution lists are supported in MD.
            persistDefaultChat(messageReceiver.getUniqueIdString())
        }
        return true
    }

    @Synchronized
    override fun persistPrivateChat(uniqueIdString: ConversationUID) {
        val uniqueIdentifier = UniqueIdentifier(uniqueIdString)
        if (!privateChatsCache.isPrivateChat(uniqueIdentifier)) {
            privateChatsCache.addPrivateChat(uniqueIdentifier)
        }
    }

    @Synchronized
    override fun persistDefaultChat(uniqueIdString: ConversationUID) {
        val uniqueIdentifier = UniqueIdentifier(uniqueIdString)
        if (privateChatsCache.isPrivateChat(uniqueIdentifier)) {
            privateChatsCache.removePrivateChat(uniqueIdentifier)
        }
    }

    @Synchronized
    override fun hasPrivateChats(): Boolean {
        return privateChatsCache.hasPrivateChats()
    }

    override fun invalidateCache() {
        privateChatsCache.invalidate()
    }

    private fun reflectContact(identity: Identity, isPrivateChat: Boolean) {
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleReflectContactConversationCategory(identity, isPrivateChat)
        }
    }

    private fun reflectGroup(groupDatabaseId: Long, isPrivateChat: Boolean) {
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleReflectGroupConversationCategory(
                groupDatabaseId = groupDatabaseId,
                isPrivateChat = isPrivateChat,
            )
        }
    }

    private class PrivateChatsCache(
        private val preferenceService: PreferenceService,
        private val preferenceStore: PreferenceStore,
    ) {
        private var privateChatsCache: WeakReference<MutableSet<ConversationUID>> = WeakReference(null)

        @Synchronized
        fun isPrivateChat(uniqueIdentifier: UniqueIdentifier): Boolean {
            return getPrivateChatUniqueIds().contains(uniqueIdentifier.uniqueId)
        }

        @Synchronized
        fun addPrivateChat(uniqueIdentifier: UniqueIdentifier) {
            val privateChatUniqueIds = getPrivateChatUniqueIds()
            privateChatUniqueIds.add(uniqueIdentifier.uniqueId)
            privateChatsCache = WeakReference(privateChatUniqueIds)
            preferenceService.setListQuietly(PREF_LIST_NAME, privateChatUniqueIds.toTypedArray(), false)
        }

        @Synchronized
        fun removePrivateChat(uniqueIdentifier: UniqueIdentifier) {
            val privateChatUniqueIds = getPrivateChatUniqueIds()
            privateChatUniqueIds.remove(uniqueIdentifier.uniqueId)
            privateChatsCache = WeakReference(privateChatUniqueIds)
            preferenceService.setListQuietly(PREF_LIST_NAME, privateChatUniqueIds.toTypedArray(), false)
        }

        @Synchronized
        fun hasPrivateChats(): Boolean {
            return getPrivateChatUniqueIds().isNotEmpty()
        }

        @Synchronized
        fun invalidate() {
            privateChatsCache = WeakReference(null)
        }

        private fun getPrivateChatUniqueIds(): MutableSet<ConversationUID> {
            return privateChatsCache.get() ?: run {
                val privateChatsUniqueIds = getFromPreferences()
                privateChatsCache = WeakReference(privateChatsUniqueIds)
                privateChatsUniqueIds
            }
        }

        @Synchronized
        private fun getFromPreferences(): MutableSet<String> {
            if (preferenceStore.containsKey(LEGACY_PREF_LIST_NAME)) {
                logger.info("Migrating private chats preference from '{}' to '{}'", LEGACY_PREF_LIST_NAME, PREF_LIST_NAME)
                // Previously, the conversation category (private chats) were saved with a deadline list service that used a map for storing the
                // property. The map used the unique id string as key and always had -1 as value, as it was never possible to mark a chat as private
                // for a limited time.
                val privateChatUniqueIdentifiers = preferenceService.getStringMap(LEGACY_PREF_LIST_NAME).keys.toMutableSet()
                preferenceService.setListQuietly(PREF_LIST_NAME, privateChatUniqueIdentifiers.toTypedArray(), false)
                preferenceStore.remove(LEGACY_PREF_LIST_NAME)
                return privateChatUniqueIdentifiers
            }

            return preferenceService.getList(PREF_LIST_NAME, false).toMutableSet()
        }
    }

    @JvmInline
    private value class UniqueIdentifier(val uniqueId: ConversationUID) {
        companion object {
            fun fromContactIdentity(identity: Identity): UniqueIdentifier {
                return UniqueIdentifier(ContactUtil.getUniqueIdString(identity))
            }

            fun fromGroupDatabaseId(groupDatabaseId: GroupDatabaseId): UniqueIdentifier {
                return UniqueIdentifier(GroupUtil.getUniqueIdString(groupDatabaseId))
            }
        }
    }

    companion object {
        // Do not change this list name as it is stored in preferences like this.
        private const val LEGACY_PREF_LIST_NAME = "list_hidden_chats"
        private const val PREF_LIST_NAME = "list_private_chats_unique_ids"
    }
}
