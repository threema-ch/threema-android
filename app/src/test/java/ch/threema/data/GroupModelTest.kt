package ch.threema.data

import ch.threema.app.eventbus.EventBus
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.common.stateFlowOf
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupState
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.UserState
import ch.threema.domain.taskmanager.LeaderState
import ch.threema.domain.taskmanager.QueueSendCompleteState
import ch.threema.domain.taskmanager.ReflectionQueueDryState
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.test.TestIdentityProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

class GroupModelTest {
    private val databaseBackendMock = mockk<DatabaseBackend> {
        every { updateGroup(any()) } just runs
    }
    private val multiDeviceManagerMock = mockk<MultiDeviceManager> {
        every { isMultiDeviceActive } returns true
    }
    private lateinit var groupEventBusMock: EventBus<GroupEvent>
    private val globalEventBusesMock = mockk<GlobalEventBuses> {
        every { groups } answers { groupEventBusMock }
    }
    private val taskManager = object : TaskManager {
        val scheduledTasks = mutableListOf<Task<*, TaskCodec>>()

        override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
            scheduledTasks.add(task)
            return CompletableDeferred()
        }

        override fun hasPendingTasks(): Boolean = scheduledTasks.isNotEmpty()

        override fun watchQueueSendComplete(): StateFlow<QueueSendCompleteState> = stateFlowOf(QueueSendCompleteState.PENDING)

        override fun watchReflectionQueueDry(): StateFlow<ReflectionQueueDryState> = stateFlowOf(ReflectionQueueDryState.PENDING)

