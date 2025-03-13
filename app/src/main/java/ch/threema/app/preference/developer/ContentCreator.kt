/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.preference.developer

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentManager
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.managers.ServiceManager
import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.domain.models.MessageId
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextInt

private val logger = LoggingUtil.getThreemaLogger("ContentCreator")

private const val AMOUNT_OF_NONCES = 50_000
private const val SPAM_MESSAGES_PER_CONVERSATION = 1000

/**
 * Chats with names that start with this prefix will be used when messages are created.
 * For groups this is the group name, for contacts the first name has to start with the prefix
 */
private const val SPAM_CHATS_PREFIX = "\uD83D\uDC7E" // 👾


object ContentCreator {
    @JvmStatic
    @AnyThread
    fun createReactionSpam(serviceManager: ServiceManager, fragmentManager: FragmentManager) {
        CoroutineScope(Dispatchers.Default).launch {
            val goOn = confirm(fragmentManager, "Create loads of messages with reactions and/or ACK/DEC for any contact/group whose name starts with '$SPAM_CHATS_PREFIX'?")
            if (!goOn) {
                return@launch
            }
            withGenericProgress(fragmentManager, "Creating reaction spam...") {
                val contacts = serviceManager.contactService.all
                    .filter { isSpamChat(it.firstName) }
                createContactReactionSpam(contacts, serviceManager)

                val groups = serviceManager.groupService.all
                    .filter { isSpamChat(it.name) }
                createGroupReactionSpam(groups, serviceManager)
            }
        }
    }

    private fun createGroupReactionSpam(
        groups: List<GroupModel>,
        serviceManager: ServiceManager
    ) {
        val reactions = mutableListOf<DbEmojiReaction>()

        groups.forEach { groupModel ->
            repeat(SPAM_MESSAGES_PER_CONVERSATION) {
                logger.debug("Group spam message #{}", it)
                reactions.addAll(createGroupReactionSpam(groupModel, serviceManager))
            }
        }

        serviceManager.modelRepositories.emojiReaction.restoreGroupReactions { insertHandle ->
            reactions.shuffled().forEach { insertHandle.insert(it) }
        }
    }

    private fun createGroupReactionSpam(
        groupModel: GroupModel,
        serviceManager: ServiceManager,
    ): List<DbEmojiReaction> {
        logger.info("Create messages with reaction/ack/dec in group {}", groupModel.id)

        val groupService = serviceManager.groupService
        val userIdentity = serviceManager.userService.identity
        val groupMessageModelFactory = serviceManager.databaseServiceNew.groupMessageModelFactory

        val members = groupService.getGroupIdentities(groupModel).toList()

        val reactionIdentities = mutableListOf<String>()
        val groupMessageStates = mutableMapOf<String, Any>()

        members.forEach { memberIdentity ->
            if (Random.nextDouble() > 0.3) {
                // add reactions
                reactionIdentities.add(memberIdentity)
            } else {
                groupMessageStates[memberIdentity] = if (Random.nextBoolean()) {
                    MessageState.USERACK.toString()
                } else {
                    MessageState.USERDEC.toString()
                }
            }
        }

        val reactions = createReactions(reactionIdentities)

        val senderIdentity = members.random()
        val message = createGroupMessage(
            createGroupText(groupMessageStates, reactions),
            senderIdentity,
            userIdentity,
            groupMessageStates,
            groupModel
        )

        groupMessageModelFactory.create(message)
        return reactions.toDbReactions(message.id)
    }

    private fun createGroupText(messageStates: Map<String, Any>, reactions: List<Pair<String, Set<String>>>): String {
        val stateTexts = messageStates
            .map { (identity, state) -> "@[$identity]: $state" }
        val reactionTexts = reactions
            .map { (identity, reactions) -> "@[$identity]: ${reactions.joinToString(", ")}" }
        return (stateTexts + reactionTexts).joinToString("\n")
    }

    private fun createContactReactionSpam(
        contacts: List<ContactModel>,
        serviceManager: ServiceManager
    ) {
        val reactions = mutableListOf<DbEmojiReaction>()

        contacts.forEach { contactModel ->
            repeat(SPAM_MESSAGES_PER_CONVERSATION) {
                logger.debug("Contact spam message #{}", it)
                reactions.addAll(createContactReactionSpam(contactModel, serviceManager))
            }
        }

        serviceManager.modelRepositories.emojiReaction.restoreContactReactions { insertHandle ->
            reactions.shuffled().forEach { insertHandle.insert(it) }
        }
    }

