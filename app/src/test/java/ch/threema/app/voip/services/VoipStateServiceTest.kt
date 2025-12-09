/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.voip.services

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import ch.threema.app.ThreemaApplication
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.services.ContactService
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.app.utils.DoNotDisturbUtil
import ch.threema.app.utils.LogUtil
import ch.threema.app.voip.listeners.VoipCallEventListener
import ch.threema.app.voip.listeners.VoipMessageListener
import ch.threema.app.voip.managers.VoipListenerManager
import ch.threema.app.voip.util.VoipUtil
import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData.OfferData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiFunction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Rule

class VoipStateServiceTest {

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<DoNotDisturbUtil> { mockk() }
    }

    // Mocks
    private lateinit var mockContext: Context
    private lateinit var mockAudioManager: AudioManager
    private lateinit var mockContactService: ContactService
    private lateinit var contactMessageReceiver: ContactMessageReceiver
    private lateinit var mockNotificationPreferenceService: NotificationPreferenceService
    private lateinit var mockLifetimeService: LifetimeService

    // Service
    private lateinit var service: VoipStateService

    @BeforeTest
    fun setUp() {
        // Mock context
        mockAudioManager = mockk(relaxed = true)
        mockContext = mockk {
            every { getSystemService(Context.AUDIO_SERVICE) } returns mockAudioManager
            every { getSystemService(Context.NOTIFICATION_SERVICE) } returns null
        }

        // Mock services
        mockContactService = mockk(relaxed = true)
        mockNotificationPreferenceService = mockk()
        mockLifetimeService = mockk(relaxed = true)

        // Mock contact message receiver
        contactMessageReceiver = mockk(relaxed = true) {
            every { hasVoipCallStatus(any(), any()) } returns false
        }

        // Set up return values for contact service
        every { mockContactService.getByIdentity("INVALID") } returns null
        every { mockContactService.getByIdentity("AAAAAAAA") } returns ContactModel.create("AAAAAAAA", ByteArray(32))
        every { mockContactService.getByIdentity("BBBBBBBB") } returns ContactModel.create("BBBBBBBB", ByteArray(32))
        every { mockContactService.createReceiver(any<ContactModel>()) } returns contactMessageReceiver

        // Static mocks
        mockkStatic(LogUtil::class)
        mockkStatic(SystemClock::class)
        mockkStatic(VoipUtil::class)
        every { VoipUtil.sendVoipBroadcast(any(), any()) } just runs

        // Clear message listeners (used by tests)
        VoipListenerManager.messageListener.clear()
        VoipListenerManager.callEventListener.clear()

        // Instantiate service
        service = VoipStateService(
            mockContactService,
            mockNotificationPreferenceService,
            mockLifetimeService,
            mockContext,
        )

        // TODO(ANDR-4219): We have to mock ServiceManager, as it is sneakily referenced somewhere deep down the stack. This needs to be cleaned up.
        mockkObject(ThreemaApplication)
        every { ThreemaApplication.getServiceManager() } returns null
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(LogUtil::class)
        unmockkStatic(SystemClock::class)
        unmockkStatic(VoipUtil::class)
        unmockkObject(ThreemaApplication)
    }

    /**
     * @noinspection deprecation
     */
    @Test
    fun callCounterIncrement() {
        // Initially at 0
        assertEquals(0, service.callState.incomingCallCounter)

        // Increment when ringing
        service.setStateRinging(1)
        assertEquals(1, service.callState.incomingCallCounter)

        // Don't increment when state didn't change
        service.setStateRinging(1)
        assertEquals(1, service.callState.incomingCallCounter)

        // Increment again when state changed
        service.setStateIdle()
        assertEquals(1, service.callState.incomingCallCounter)
        service.setStateRinging(1)
        assertEquals(2, service.callState.incomingCallCounter)
    }

    /**
     * Test the entire state lifecycle.
     */
    @Test
    fun callStateSetting() {
        service.setStateIdle()
        assertTrue(service.callState.isIdle)

        service.setStateRinging(1)
        assertTrue(service.callState.isRinging)

        service.setStateInitializing(1)
        assertTrue(service.callState.isInitializing)

        service.setStateCalling(1)
        assertTrue(service.callState.isCalling)

        service.setStateDisconnecting(1)
        assertTrue(service.callState.isDisconnecting)

        service.setStateIdle()
        assertTrue(service.callState.isIdle)
    }

    @Test
    fun callDuration() {
        // Initially null
        assertNull(service.callDuration)

        // Stays null until call is started
        service.setStateRinging(1)
        service.setStateInitializing(1)
        assertNull(service.callDuration)

        // Counts up from 0
        every { SystemClock.elapsedRealtime() } returns 1000L
        service.setStateCalling(1)
        assertEquals(0, service.callDuration)
        every { SystemClock.elapsedRealtime() } returns 3100L
        assertEquals(2, service.callDuration)
        every { SystemClock.elapsedRealtime() } returns 13100L
        assertEquals(12, service.callDuration)

        // Resets on disconnect
        service.setStateDisconnecting(1)
        assertNull(service.callDuration)
        every { SystemClock.elapsedRealtime() } returns 15000L
        service.setStateCalling(1)
        assertEquals(0, service.callDuration)
    }

    @Test
    fun initiatorFlag() {
        // Flag is null initially
        assertNull(service.isInitiator())

        // It can be set by external code
        service.setInitiator(true)
        assertTrue(service.isInitiator()!!)

        // When the state machine goes back to idle, the initiator flag must be reset
        service.setStateRinging(1)
        assertTrue(service.isInitiator()!!)
        service.setStateIdle()
        assertNull(service.isInitiator())
    }

    /**
     * Offers with no data are ignored and do not change the state.
     */
    @Test
    fun handleOfferNullData() {
        // Offer message with no data
        val msg = VoipCallOfferMessage()
        msg.fromIdentity = "AAAAAAAA"
        msg.toIdentity = "BBBBBBBB"

        // Handling should not change the state
        assertTrue(service.callState.isIdle)
        assertFalse(service.handleCallOffer(msg))
        assertTrue(service.callState.isIdle)
    }

    /**
     * Offers with no data are ignored and do not change the state.
     */
    @Test
    fun handleOfferInvalidContact() {
        // Offer message with no data
        val msg = this.createOfferMessage()
        msg.fromIdentity = "INVALID"

        // Handling should not change the state
        assertTrue(service.callState.isIdle)
        assertFalse(service.handleCallOffer(msg))
        assertTrue(service.callState.isIdle)
    }

    /**
     * If a call is already active, an offer will be rejected.
     */
    @Test
    @Throws(ThreemaException::class)
    fun handleOfferBusy() {
        val offer = this.createOfferMessage()

        val states = arrayOf("ringing", "initializing", "calling", "disconnecting")
        val callId: Long = 1
        for (state in states) {
            val reasonSlot = slot<Byte>()

            // Partially mock service
            val serviceSpy = spyk(service)

            // Handle offer in non-idle state
            when (state) {
                "ringing" -> serviceSpy.setStateRinging(callId)
                "initializing" -> serviceSpy.setStateInitializing(callId)
                "calling" -> serviceSpy.setStateCalling(callId)
                "disconnecting" -> serviceSpy.setStateDisconnecting(callId)
                else -> // Not supported
                    fail("Unsupported state: $state")
            }

            assertTrue(serviceSpy.handleCallOffer(offer))

            // Capture reject reason
            verify(exactly = 1) {
                serviceSpy.sendRejectCallAnswerMessage(
                    any(),
                    any(),
                    capture(reasonSlot),
                    any(),
                )
            }

            assertEquals(
                VoipCallAnswerData.RejectReason.BUSY,
                reasonSlot.captured,
            )
        }
    }

    /**
     * Create an offer message.
     */
    private fun createOfferMessage(callId: Long? = null): VoipCallOfferMessage {
        val msg = VoipCallOfferMessage()
        msg.fromIdentity = "AAAAAAAA"
        msg.toIdentity = "BBBBBBBB"
        val msgData = VoipCallOfferData()
        val data = OfferData()
            .setSdpType("offer")
            .setSdp("mocked")
        msgData.setOfferData(data)
        if (callId != null) {
            msgData.setCallId(callId)
        }
        msg.data = msgData
        return msg
    }

    /**
     * Reject a call while another call is active.
     * The call ID in the reject message should correspond to the incoming call,
     * not to the current call.
     */
    @Test
    @Throws(ThreemaException::class)
    fun rejectCallWhileCalling() {
        val currentCallId: Long = 1
        val interruptingCallId: Long = 2

        // Initially, no call ID
        assertEquals(0L, service.callState.callId)

        // Start call
        service.setStateInitializing(currentCallId)
        assertEquals(currentCallId, service.callState.callId)
        service.setStateCalling(currentCallId)
        assertEquals(currentCallId, service.callState.callId)

        // Partially mock service
        val serviceSpy = spyk(service)

        // Incoming offer
        val msg = this.createOfferMessage(interruptingCallId)
        assertTrue(serviceSpy.handleCallOffer(msg))

        // Capture reject call ID
        val callIdSlot = slot<Long>()
        verify(exactly = 1) {
            serviceSpy.sendRejectCallAnswerMessage(
                any(),
                capture(callIdSlot),
                any(),
                any(),
            )
        }
        assertEquals(interruptingCallId, callIdSlot.captured)
    }

    /**
     * The call ID in an answer message should be validated.
     */
    @Test
    fun validateCallIdAnswer() {
        // Detect message handling
        val answerHandled = AtomicBoolean(false)
        VoipListenerManager.messageListener.add(object : VoipMessageListener {
            override fun onAnswer(identity: Identity, data: VoipCallAnswerData) {
                answerHandled.set(true)
            }

            override fun handle(identity: Identity): Boolean {
                return true
            }
        })

        // Create answer
        val answer = VoipCallAnswerMessage()
        answer.fromIdentity = "AAAAAAAA"
        answer.toIdentity = "BBBBBBBB"
        val msgData = VoipCallAnswerData()
        msgData.setAction(VoipCallAnswerData.Action.REJECT)
        msgData.setRejectReason(VoipCallAnswerData.RejectReason.UNKNOWN)

        // Test function (local call ID is always 1)
        val testProcessAnswer =
            BiFunction<Int, Boolean, Void?> { callId: Int, shouldBeHandled: Boolean ->
                // Outgoing call
                service.setInitiator(true)

                // Set current callId to 1
                service.setStateInitializing(1)

                // Set call ID of incoming answer
                msgData.setCallId(callId.toLong())
                answer.data = msgData

                // Handle and assert
                answerHandled.set(false)
                service.handleCallAnswer(answer)
                if (shouldBeHandled) {
                    assertTrue(answerHandled.get(), "Answer should have been handled")
                } else {
                    assertFalse(answerHandled.get(), "Answer should not have been handled")
                }

                // Reset
                service.setStateIdle()
                null
            }

        // Process answer with valid call ID
        testProcessAnswer.apply(1, true)

        // Do not process answer with invalid call ID
        testProcessAnswer.apply(2, false)

        // Process answer with missing call ID (accepted for backwards compatibility)
        testProcessAnswer.apply(0, true)
    }

    /**
     * The call ID in a hangup message should be validated.
     */
    @Test
    fun validateCallIdHangup() {
        // Mock call notifications
        val service = spyk(service)
        every { service.cancelCallNotification(any(), any()) } just runs

        // Create hangup
        val msg = VoipCallHangupMessage()
        msg.fromIdentity = "AAAAAAAA"
        msg.toIdentity = "BBBBBBBB"

        // Process hangup with valid call ID
        service.setStateInitializing(1)
        service.setStateCalling(1)
        msg.data = VoipCallHangupData().setCallId(1)
        service.handleRemoteCallHangup(msg)
        assertTrue(service.callState.isIdle, "Hangup should have been handled")

        // Process hangup with invalid call ID
        service.setStateInitializing(1)
        service.setStateCalling(1)
        msg.data = VoipCallHangupData().setCallId(2)
        service.handleRemoteCallHangup(msg)
        assertTrue(service.callState.isCalling, "Hangup should not have been handled")

        // Process hangup with missing call ID
        service.setStateInitializing(1)
        service.setStateCalling(1)
        // As callee
        service.setInitiator(false)
        msg.data = VoipCallHangupData()
        service.handleRemoteCallHangup(msg)
        assertTrue(service.callState.isCalling, "Hangup should not have been handled")
        // As caller
        service.setInitiator(true)
        msg.data = VoipCallHangupData()
        service.handleRemoteCallHangup(msg)
        assertTrue(service.callState.isIdle, "Hangup should have been handled")
    }

    /**
     * Handle hangup message with unknown call id as missed call
     */
    @Test
    fun handleMissedCall() {
        // Create hangup
        val msg = VoipCallHangupMessage()
        msg.fromIdentity = "AAAAAAAA"
        msg.toIdentity = "BBBBBBBB"

        // The call id that belongs to a past call (no missed call!)
        val pastCallId: Long = 1
        // The call id that belongs to the missed call
        val missedCallId: Long = 2

        val listenerSpy: VoipCallEventListener = spyk(object : VoipCallEventListener {
            override fun onRinging(peerIdentity: Identity) {
                // This must not be executed
                fail()
            }

            override fun onStarted(peerIdentity: Identity, outgoing: Boolean) {
                // This must not be executed
                fail()
            }

            override fun onFinished(callId: Long, peerIdentity: Identity, outgoing: Boolean, duration: Int) {
                // This must not be executed
                fail()
            }

            override fun onRejected(callId: Long, peerIdentity: Identity, outgoing: Boolean, reason: Byte) {
                // This must not be executed
                fail()
            }

            override fun onMissed(callId: Long, peerIdentity: Identity, accepted: Boolean, date: Date?) {
                // This must be called with the missed call id
                assertEquals(callId, missedCallId)
            }

            override fun onAborted(callId: Long, peerIdentity: Identity) {
                // This must not be executed
                fail()
            }
        })

        VoipListenerManager.callEventListener.add(listenerSpy)

        // Initialize a call and set state to idle again
        service.setStateInitializing(pastCallId)
        service.setStateCalling(pastCallId)
        service.setStateIdle()

        // Send a delayed hangup message (after call has been finished => no missed call!)
        msg.data = VoipCallHangupData().setCallId(pastCallId)
        service.handleRemoteCallHangup(msg)

        // Send hangup message with unknown call id => missed call
        msg.data = VoipCallHangupData().setCallId(missedCallId)
        service.handleRemoteCallHangup(msg)

        verify(exactly = 1) {
            listenerSpy.onMissed(missedCallId, any(), false, any())
        }
    }

    /**
     * Duplicate answers with the same Call ID: Should only be handled once.
     */
    @Test
    fun ignoreDuplicateAnswer() {
        // Detect message handling
        val answerHandled = AtomicBoolean(false)
        VoipListenerManager.messageListener.add(object : VoipMessageListener {
            override fun onAnswer(identity: Identity, data: VoipCallAnswerData) {
                answerHandled.set(true)
            }

            override fun handle(identity: Identity): Boolean {
                return true
            }
        })

        // Call ID is always 1
        val callId = 1

        // Create answer
        val answer = VoipCallAnswerMessage()
        answer.fromIdentity = "AAAAAAAA"
        answer.toIdentity = "BBBBBBBB"
        val msgData = VoipCallAnswerData()
        msgData.setAction(VoipCallAnswerData.Action.ACCEPT)
        msgData.setAnswerData(VoipCallAnswerData.AnswerData().setSdpType("answer").setSdp("sdpsdpsdp"))
        msgData.setCallId(callId.toLong())
        answer.data = msgData

        // Outgoing call
        service.setInitiator(true)
        service.setStateInitializing(callId.toLong())

        // Process first answer
        answerHandled.set(false)
        service.handleCallAnswer(answer)
        assertTrue(answerHandled.get(), "Answer should have been handled")

        // Process second answer
        answerHandled.set(false)
        service.handleCallAnswer(answer)
        assertFalse(answerHandled.get(), "Answer should not have been handled")
    }
}
