package ch.threema.app.preference.developer

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentManager
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.managers.ServiceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import java.util.Date
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("ContentCreator")

private const val AMOUNT_OF_NONCES = 50_000
private const val SPAM_TEXT_MESSAGES_PER_CONVERSATION = 50000
private const val SPAM_MESSAGES_WITH_REACTIONS_PER_CONVERSATION = 1000

/**
 * Chats with names that start with this prefix will be used when messages are created.
 * For groups this is the group name, for contacts the first name has to start with the prefix
 */
private const val SPAM_CHATS_PREFIX = "\uD83D\uDC7E" // 👾

object ContentCreator {
    @JvmStatic
    @AnyThread
    fun createTextMessageSpam(serviceManager: ServiceManager, fragmentManager: FragmentManager) {
        CoroutineScope(Dispatchers.Default).launch {
            val goOn = confirm(
                fragmentManager,
                "Create $SPAM_TEXT_MESSAGES_PER_CONVERSATION messages in any contact/group whose name starts with '$SPAM_CHATS_PREFIX'?",
            )
            if (!goOn) {
                return@launch
            }
            withGenericProgress(fragmentManager, "Creating message spam...") {
                val contacts = serviceManager.contactService.all
                    .filter { isSpamChat(it.firstName) }
                createContactTextSpam(contacts, serviceManager)

                val groups = serviceManager.groupService.all
                    .filter { isSpamChat(it.name) }
                createGroupTextSpam(groups, serviceManager)
            }
        }
    }

    @JvmStatic
    @AnyThread
    fun createReactionSpam(serviceManager: ServiceManager, fragmentManager: FragmentManager) {
        CoroutineScope(Dispatchers.Default).launch {
            val goOn = confirm(
                fragmentManager,
                "Create loads of messages with reactions and/or ACK/DEC for any contact/group whose name starts with '$SPAM_CHATS_PREFIX'?",
            )
            if (!goOn) {
                return@launch
            }
            withGenericProgress(fragmentManager, "Creating reaction spam...") {
                val contacts = serviceManager.contactService.all
                    .filter { isSpamChat(it.firstName) }
                createContactReactionSpam(contacts, serviceManager)

                val groups = serviceManager.groupService.all
                    .filter { isSpamChat(it.name) }
                logger.debug("Group ids for reaction spam: [{}]", groups.joinToString(", ") { "${it.id}" })
                createGroupReactionSpam(groups, serviceManager)
            }
        }
    }

    private fun createGroupReactionSpam(
        groups: List<GroupModel>,
        serviceManager: ServiceManager,
    ) {
        val reactions = mutableListOf<DbEmojiReaction>()
        val groupService = serviceManager.groupService
        groups.forEach { groupModel ->
            logger.info(
                "Create messages with reaction/ack/dec in group with id={}",
                groupModel.id,
            )
            val members = groupService.getGroupMemberIdentities(groupModel).toList()
            if (members.isEmpty()) {
                logger.debug("Skip group without members")
                return@forEach
            }
            repeat(SPAM_MESSAGES_WITH_REACTIONS_PER_CONVERSATION) {
                logger.debug("Group reaction spam message #{}", it)
                reactions.addAll(
                    createGroupReactionSpam(groupModel, members, serviceManager),
                )
            }
        }

        serviceManager.modelRepositories.emojiReaction.restoreGroupReactions { insertHandle ->
            reactions.shuffled().forEach { insertHandle.insert(it) }
        }
    }

    private fun createGroupReactionSpam(
        groupModel: GroupModel,
        members: List<String>,
        serviceManager: ServiceManager,
    ): List<DbEmojiReaction> {
        val userIdentity = serviceManager.userService.identity!!
        val groupMessageModelFactory = serviceManager.databaseService.groupMessageModelFactory

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
            groupModel,
        )

