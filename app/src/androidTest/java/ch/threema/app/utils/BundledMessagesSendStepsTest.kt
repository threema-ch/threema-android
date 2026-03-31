package ch.threema.app.utils

import ch.threema.app.processors.MessageProcessorProvider
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledMessagesSendStepsTest : MessageProcessorProvider() {
    private lateinit var outgoingCspMessageServices: OutgoingCspMessageServices

    @BeforeTest
    fun initialize() {
        outgoingCspMessageServices = OutgoingCspMessageServices(
            serviceManager.forwardSecurityMessageProcessor,
            myContact.identityStore,
            serviceManager.userService,
            serviceManager.contactStore,
            serviceManager.contactService,
            serviceManager.modelRepositories.contacts,
            serviceManager.groupService,
            serviceManager.nonceFactory,
            serviceManager.preferenceService,
            serviceManager.synchronizedSettingsService,
            serviceManager.multiDeviceManager,
        )
    }

    @Test
    fun testContactMessage() {
        runInsideOfATask { handle ->
            val messageId = MessageId.random()
            val createdAt = Date()
            var hasBeenMarkedAsSent = false
            var forwardSecurityModes: Map<String, ForwardSecurityMode>? = null
            val outgoingCspMessageHandle = OutgoingCspMessageHandle(
                contactA.toBasicContact(),
                OutgoingCspContactMessageCreator(
                    messageId,
                    createdAt,
                    contactA.identity,
                ) { TextMessage().apply { text = "Test" } },
                { hasBeenMarkedAsSent = true },
                { stateMap -> forwardSecurityModes = stateMap },
            )

            handle.runBundledMessagesSendSteps(
                outgoingCspMessageHandle,
                outgoingCspMessageServices,
                serviceManager.identityBlockedSteps,
            )
            assertMessageHandleSent(outgoingCspMessageHandle) { message ->
                message as TextMessage
                assertEquals("Test", message.text)
            }
            assertTrue(sentMessagesInsideTask.isEmpty())
            assertTrue(sentMessagesNewTask.isEmpty())

            assertTrue(hasBeenMarkedAsSent)
            assertEquals(1, forwardSecurityModes!!.keys.size)
            forwardSecurityModes.values.forEach {
                assertEquals(ForwardSecurityMode.FOURDH, it)
            }
        }
    }

    @Test
    fun testGroupMessage() {
        runInsideOfATask { handle ->
            val messageId = MessageId.random()
            val createdAt = Date()
            val group = groupAB
            var hasBeenMarkedAsSent = false
            var forwardSecurityModes: Map<String, ForwardSecurityMode>? = null
            val outgoingCspMessageHandle = OutgoingCspMessageHandle(
                group.members.map { it.toBasicContact() }.toSet(),
                OutgoingCspGroupMessageCreator(
                    messageId,
                    createdAt,
                    group.groupModel,
                ) { GroupTextMessage().apply { text = "Test" } },
                { hasBeenMarkedAsSent = true },
                { stateMap -> forwardSecurityModes = stateMap },
            )

            handle.runBundledMessagesSendSteps(
                outgoingCspMessageHandle,
                outgoingCspMessageServices,
                serviceManager.identityBlockedSteps,
            )

            assertMessageHandleSent(outgoingCspMessageHandle) { message ->
                message as GroupTextMessage
                assertEquals("Test", message.text)
            }
            assertTrue(sentMessagesInsideTask.isEmpty())
            assertTrue(sentMessagesNewTask.isEmpty())

            assertTrue(hasBeenMarkedAsSent)
            assertEquals(group.members.size - 1, forwardSecurityModes!!.keys.size)
            forwardSecurityModes.values.forEach {
                assertEquals(ForwardSecurityMode.FOURDH, it)
            }
        }
    }

    @Test
    fun testMultipleMessages() = runInsideOfATask { handle ->
        val sentDates = mutableListOf<ULong>()
        val handles = listOf(
            OutgoingCspMessageHandle(
                contactA.toBasicContact(),
                OutgoingCspContactMessageCreator(
                    MessageId.random(),
                    Date(),
                    contactA.identity,
                ) {
                    TextMessage().apply { text = "Test" }
                },
                { sentAt -> sentDates.add(sentAt) },
            ),
            OutgoingCspMessageHandle(
                contactB.toBasicContact(),
                OutgoingCspContactMessageCreator(
                    MessageId.random(),
                    Date(),
                    contactA.identity,
                ) {
                    TextMessage().apply { text = "Test" }
                },
                { sentAt -> sentDates.add(sentAt) },
            ),
            OutgoingCspMessageHandle(
                groupAB.members.map { it.toBasicContact() }.toSet(),
                OutgoingCspGroupMessageCreator(
                    MessageId.random(),
                    Date(),
                    groupAB.groupModel,
                ) {
                    GroupTextMessage().apply { text = "Test" }
                },
                { sentAt -> sentDates.add(sentAt) },
            ),
        )

        handle.runBundledMessagesSendSteps(
            handles,
            outgoingCspMessageServices,
            serviceManager.identityBlockedSteps,
        )

        assertMessageHandleSent(handles[0]) { message ->
            message as TextMessage
            assertEquals("Test", message.text)
        }
        assertMessageHandleSent(handles[1]) { message ->
            message as TextMessage
            assertEquals("Test", message.text)
        }
        assertMessageHandleSent(handles[2]) { message ->
            message as GroupTextMessage
            assertEquals("Test", message.text)
        }

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())

        assertEquals(handles.size, sentDates.size)
        // We use the same sent at timestamp for all bundled messages
        assertEquals(1, sentDates.toSet().size)
    }

    private fun assertMessageHandleSent(
        messageHandle: OutgoingCspMessageHandle,
        assertMessage: (AbstractMessage) -> Unit,
    ) {
        val expectedReceivers = messageHandle.receivers
            .map { it.identity }
            .filter { it != myContact.identity }
            .sorted()

        val actualReceivers = sentMessagesInsideTask
            .asSequence()
            .take(expectedReceivers.size)
            .sortedBy { it.toIdentity }
            .onEach {
                assertMessage(it)
                assertEquals(
                    messageHandle.messageCreator.messageId.messageIdLong,
                    it.messageId.messageIdLong,
                )
                assertEquals(messageHandle.messageCreator.createdAt.time, it.date.time)
            }
            .map { it.toIdentity }
            .toList()

        assertEquals(expectedReceivers, actualReceivers)

        repeat(expectedReceivers.size) {
            sentMessagesInsideTask.remove()
        }
    }

    private fun <T> runInsideOfATask(runnable: suspend (handle: ActiveTaskCodec) -> T): T =
        runTask(object : ActiveTask<T> {
            override val type = "TestTask"

            override suspend fun invoke(handle: ActiveTaskCodec) = runnable(handle)
        })
}
