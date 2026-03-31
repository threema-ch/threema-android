package ch.threema.app.usecases.contacts

import ch.threema.app.listeners.ContactListener
import ch.threema.app.listeners.ProfileListener
import ch.threema.app.managers.ListenerManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.MentionNameData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.toIdentityOrNull
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

private val logger = getThreemaLogger("WatchAllMentionNamesUseCase")

class WatchAllMentionNamesUseCase(
    private val contactModelRepository: ContactModelRepository,
    private val identityStore: IdentityStore,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     *  Creates a *cold* [Flow] that emits the latest collection of [MentionNameData] from every contact, also including the users own profile.
     *
     *  In the edge-case that no **own** identity exists, the flow will not include a [MentionNameData.Me] instance.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current values because [watchOwnMentionNameData] and [watchContactsMentionNameData] do so.
     *
     *  ##### Overflow strategy
     *  See [watchOwnMentionNameData] and [watchContactsMentionNameData]
     *
     *  ##### Error strategy
     *  See [watchOwnMentionNameData] and [watchContactsMentionNameData]
     */
    fun call(): Flow<List<MentionNameData>> =
        combine(
            watchContactsMentionNameData(),
            watchOwnMentionNameData(),
        ) { contactsMentionNameData: List<MentionNameData>, ownMentionNameData: MentionNameData.Me? ->
            if (ownMentionNameData != null) {
                contactsMentionNameData + ownMentionNameData
            } else {
                contactsMentionNameData
            }
        }

    /**
     *  Creates a *cold* [Flow] that emits the latest [MentionNameData.Me] from the users profile. If no own identity exists, this flow will emit
     *  `null`.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current value (or `null`).
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception that's not occurring inside the [ProfileListener] will flow downstream.
     */
    private fun watchOwnMentionNameData(): Flow<MentionNameData.Me?> = callbackFlow {
        // Direct emit promise
        val currentMentionNameData = getOwnMentionNameDataOrNull(
            nickname = identityStore::getPublicNickname,
        )
        trySend(currentMentionNameData)
            .onClosed {
                // Collection already ended
                return@callbackFlow
            }

        val listener = object : ProfileListener {
            override fun onAvatarChanged(triggerSource: TriggerSource) {}

            override fun onNicknameChanged(newNickname: String?) {
                val updatedMentionNameData = getOwnMentionNameDataOrNull(
                    nickname = { newNickname },
                )
                trySend(updatedMentionNameData)
                    .onClosed { throwable ->
                        logger.error("Tried to send a new value after channel was closed", throwable)
                    }
            }
        }
        ListenerManager.profileListeners.add(listener)
        awaitClose {
            ListenerManager.profileListeners.remove(listener)
        }
    }
        .buffer(capacity = CONFLATED)

    private fun getOwnMentionNameDataOrNull(nickname: () -> String?): MentionNameData.Me? =
        identityStore.getIdentity()
            ?.let { ownIdentity ->
                MentionNameData.Me(
                    identity = ownIdentity,
                    nickname = nickname(),
                )
            }

    /**
     *  Creates a *cold* [Flow] that emits the most recent collection of [MentionNameData.Contact] from all contacts.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current values.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception that's not occurring inside the [ContactListener] will flow downstream.
     */
    private fun watchContactsMentionNameData(): Flow<List<MentionNameData.Contact>> = callbackFlow {
        // Direct emit promise
        val currentContactsMentionNameData = getCurrentContactsMentionNameData()
        trySend(currentContactsMentionNameData)
            .onClosed {
                // Collection already ended
                return@callbackFlow
            }

        fun trySendCurrent() {
            val currentContactsMentionNameData = getCurrentContactsMentionNameData()
            trySend(currentContactsMentionNameData)
                .onClosed { throwable ->
                    logger.error("Tried to send a new value after channel was closed", throwable)
                }
        }

        val contactListener = object : ContactListener {
            override fun onNew(identity: String) {
                trySendCurrent()
            }

            override fun onModified(identity: String) {
                trySendCurrent()
            }

            override fun onRemoved(identity: String) {
                trySendCurrent()
            }

            override fun onAvatarChanged(identity: String) {
                trySendCurrent()
            }
        }
        ListenerManager.contactListeners.add(contactListener)
        awaitClose {
            ListenerManager.contactListeners.remove(contactListener)
        }
    }
        .buffer(capacity = CONFLATED)
        .flowOn(context = dispatcherProvider.io)

    private fun getCurrentContactsMentionNameData(): List<MentionNameData.Contact> =
        contactModelRepository
            .getAll()
            .mapNotNull { contactModel ->
                contactModel.data?.let { contactModelData ->
                    val identity = contactModelData.identity.toIdentityOrNull()
                        ?: return@mapNotNull null
                    MentionNameData.Contact(
                        identity = identity,
                        nickname = contactModelData.nickname,
                        firstname = contactModelData.firstName,
                        lastname = contactModelData.lastName,
                    )
                }
            }
}