    private fun createContactReactionSpam(
        contactModel: ContactModel,
        serviceManager: ServiceManager
    ): List<DbEmojiReaction> {
        logger.info("Create ack/dec messages for contact {}", contactModel)

        val userIdentity = serviceManager.userService.identity
        val messageModelFactory = serviceManager.databaseServiceNew.messageModelFactory

        val hasUserReactions = Random.nextBoolean()
        val hasContactReactions = Random.nextBoolean()
        val hasAckDec = (!hasContactReactions && !hasUserReactions)
            || ((!hasContactReactions || !hasUserReactions) && Random.nextBoolean())

        val state = if (!hasAckDec) {
            null
        } else if (Random.nextBoolean()) {
            MessageState.USERACK
        } else {
            MessageState.USERDEC
        }

        val reactionsIdentities = mutableListOf<String>()

        if (hasUserReactions) {
            reactionsIdentities.add(userIdentity)
        }
        if (hasContactReactions) {
            reactionsIdentities.add(contactModel.identity)
        }

        val reactions = createReactions(reactionsIdentities)

        val message = createContactMessage(
            createContactText(state, reactions),
            isOutbox = Random.nextBoolean(),
            state = state,
            contactModel
        )
        messageModelFactory.create(message)
        return reactions.toDbReactions(message.id)
    }

    private fun createContactText(state: MessageState?, reactions: List<Pair<String, Set<String>>>): String {
        val stateText = state?.let { "State: $it" }
        val reactionTexts = reactions
            .map { (identity, reactions) -> "@[$identity]: ${reactions.joinToString(", ")}" }
        return (listOfNotNull(stateText) + reactionTexts).joinToString("\n")
    }

    private fun List<Pair<String, Set<String>>>.toDbReactions(messageId: Int): List<DbEmojiReaction> {
        return flatMap { (identity, reactions) ->
            reactions.map{ reaction -> DbEmojiReaction(
                messageId,
                identity,
                reaction,
                Date()
            ) }
        }
    }

    private fun createReactions(identities: List<String>): List<Pair<String, Set<String>>> {
        val availableReactions = getReactionSequences(identities.size * 3)
        return identities.map { identity ->
            val numberOfReactions = Random.nextInt(1 .. 3)
            identity to availableReactions.shuffled().take(numberOfReactions).toSet()
        }.filter { it.second.isNotEmpty() }
    }

    private fun isSpamChat(identifier: String?)
        = identifier?.startsWith(SPAM_CHATS_PREFIX) == true

    private fun createContactMessage(
        text: String,
        isOutbox: Boolean,
        state: MessageState?,
        contactModel: ContactModel,
    ): MessageModel = MessageModel().apply {
        identity = contactModel.identity
        enrichTextMessage(text, isOutbox, state)
    }

    private fun createGroupMessage(
        text: String,
        senderIdentity: String,
        userIdentity: String,
        groupMessageStates: Map<String, Any>,
        groupModel: GroupModel,
    ): GroupMessageModel = GroupMessageModel().apply {
        groupId = groupModel.id
        identity = senderIdentity
        this.groupMessageStates  = groupMessageStates.toMap()
        enrichTextMessage(
            text,
            senderIdentity == userIdentity
        )
    }

    private fun AbstractMessageModel.enrichTextMessage(text: String, isOutbox: Boolean, state: MessageState? = null) {
        val theDate = Date()
        uid = UUID.randomUUID().toString()
        apiMessageId = MessageId().toString()
        this.isOutbox = isOutbox
        this.type = MessageType.TEXT
        bodyAndQuotedMessageId = text
        isRead = true
        this.state = state ?: if (isOutbox) {
            MessageState.DELIVERED
        } else {
            MessageState.READ
        }
        postedAt = theDate
        createdAt = theDate
        isSaved = true
    }

