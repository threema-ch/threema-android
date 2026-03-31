package ch.threema.app.usecases.conversations

import ch.threema.app.listeners.ContactListener
import ch.threema.app.listeners.ContactSettingsListener
import ch.threema.app.listeners.GroupListener
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.routines.SynchronizeContactsRoutine
import ch.threema.app.services.ConversationService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private val logger = getThreemaLogger("WatchAvatarIterationsUseCase")

/**
 *  This use-case produces a map that can be used to identify when to invalidate a **conversation** receivers avatar.
 *
 *  #### Why this use-case exists
 *
 *  See [AvatarIteration] definition
 *
 *  #### Resulting map
 *  - It can *not* be used for a list of all contacts, as it focuses around receivers with conversations
 *  - Receivers whose conversation gets deleted will *not* be removed from this map while collecting
 *  - Because of a limitation by the [SynchronizeContactsListener] we have to increment the iteration value of *all* contact receivers if a contact
 *  sync finishes
 *  - Distribution-list receiver iteration values will always stay at [AvatarIteration.initial]
 */
class WatchAvatarIterationsUseCase(
    private val conversationService: ConversationService,
    private val groupModelRepository: GroupModelRepository,
) {
    /**
     *  Creates a *cold* [Flow] associating a receiver with its latest avatar iteration value.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current initial iterations map. If the current initial iterations could not be determined
     *  due to an internal error, a fallback value is emitted.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  In the unlikely case that we fail to determine current initial iterations map, an empty map will be emitted as a fallback value.
     */
    fun call(): Flow<Map<ReceiverIdentifier, AvatarIteration>> = callbackFlow {
        // Map mutations happen synchronized
        val iterationsMap = getInitialIterationsMapOrEmpty().toMutableMap()

        // Direct emit promise
        trySend(iterationsMap.toMap())
            .onClosed {
                // Collection already ended
                return@callbackFlow
            }

        fun updateIterationsAndSend(update: (MutableMap<ReceiverIdentifier, AvatarIteration>) -> Unit) {
            synchronized(iterationsMap) {
                update(iterationsMap)
                val iterationsSnapshot = iterationsMap.toMap()
                trySend(iterationsSnapshot)
                    .onClosed { throwable ->
                        logger.error("Tried to send a new value after channel was closed", throwable)
                    }
            }
        }

        // Catching both "The contact updated its profile picture" and "The user set a profile picture for his contact in Threema" event
        val contactListener = object : ContactListener {
            override fun onAvatarChanged(identity: String) {
                val contactReceiverIdentifier = ContactReceiverIdentifier(identity)
                updateIterationsAndSend { iterations ->
                    addNewReceiversOfType(
                        iterations = iterations,
                        typeFilter = ConversationModel::isContactConversation,
                    )
                    val currentIteration = iterations[contactReceiverIdentifier] ?: AvatarIteration.initial
                    iterations[contactReceiverIdentifier] = currentIteration.inc()
                }
            }
        }

        // Catching "The user set a profile picture for his synced contact via Android address book" event
        val synchronizeContactsListener = object : SynchronizeContactsListener {
            override fun onFinished(finishedRoutine: SynchronizeContactsRoutine?) {
                updateIterationsAndSend { iterations ->
                    addNewReceiversOfType(
                        iterations = iterations,
                        typeFilter = ConversationModel::isContactConversation,
                    )
                    // We have to increment all contact conversation iteration values, as we don't know which contacts were affected by this sync
                    incrementAllContactReceiverIterations(iterations)
                }
            }
        }

        // Catching "The user changed the display settings for other contacts avatars"
        val contactSettingsListener = object : ContactSettingsListener {
            override fun onIsDefaultContactPictureColoredChanged(isColored: Boolean) {
                onRelevantSettingChanged()
            }

            override fun onShowContactDefinedAvatarsChanged(shouldShow: Boolean) {
                onRelevantSettingChanged()
            }

            private fun onRelevantSettingChanged() {
                updateIterationsAndSend { iterations ->
                    addNewReceiversOfType(
                        iterations = iterations,
                        typeFilter = ConversationModel::isContactConversation,
                    )
                    incrementAllContactReceiverIterations(iterations)
                }
            }
        }

        // Catching "The group picture was changed by the admin" event
        val groupListener = object : GroupListener {
            override fun onUpdatePhoto(groupIdentity: GroupIdentity) {
                val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity) ?: return
                val groupModelData = groupModel.data ?: return
                val groupReceiverIdentifier = GroupReceiverIdentifier(
                    groupDatabaseId = groupModel.getDatabaseId(),
                    groupCreatorIdentity = groupModelData.groupIdentity.creatorIdentity,
                    groupApiId = groupModelData.groupIdentity.groupId,
                )
                updateIterationsAndSend { iterations ->
                    addNewReceiversOfType(
                        iterations = iterations,
                        typeFilter = ConversationModel::isGroupConversation,
                    )
                    val currentIteration: AvatarIteration = iterations[groupReceiverIdentifier] ?: AvatarIteration.initial
                    iterations[groupReceiverIdentifier] = currentIteration.inc()
                }
            }
        }

        ListenerManager.contactListeners.add(contactListener)
        ListenerManager.synchronizeContactsListeners.add(synchronizeContactsListener)
        ListenerManager.groupListeners.add(groupListener)
        ListenerManager.contactSettingsListeners.add(contactSettingsListener)
        awaitClose {
            ListenerManager.contactListeners.remove(contactListener)
            ListenerManager.synchronizeContactsListeners.remove(synchronizeContactsListener)
            ListenerManager.groupListeners.remove(groupListener)
            ListenerManager.contactSettingsListeners.remove(contactSettingsListener)
        }
    }
        .buffer(capacity = CONFLATED)

    /**
     *  @return The initial iterations map, or an empty map in case of any internal error
     */
    private fun getInitialIterationsMapOrEmpty(): Map<ReceiverIdentifier, AvatarIteration> {
        val conversations = runCatching {
            conversationService.getAll(
                /* forceReloadFromDatabase = */
                false,
            )
        }.getOrElse { throwable ->
            logger.error("Failed to get the current conversations", throwable)
            return emptyMap()
        }
        return conversations.associate { conversationModel ->
            conversationModel.receiverModel.identifier to AvatarIteration.initial
        }
    }

    /**
     *  Adds all missing receivers to the [iterations] map that pass the [typeFilter].
     *
     *  Mutates [iterations]
     */
    private fun addNewReceiversOfType(
        iterations: MutableMap<ReceiverIdentifier, AvatarIteration>,
        typeFilter: (ConversationModel) -> Boolean,
    ) {
        conversationService
            .getAll(
                /* forceReloadFromDatabase = */
                false,
            )
            .filter(typeFilter)
            .forEach { conversationModel ->
                iterations.putIfAbsent(
                    conversationModel.receiverModel.identifier,
                    AvatarIteration.initial,
                )
            }
    }

    private fun incrementAllContactReceiverIterations(iterations: MutableMap<ReceiverIdentifier, AvatarIteration>) {
        iterations.entries.forEach { entry ->
            if (entry.key is ContactReceiverIdentifier) {
                entry.setValue(entry.value.inc())
            }
        }
    }
}

/**
 *  Avatar assets are identified by a local filename depending on the receiver's identifier only. If an avatar is changed, the path will still be the
 *  same as before. There is no unique id per avatar iteration. So this [value] can be used to invalidate composables. The actual integer value has no
 *  further meaning to it.
 */
@JvmInline
value class AvatarIteration private constructor(val value: Int) {

    operator fun inc(): AvatarIteration = AvatarIteration(value + 1)

    companion object {
        val initial = AvatarIteration(0)
    }
}