        override fun watchLeaderState(): StateFlow<LeaderState> = stateFlowOf(LeaderState.NOT_LEADER)
    }
    private val identityProvider = TestIdentityProvider(Identity("TESTTEST"))

    @BeforeTest
    fun setUp() {
        groupEventBusMock = mockk<EventBus<GroupEvent>>(relaxed = true)
    }

    private fun createTestGroup(
        members: Set<IdentityString> = setOf("AAAAAAAA", "BBBBBBBB"),
    ): GroupModel {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val now = Instant.now()
        return GroupModel(
            groupIdentity,
            GroupModelData(
                groupIdentity = groupIdentity,
                name = "Group",
                createdAt = now,
                synchronizedAt = now,
                lastUpdate = null,
                conversationVisibility = ConversationVisibility.NORMAL,
                conversationCategory = ConversationCategory.DEFAULT,
                precomputedIdColor = IdColor(0),
                groupDescription = "Description",
                groupDescriptionChangedAt = now,
                otherMembers = members,
                userState = UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
                spamReportingState = ConversationSpamReportingState.DEFAULT,
            ),
            databaseBackendMock,
            identityProvider,
            multiDeviceManagerMock,
            taskManager,
            globalEventBusesMock,
        )
    }

    @Test
    fun testGroupIdentityToHexString() {
        val identity = "TESTTEST"
        assertEquals("d6ffffffffffffff", GroupIdentity(identity, -42).apiGroupIdHexString)
        assertEquals("ffffffffffffffff", GroupIdentity(identity, -1).apiGroupIdHexString)
        assertEquals("0000000000000000", GroupIdentity(identity, 0).apiGroupIdHexString)
        assertEquals("0100000000000000", GroupIdentity(identity, 1).apiGroupIdHexString)
        assertEquals("2a00000000000000", GroupIdentity(identity, 42).apiGroupIdHexString)
        assertEquals("0000000000000080", GroupIdentity(identity, Long.MIN_VALUE).apiGroupIdHexString)
        assertEquals("ffffffffffffff7f", GroupIdentity(identity, Long.MAX_VALUE).apiGroupIdHexString)
        assertEquals("4ea878fdffffffff", GroupIdentity(identity, -42424242).apiGroupIdHexString)
        assertEquals("b257870200000000", GroupIdentity(identity, 42424242).apiGroupIdHexString)
    }

    @Test
    fun testGroupIdentityToByteArray() {
        val identity = "TESTTEST"
        assertContentEquals(
            byteArrayOf(-42, -1, -1, -1, -1, -1, -1, -1),
            GroupIdentity(identity, -42).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1),
            GroupIdentity(identity, -1).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            GroupIdentity(identity, 0).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0),
            GroupIdentity(identity, 1).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(42, 0, 0, 0, 0, 0, 0, 0),
            GroupIdentity(identity, 42).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, -128),
            GroupIdentity(identity, Long.MIN_VALUE).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -1, -1, -1, -1, 127),
            GroupIdentity(identity, Long.MAX_VALUE).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(78, -88, 120, -3, -1, -1, -1, -1),
            GroupIdentity(identity, -42424242).apiGroupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(-78, 87, -121, 2, 0, 0, 0, 0),
            GroupIdentity(identity, 42424242).apiGroupIdByteArray,
        )
    }

    /**
     * Test the construction using the primary constructor.
     *
     * Data is accessed through the `data` state flow.
     */
    @Test
    fun testConstruction() {
        val now = Instant.now()
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val name = "Group"
        val lastUpdate = null
        val conversationVisibility = ConversationVisibility.NORMAL
        val conversationCategory = ConversationCategory.DEFAULT
        val idColor = IdColor(0)
        val groupDesc = "Description"
        val members = setOf("AAAAAAAA", "BBBBBBBB")
        val group = GroupModel(
            groupIdentity,
            GroupModelData(
                groupIdentity = groupIdentity,
                name = name,
                createdAt = now,
                synchronizedAt = now,
                lastUpdate = lastUpdate,
                conversationVisibility = conversationVisibility,
                conversationCategory = conversationCategory,
                precomputedIdColor = IdColor(idColor.colorIndex),
                groupDescription = groupDesc,
                groupDescriptionChangedAt = now,
                otherMembers = members,
                userState = UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
                spamReportingState = ConversationSpamReportingState.DEFAULT,
            ),
            databaseBackendMock,
            identityProvider = identityProvider,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
            globalEventBuses = mockk(relaxed = true),
        )

        val value = group.data!!
        assertEquals(groupIdentity, value.groupIdentity)
        assertEquals(name, value.name)
        assertEquals(now, value.createdAt)
        assertEquals(now, value.synchronizedAt)
        assertEquals(lastUpdate, value.lastUpdate)
        assertEquals(conversationVisibility, value.conversationVisibility)
        assertEquals(conversationCategory, value.conversationCategory)
        assertEquals(idColor, value.idColor)
        assertEquals(groupDesc, value.groupDescription)
        assertEquals(now, value.groupDescriptionChangedAt)
        assertEquals(members, value.otherMembers)
        assertEquals(ConversationSpamReportingState.DEFAULT, value.spamReportingState)
    }

    @Test
    fun testConstructorValidGroupIdentity() {
        val testData = createTestGroup().data!!
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val data = testData.copy(
            groupIdentity = groupIdentity,
            otherMembers = testData.otherMembers - groupIdentity.creatorIdentity,
        )
        val model = GroupModel(
            // The same identity but different object is provided
            GroupIdentity("AAAAAAAA", 42),
            data,
            databaseBackendMock,
            identityProvider = identityProvider,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManager,
            globalEventBuses = mockk(relaxed = true),
        )

        assertEquals("AAAAAAAA", model.groupIdentity.creatorIdentity)
        assertEquals(42, model.groupIdentity.groupId)
    }

    @Test
    fun testConstructorValidateCreatorIdentity() {
        val testData = createTestGroup().data!!
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val data = testData.copy(
            groupIdentity = groupIdentity,
            otherMembers = testData.otherMembers - groupIdentity.creatorIdentity,
        )
        assertFailsWith<IllegalArgumentException> {
            GroupModel(
                data.groupIdentity.copy(creatorIdentity = "BBBBBBBB"),
                data,
                databaseBackendMock,
                identityProvider = identityProvider,
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
                globalEventBuses = mockk(relaxed = true),
            )
        }
    }

    @Test
    fun testConstructorValidateGroupId() {
        val testData = createTestGroup().data!!
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val data = testData.copy(
            groupIdentity = groupIdentity,
            otherMembers = testData.otherMembers - groupIdentity.creatorIdentity,
        )
        assertFailsWith<IllegalArgumentException> {
            GroupModel(
                data.groupIdentity.copy(groupId = 0),
                data,
                databaseBackendMock,
                identityProvider = identityProvider,
                multiDeviceManager = multiDeviceManagerMock,
                taskManager = taskManager,
                globalEventBuses = mockk(relaxed = true),
            )
        }
    }

    @Test
    fun `group status event is emitted when the status changes from people group to notes group`() {
        val group = createTestGroup(
            members = setOf("AAAAAAAA"),
        )

        // Last member is removed
        group.persistMemberChanges(
            addedMembers = emptySet(),
            removedMembers = setOf("AAAAAAAA"),
        )

        verify {
            groupEventBusMock.emit(GroupEvent.GroupStateChanged(group.groupIdentity, newState = GroupState.NOTES))
        }
    }

    @Test
    fun `group status event is emitted when the status changes from notes group to people group`() {
        val group = createTestGroup(
            members = emptySet(),
        )

        // A new member is added to a previously empty group
        group.persistMemberChanges(
            addedMembers = setOf("AAAAAAAA"),
            removedMembers = emptySet(),
        )

        verify {
            groupEventBusMock.emit(GroupEvent.GroupStateChanged(group.groupIdentity, newState = GroupState.PEOPLE))
        }
    }

    @Test
    fun `no group status event is emitted when the status does not change`() {
        val group = createTestGroup(
            members = setOf("AAAAAAAA", "BBBBBBBB"),
        )

        // A member is removed, but there are still other members left afterward
        group.persistMemberChanges(
            addedMembers = emptySet(),
            removedMembers = setOf("AAAAAAAA"),
        )

        verify(exactly = 0) { groupEventBusMock.emit(match { it is GroupEvent.GroupStateChanged }) }
    }

    @Test
    fun `updating the spam reporting state triggers a group updated event`() {
        val group = createTestGroup(
            members = setOf("AAAAAAAA", "BBBBBBBB"),
        )

        // By default, the spam state is OPEN. So this is an effective change
        group.persistSpamReportingState(
            spamReportingState = ConversationSpamReportingState.NOT_SPAM,
        )

        verify(exactly = 1) {
            groupEventBusMock.emit(match { it is GroupEvent.GroupUpdated })
        }
    }
}