    private fun getReactionSequences(n: Int): List<String> = setOf(
        "👍", "👎", "🪒", "🌛", "🧲", "🇹🇹", "🧽", "🧎🏻‍♀️", "🧏🏽‍♀️", "🧝🏻‍♂️",
        "👩🏿‍🚒", "🏌️‍♂️", "👨🏻", "🤸‍♂️", "👩🏿‍🦰", "👨🏼‍🦼", "🕹️", "🍾", "🇨🇫", "🍫",
        "🧀", "🍔", "🕵🏼‍♂️", "👨🏻‍🏫", "🤷🏻‍♀️", "🧯", "🩼", "✍🏾", "🦶🏻", "🏊🏻‍♀️",
        "😔", "⌛", "👮🏿‍♂️", "☔", "🧎🏿‍➡️", "🕡", "👑", "🧖🏾", "🧑🏻‍🔬", "🐧",
        "🧑🏾‍🎤", "🧑🏻‍🦲", "⛲", "👇🏻", "⛹🏼", "🌦️", "🙋🏾", "🦸🏼‍♂️", "👩🏻‍🎤", "🏊🏿",
        "👮🏾‍♂️", "📵", "🧖🏻", "🇱🇹", "👨🏻‍❤️‍👨🏿", "👦🏼", "🚶🏽‍➡️", "🥏", "🏹", "🧑🏻‍🎨",
        "🏄🏿", "🇦🇶", "🧑🏿‍🎄", "👩🏾‍🍳", "📳", "🫱🏼‍🫲🏽", "👨‍👧‍👦", "👩🏽‍❤️‍💋‍👩🏿", "🌐", "🫃🏾",
        "💅🏿", "🤰🏻", "🧎🏽", "🏃🏿‍♂️", "👨🏼‍🚒", "🦇", "✈️", "👩🏽‍🤝‍👨🏿", "🐎", "🏒",
        "👈🏾", "🇱🇺", "🫙", "🇸🇿", "🧍🏼‍♂️", "💁🏼‍♂️", "🧑🏿‍🔧", "👨🏽‍🍳", "🦵🏽", "🧙🏿‍♂️",
        "🧙‍♀️", "💆🏾‍♀️", "↔️", "🧑🏿‍🦲", "🫴🏼", "🤚", "🫱🏼", "🏌🏾‍♂️", "🥦", "🤛🏻",
        "\uD83E\uDEC6"
    ).shuffled().take(n)

    @JvmStatic
    @AnyThread
    fun createNonces(serviceManager: ServiceManager, fragmentManager: FragmentManager) {
        CoroutineScope(Dispatchers.Default).launch {
            val goOn = confirm(
                fragmentManager,
                "Generate $AMOUNT_OF_NONCES nonces for each scope ${NonceScope.CSP} and ${NonceScope.D2D}?"
            )
            if (!goOn) {
                return@launch
            }
            withGenericProgress(fragmentManager, "Generate random nonces") {
                createNonces(NonceScope.CSP, serviceManager.nonceFactory)
                createNonces(NonceScope.D2D, serviceManager.nonceFactory)
            }
        }
    }

    @WorkerThread
    private fun createNonces(scope: NonceScope, nonceFactory: NonceFactory) {
        logger.info("Generate random nonces for scope {}", scope)
        val nonces = (0 until AMOUNT_OF_NONCES).asSequence()
            .map { nonceFactory.next(scope) }
            // We skip hashing of nonces for faster generation of test data
            .map { HashedNonce(it.bytes) }.toList()
        val success = nonceFactory.insertHashedNonces(scope, nonces)
        logger.info("Generate {} nonces success={}", nonces.size, success)
    }

    private suspend fun confirm(fragmentManager: FragmentManager, message: String): Boolean {
        val dialog = GenericAlertDialog.newInstance("Continue?", message, R.string.ok, R.string.no)
        val result = CompletableDeferred<Boolean>()
        dialog.setCallback(object : GenericAlertDialog.DialogClickListener {
            override fun onYes(tag: String?, data: Any?) {
                result.complete(true)
            }

            override fun onNo(tag: String?, data: Any?) {
                result.complete(false)
            }
        })
        dialog.show(fragmentManager, "CONTENT_CREATOR_CONFIRM_DIALOG")
        return result.await()
    }

    private fun withGenericProgress(fragmentManager: FragmentManager, message: String, block: () -> Unit) {
        val dialog = GenericProgressDialog.newInstance(null, message)
        dialog.show(fragmentManager, "CONTENT_CREATOR_PROGRESS_DIALOG")
        try {
            block()
        } finally {
            dialog.dismiss()
        }
    }
}