        groupMessageModelFactory.create(message)
        return reactions.toDbReactions(message.id)
    }

    private fun createGroupText(
        messageStates: Map<String, Any>,
        reactions: List<Pair<String, Set<String>>>,
    ): String {
        val stateTexts = messageStates
            .map { (identity, state) -> "@[$identity]: $state" }
        val reactionTexts = reactions
            .map { (identity, reactions) -> "@[$identity]: ${reactions.joinToString(", ")}" }
        return (stateTexts + reactionTexts).joinToString("\n")
    }

    private fun createContactTextSpam(
        contacts: List<ContactModel>,
        serviceManager: ServiceManager,
    ) {
        contacts.forEach { contactModel ->
            logger.info("Create spam messages for contact with identity {}", contactModel.identity)
            repeat(SPAM_TEXT_MESSAGES_PER_CONVERSATION) {
                logger.debug("Contact text spam message #{}", it)
                createContactTextSpamMessage(it, contactModel, serviceManager)
            }
        }
    }

    private fun createContactTextSpamMessage(
        no: Int,
        contactModel: ContactModel,
        serviceManager: ServiceManager,
    ) {
        val messageModelFactory = serviceManager.databaseService.messageModelFactory

        val message = createContactMessage(
            "Spam #$no",
            isOutbox = Random.nextBoolean(),
            state = null,
            contactModel,
        )
        messageModelFactory.create(message)
    }

    private fun createGroupTextSpam(
        groups: List<GroupModel>,
        serviceManager: ServiceManager,
    ) {
        val groupService = serviceManager.groupService
        groups.forEach { groupModel ->
            logger.info(
                "Create text messages in group with id={}",
                groupModel.id,
            )
            val members = groupService.getGroupMemberIdentities(groupModel).toList()
            if (members.isEmpty()) {
                logger.debug("Skip empty group")
                return@forEach
            }
            repeat(SPAM_TEXT_MESSAGES_PER_CONVERSATION) {
                logger.debug("Group text spam message #{}", it)
                createGroupTextSpamMessage(it, groupModel, members, serviceManager)
            }
        }
    }

    private fun createGroupTextSpamMessage(
        no: Int,
        groupModel: GroupModel,
        members: List<String>,
        serviceManager: ServiceManager,
    ) {
        val userIdentity = serviceManager.userService.identity!!
        val groupMessageModelFactory = serviceManager.databaseService.groupMessageModelFactory

        val senderIdentity = members.random()
        val message = createGroupMessage(
            "Spam message #$no",
            senderIdentity,
            userIdentity,
            emptyMap(),
            groupModel,
        )

        groupMessageModelFactory.create(message)
    }

    private fun createContactReactionSpam(
        contacts: List<ContactModel>,
        serviceManager: ServiceManager,
    ) {
        val reactions = mutableListOf<DbEmojiReaction>()

        contacts.forEach { contactModel ->
            logger.info("Create ack/dec messages for contact with identity {}", contactModel.identity)
            repeat(SPAM_MESSAGES_WITH_REACTIONS_PER_CONVERSATION) {
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
        serviceManager: ServiceManager,
    ): List<DbEmojiReaction> {
        val userIdentity = serviceManager.userService.identity!!
        val messageModelFactory = serviceManager.databaseService.messageModelFactory

        val hasUserReactions = Random.nextBoolean()
        val hasContactReactions = Random.nextBoolean()
        val hasAckDec = (!hasContactReactions && !hasUserReactions) ||
            ((!hasContactReactions || !hasUserReactions) && Random.nextBoolean())

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
            contactModel,
        )
        messageModelFactory.create(message)
        return reactions.toDbReactions(message.id)
    }

    private fun createContactText(
        state: MessageState?,
        reactions: List<Pair<String, Set<String>>>,
    ): String {
        val stateText = state?.let { "State: $it" }
        val reactionTexts = reactions
            .map { (identity, reactions) -> "@[$identity]: ${reactions.joinToString(", ")}" }
        return (listOfNotNull(stateText) + reactionTexts).joinToString("\n")
    }

    private fun List<Pair<String, Set<String>>>.toDbReactions(messageId: Int): List<DbEmojiReaction> {
        return flatMap { (identity, reactions) ->
            reactions.map { reaction ->
                DbEmojiReaction(
                    messageId,
                    identity,
                    reaction,
                    Date(),
                )
            }
        }
    }

    private fun createReactions(identities: List<Identity>): List<Pair<Identity, Set<String>>> {
        val availableReactions = getReactionSequences(identities.size * 3)
        return identities.map { identity ->
            val numberOfReactions = Random.nextInt(1..3)
            identity to availableReactions.shuffled().take(numberOfReactions).toSet()
        }.filter { it.second.isNotEmpty() }
    }

    private fun isSpamChat(identifier: String?) = identifier?.startsWith(SPAM_CHATS_PREFIX) == true

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
        senderIdentity: Identity,
        userIdentity: Identity,
        groupMessageStates: Map<String, Any>,
        groupModel: GroupModel,
    ): GroupMessageModel = GroupMessageModel().apply {
        groupId = groupModel.id
        identity = senderIdentity
        this.groupMessageStates = groupMessageStates.toMap()
        enrichTextMessage(
            text,
            senderIdentity == userIdentity,
        )
    }

    private fun AbstractMessageModel.enrichTextMessage(
        text: String,
        isOutbox: Boolean,
        state: MessageState? = null,
    ) {
        val now = now()
        uid = UUID.randomUUID().toString()
        apiMessageId = MessageId.random().toString()
        this.isOutbox = isOutbox
        this.type = MessageType.TEXT
        bodyAndQuotedMessageId = text
        isRead = true
        this.state = state ?: if (isOutbox) {
            MessageState.DELIVERED
        } else {
            MessageState.READ
        }
        postedAt = now
        createdAt = now
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
        "\uD83E\uDEC6",
    ).shuffled().take(n)

    @JvmStatic
    @AnyThread
    fun createNonces(serviceManager: ServiceManager, fragmentManager: FragmentManager) {
        CoroutineScope(Dispatchers.Default).launch {
            val goOn = confirm(
                fragmentManager,
                "Generate $AMOUNT_OF_NONCES nonces for each scope ${NonceScope.CSP} and ${NonceScope.D2D}?",
            )
            if (!goOn) {
                return@launch
            }
            withGenericProgress(fragmentManager, "Generate random nonces") {
                val myIdentity = serviceManager.identityStore.getIdentity()!!
                createNonces(NonceScope.CSP, serviceManager.nonceFactory, myIdentity)
                createNonces(NonceScope.D2D, serviceManager.nonceFactory, myIdentity)
            }
        }
    }

    @WorkerThread
    private fun createNonces(scope: NonceScope, nonceFactory: NonceFactory, identity: Identity) {
        logger.info("Generate random nonces for scope {}", scope)
        val nonces = (0 until AMOUNT_OF_NONCES).asSequence()
            .map { nonceFactory.next(scope) }
            .map { it.hashNonce(identity) }
            .toList()
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

    private fun withGenericProgress(
        fragmentManager: FragmentManager,
        message: String,
        block: () -> Unit,
    ) {
        val dialog = GenericProgressDialog.newInstance(null, message)
        dialog.show(fragmentManager, "CONTENT_CREATOR_PROGRESS_DIALOG")
        try {
            block()
        } finally {
            dialog.dismiss()
        }
    }
}
