/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import ch.threema.app.ThreemaApplication
import ch.threema.app.asynctasks.AddContactRestrictionPolicy
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.ContactAvailable
import ch.threema.app.asynctasks.Failed
import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.gcBlake2b
import ch.threema.app.voip.groupcall.getSecureRandomBytes
import ch.threema.app.voip.groupcall.sfu.messages.Handshake
import ch.threema.app.voip.groupcall.sfu.messages.P2PMessageContent
import ch.threema.app.voip.groupcall.sfu.messages.P2POuterEnvelope
import ch.threema.app.voip.groupcall.sfu.messages.P2SMessage
import ch.threema.app.voip.groupcall.sfu.webrtc.ConnectionCtx
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import com.neilalexander.jnacl.NaCl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.webrtc.SurfaceViewRenderer

private val logger = LoggingUtil.getThreemaLogger("P2PHandshake")
private val participantLogger = LoggingUtil.getThreemaLogger("NormalRemoteParticipant")

@WorkerThread
class P2PHandshake private constructor(
    val sender: LocalParticipant,
    val receiverId: ParticipantId,
    private val call: GroupCall,
    initialState: HandshakeState,
) {
    private var handshakeState: HandshakeState = initialState
        set(value) {
            logger.info("Setting handshakeState={} with {}", value, receiverId)
            field = value
        }

    private val senderP2PContext: LocalP2PContext by lazy {
        val pckPrivate = getSecureRandomBytes(NaCl.SECRETKEYBYTES)
        val pcck = getSecureRandomBytes(ProtocolDefines.COOKIE_LEN)
        LocalP2PContext(sender, pckPrivate, pcck)
    }

    private val gcnhak: ByteArray by lazy {
        val sharedKey = call.dependencies.identityStore.calcSharedSecret(receiverContact.publicKey)
        gcBlake2b(NaCl.SECRETKEYBYTES, sharedKey, "nha", call.description.gckh)
    }
    private val pck: NaCl by lazy {
        NaCl(senderP2PContext.pckPrivate, receiverP2PContext.pckPublic)
    }

    private lateinit var receiverP2PContext: RemoteP2PContext
    private lateinit var receiverContact: ContactModel

    lateinit var p2pContexts: P2PContexts

    private val queuedMessages = mutableListOf<P2PMessageContent>()
    private val completedSignal: CompletableDeferred<Pair<P2PContexts, List<P2PMessageContent.MediaKey>>> =
        CompletableDeferred()

    val isDone: Boolean
        get() = handshakeState == HandshakeState.DONE
    val handshakeCompletedSignal: Deferred<Pair<P2PContexts, List<P2PMessageContent.MediaKey>>>
        get() = completedSignal

    init {
        GroupCallThreadUtil.assertDispatcherThread()

        if (initialState == HandshakeState.INIT) {
            sendMessage { createHello() }
            handshakeState = HandshakeState.AWAIT_EXISTING_PARTICIPANT_HELLO
        }
    }

    @WorkerThread
    fun cancel() {
        GroupCallThreadUtil.assertDispatcherThread()

        if (handshakeState != HandshakeState.DONE && handshakeState != HandshakeState.CANCELLED) {
            logger.info("Cancel handshake with {}", receiverId)
            handshakeState = HandshakeState.CANCELLED
            completedSignal.completeExceptionally(GroupCallException("Handshake cancelled"))
        }
    }

    @WorkerThread
    fun queueRekey(message: P2PMessageContent.MediaKey) {
        GroupCallThreadUtil.assertDispatcherThread()

        when (handshakeState) {
            HandshakeState.AWAIT_AUTH -> {
                logger.debug("Queuing Rekey to be sent once authenticated")
                queuedMessages.add(message)
            }

            HandshakeState.DONE -> {
                // Note: This is considered unreachable
                throw Error("Cannot queue Rekey, handshake already done!")
            }

            HandshakeState.CANCELLED -> logger.debug("Ignoring Rekey because handshake is cancelled")
            else -> logger.debug("Ignoring Rekey because we have not sent a Handshake.*Auth yet")
        }
    }

    @WorkerThread
    fun handleMessage(message: P2POuterEnvelope) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Handle message from {} in state={}", message.senderId, handshakeState)
        if (sender.id == message.receiverId && receiverId == message.senderId) {
            try {
                when (handshakeState) {
                    HandshakeState.INIT -> handleUnexpectedMessage(message)
                    HandshakeState.AWAIT_EXISTING_PARTICIPANT_HELLO -> handleMessageInAwaitEpHelloState(
                        message
                    )

                    HandshakeState.AWAIT_NEW_PARTICIPANT_HELLO -> handleMessageInAwaitNpHelloState(
                        message
                    )

                    HandshakeState.AWAIT_AUTH -> handleMessageInAwaitAuthState(message)
                    HandshakeState.DONE, HandshakeState.CANCELLED -> handleUnexpectedMessage(message)
                }
            } catch (e: GroupCallException) {
                completedSignal.completeExceptionally(e)
            }
        } else {
            logger.warn(
                "Ignore unexpected message to {} from {} (Handshake between {} and {})",
                message.senderId, message.receiverId, sender.id, receiverId
            )
        }
    }

    @WorkerThread
    private fun sendMessage(provider: ConnectionCtx.P2SMessageProvider) {
        GroupCallThreadUtil.assertDispatcherThread()

        call.context.sendMessageToSfu(provider)
    }

    @WorkerThread
    private fun createHello(): P2POuterEnvelope {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Create Hello from {} to {}", sender.id, receiverId)
        val hello = Handshake.Hello(
            call.dependencies.identityStore.identity,
            call.dependencies.identityStore.publicNickname
                ?: call.dependencies.identityStore.identity,
            senderP2PContext.pckPublic,
            senderP2PContext.pcck
        )
        val encryptedData = call.description.encryptWithGchk(hello.getEnvelopeBytes())
        return P2POuterEnvelope(sender.id, receiverId, encryptedData)
    }

    @WorkerThread
    private fun createAuth(): P2POuterEnvelope {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Create Auth from {} to {}", sender.id, receiverId)
        val innerNonce = getSecureRandomBytes(NaCl.NONCEBYTES)
        val auth = Handshake.Auth(
            receiverP2PContext.pckPublic,
            receiverP2PContext.pcck,
            call.context.connectionCtx.pcmk.all().map { P2PMessageContent.MediaKey.fromState(it) },
        )
        val encryptedInnerData =
            NaCl.symmetricEncryptData(auth.getEnvelopeBytes(), gcnhak, innerNonce)
        val encryptedAuthData = pck.encrypt(
            innerNonce + encryptedInnerData,
            senderP2PContext.nextPcckNonce()
        )
        return P2POuterEnvelope(sender.id, receiverId, encryptedAuthData)
    }

    @WorkerThread
    private fun createRemoteParticipant(participantId: ParticipantId): NormalRemoteParticipant {
        GroupCallThreadUtil.assertDispatcherThread()

        return object : NormalRemoteParticipant(participantId, receiverContact) {
            override val type = "NormalRemoteParticipant"

            @UiThread
            override fun subscribeCamera(
                renderer: SurfaceViewRenderer,
                width: Int,
                height: Int,
                fps: Int,
            ): DetachSinkFn {
                call.context.sendMessageToSfu {
                    P2SMessage.SubscribeParticipantCamera(
                        participantId,
                        width,
                        height,
                        fps
                    )
                }

                participantLogger.trace("Starting to render remote camera video")
                val videoContext = remoteCtx?.cameraVideoContext
                if (videoContext == null) {
                    participantLogger.warn("No video context for {}", participantId)
                }
                return remoteCtx?.cameraVideoContext?.renderTo(renderer) ?: {}
            }

            @UiThread
            override fun unsubscribeCamera() {
                participantLogger.trace("Unsubscribe camera participant={}", participantId.id)
                call.context.sendMessageToSfu {
                    P2SMessage.UnsubscribeParticipantCamera(
                        participantId
                    )
                }
            }
        }
    }

    @WorkerThread
    private fun handleUnexpectedMessage(message: P2POuterEnvelope) {
        logger.warn(
            "Received unexpected message from {} while in handshake state '{}'",
            message.senderId,
            handshakeState
        )
    }

    @WorkerThread
    private fun handleMessageInAwaitEpHelloState(message: P2POuterEnvelope) {
        GroupCallThreadUtil.assertDispatcherThread()

        withValidHello(message) {
            initReceiver(it)
            sendMessage { createAuth() }
            handshakeState = HandshakeState.AWAIT_AUTH
        }
    }

    @WorkerThread
    private fun handleMessageInAwaitNpHelloState(message: P2POuterEnvelope) {
        GroupCallThreadUtil.assertDispatcherThread()

        withValidHello(message) {
            initReceiver(it)
            sendMessage { createHello() }
            sendMessage { createAuth() }
            handshakeState = HandshakeState.AWAIT_AUTH
        }
    }

    @WorkerThread
    private fun withValidHello(message: P2POuterEnvelope, block: (Handshake.Hello) -> Unit) {
        GroupCallThreadUtil.assertDispatcherThread()

        val hello = decodeHelloMessage(message)
        if (hello != null && isValidHello(hello)) {
            block(hello)
        } else {
            logger.warn("Received unsuitable hello message")
        }
    }

    @WorkerThread
    private fun isValidHello(hello: Handshake.Hello): Boolean {
        return isGroupMember(hello.identity)
            && hello.pck.size == NaCl.PUBLICKEYBYTES
            && hello.pcck.size == ProtocolDefines.COOKIE_LEN
            && !hello.pck.contentEquals(senderP2PContext.pckPublic)
            && !hello.pcck.contentEquals(senderP2PContext.pcck)
    }

    @WorkerThread
    private fun isGroupMember(identity: String): Boolean {
        val groupService = call.dependencies.groupService
        return groupService.getById(call.description.groupId.id)?.let {
            groupService.isGroupMember(it, identity)
        } ?: false
    }

    @WorkerThread
    private fun decodeHelloMessage(message: P2POuterEnvelope): Handshake.Hello? {
        GroupCallThreadUtil.assertDispatcherThread()

        return try {
            decryptHelloData(message.encryptedData)?.let {
                Handshake.Hello.decode(it)
            }
        } catch (e: Exception) {
            logger.warn("Error decoding Hello message", e)
            null
        }
    }

    @WorkerThread
    private fun decryptHelloData(encryptedData: ByteArray): ByteArray? {
        GroupCallThreadUtil.assertDispatcherThread()

        return call.description.decryptByGchk(encryptedData).also {
            if (it == null) {
                logger.warn("Could not decrypt hello")
            }
        }
    }

    @WorkerThread
    private fun initReceiver(hello: Handshake.Hello) {
        GroupCallThreadUtil.assertDispatcherThread()

        try {
            receiverContact = getOrCreateContact(hello.identity)
            val receiverParticipant = createRemoteParticipant(receiverId)
            receiverP2PContext = RemoteP2PContext(receiverParticipant, hello.pck, hello.pcck)
        } catch (e: Exception) {
            throw GroupCallException("Could not init receiver", e)
        }
    }

    @WorkerThread
    private fun getOrCreateContact(identity: String): ContactModel {
        // Check if the contact already exists
        call.dependencies.contactService.getByIdentity(identity)?.let {
            return it
        }

        val result = BasicAddOrUpdateContactBackgroundTask(
            identity = identity,
            AcquaintanceLevel.GROUP,
            myIdentity = sender.identity,
            call.dependencies.apiConnector,
            call.dependencies.contactModelRepository,
            AddContactRestrictionPolicy.CHECK,
            context = ThreemaApplication.getAppContext(),
            null
        ).runSynchronously()

        when (result) {
            is ContactAvailable -> Unit
            is Failed -> {
                logger.error("Could not create contact: {}", result.message)
                throw IllegalStateException("Could not create contact")
            }
        }

        return call.dependencies.contactService.getByIdentity(identity)
            ?: throw IllegalStateException("Contact must exist after creating it")
    }

    @WorkerThread
    private fun handleMessageInAwaitAuthState(message: P2POuterEnvelope) {
        GroupCallThreadUtil.assertDispatcherThread()

        decryptAuthMessage(message.encryptedData)
            ?.let { decodeAuthMessage(it) }
            ?.let { auth ->
                if (!hasValidRepeatedAuthFeatures(auth)) {
                    logger.warn("Invalid repeated auth features")
                } else {
                    completeHandshake(auth.mediaKeys)
                }
            }
    }

    @WorkerThread
    private fun hasValidRepeatedAuthFeatures(auth: Handshake.Auth): Boolean {
        return auth.pck.contentEquals(senderP2PContext.pckPublic) && auth.pcck.contentEquals(
            senderP2PContext.pcck
        )
    }

    @WorkerThread
    private fun decodeAuthMessage(decryptedData: ByteArray): Handshake.Auth? {
        GroupCallThreadUtil.assertDispatcherThread()

        return try {
            Handshake.Auth.decode(decryptedData)
        } catch (e: Exception) {
            logger.error("Could not decode decrypted auth message", e)
            null
        }
    }

    @WorkerThread
    private fun decryptAuthMessage(encryptedData: ByteArray): ByteArray? {
        GroupCallThreadUtil.assertDispatcherThread()

        return try {
            val decryptedOuterData = pck.decrypt(
                encryptedData, receiverP2PContext.nextPcckNonce()
            )
            decryptedOuterData?.let { decryptAuthMessageInnerData(it) }
        } catch (e: Exception) {
            logger.error("Could not decrypt auth message", e)
            null
        }
    }

    @WorkerThread
    private fun decryptAuthMessageInnerData(decryptedOuterData: ByteArray): ByteArray? {
        GroupCallThreadUtil.assertDispatcherThread()

        val nonce = decryptedOuterData.copyOfRange(0, NaCl.NONCEBYTES)
        val encryptedInnerData =
            decryptedOuterData.copyOfRange(NaCl.NONCEBYTES, decryptedOuterData.size)
        return NaCl.symmetricDecryptData(encryptedInnerData, gcnhak, nonce)
    }

    @WorkerThread
    private fun completeHandshake(mediaKeys: List<P2PMessageContent.MediaKey>) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Complete handshake with {}", receiverId)
        p2pContexts = P2PContexts(senderP2PContext, receiverP2PContext)
        handshakeState = HandshakeState.DONE
        queuedMessages.forEach {
            logger.debug("Sending queued message {}", it.type)
            sendMessage { p2pContexts.createP2PMessage(it) }
        }
        queuedMessages.clear()
        completedSignal.complete(Pair(p2pContexts, mediaKeys))
    }

    @AnyThread
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is P2PHandshake) return false

        if (sender != other.sender) return false
        if (receiverId != other.receiverId) return false

        return true
    }

    @AnyThread
    override fun hashCode(): Int {
        var result = sender.hashCode()
        result = 31 * result + receiverId.hashCode()
        return result
    }

    @WorkerThread
    internal class P2PHandshakeFactory(
        private val sender: LocalParticipant,
        private val call: GroupCall,
    ) {
        /**
         * This will initiate a Handshake with an existing call participant.
         * That means a `Hello`-message will be sent to that participant immediately and
         * the handshake state will be set to 'await-ep-hello'
         */
        @WorkerThread
        fun initHandshakeWithExistingParticipant(receiverId: ParticipantId): P2PHandshake {
            GroupCallThreadUtil.assertDispatcherThread()

            logger.info("Init handshake with existing participant {}", receiverId)
            return P2PHandshake(
                sender,
                receiverId,
                call,
                HandshakeState.INIT
            )
        }

        @WorkerThread
        fun createForNewParticipant(receiverId: ParticipantId): P2PHandshake {
            GroupCallThreadUtil.assertDispatcherThread()

            logger.info("Create handshake for new participant {}", receiverId)
            return P2PHandshake(
                sender,
                receiverId,
                call,
                HandshakeState.AWAIT_NEW_PARTICIPANT_HELLO
            )
        }
    }

    enum class HandshakeState {
        INIT,
        AWAIT_NEW_PARTICIPANT_HELLO,
        AWAIT_EXISTING_PARTICIPANT_HELLO,
        AWAIT_AUTH,
        DONE,
        CANCELLED
    }
}
