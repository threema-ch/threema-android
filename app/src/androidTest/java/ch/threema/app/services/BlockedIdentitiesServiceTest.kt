package ch.threema.app.services

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestNonceStore
import ch.threema.app.TestTaskManager
import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceServiceImpl
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.helpers.ServerAckTaskCodec
import ch.threema.domain.types.Identity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class BlockedIdentitiesServiceTest : KoinComponent {
    private val multiDeviceManager = TestMultiDeviceManager(
        isMultiDeviceActive = false,
    )

    private val taskManager = TestTaskManager(ServerAckTaskCodec())

    private val preferenceService =
        PreferenceServiceImpl(
            /* context = */
            get(),
            /* preferenceStore = */
            get(),
            /* encryptedPreferenceStore = */
            get(),
            /* taskManager = */
            taskManager,
            /* multiDeviceManager = */
            multiDeviceManager,
            /* nonceFactory = */
            NonceFactory(TestNonceStore()),
        )

    private val blockedIdentitiesService: BlockedIdentitiesService = BlockedIdentitiesServiceImpl(
        preferenceService = preferenceService,
        multiDeviceManager = multiDeviceManager,
        taskCreator = get(),
    )

    private val onModified = ArrayDeque<String>()

    init {
        ListenerManager.contactListeners.add(object : ContactListener {
            override fun onModified(identity: Identity) {
                onModified.addLast(identity)
            }
        })
    }

    @BeforeTest
    fun initListener() {
        blockedIdentitiesService.persistBlockedIdentities(emptySet())
        onModified.clear()
        // Assert that initially no identities are blocked
        assertTrue { blockedIdentitiesService.getAllBlockedIdentities().isEmpty() }
    }

    @Test
    fun testBlockIdentity() {
        blockedIdentitiesService.blockIdentity("ABCDEFGH")
        blockedIdentitiesService.blockIdentity("TESTTEST")

        assertTrue { blockedIdentitiesService.isBlocked("ABCDEFGH") }
        assertTrue { blockedIdentitiesService.isBlocked("TESTTEST") }

        assertTrue { onModified.removeFirst() == "ABCDEFGH" }
        assertTrue { onModified.removeFirst() == "TESTTEST" }
    }

    @Test
    fun testUnblockIdentity() {
        blockedIdentitiesService.blockIdentity("ABCDEFGH")
        blockedIdentitiesService.blockIdentity("TESTTEST")
        blockedIdentitiesService.unblockIdentity("ABCDEFGH")

        assertTrue { !blockedIdentitiesService.isBlocked("ABCDEFGH") }
        assertTrue { blockedIdentitiesService.isBlocked("TESTTEST") }

        assertTrue { onModified.removeFirst() == "ABCDEFGH" }
        assertTrue { onModified.removeFirst() == "TESTTEST" }
        assertTrue { onModified.removeFirst() == "ABCDEFGH" }
    }

    @Test
    fun testPersistIdentities() {
        blockedIdentitiesService.persistBlockedIdentities(setOf("ABCDEFGH", "12345678"))

        assertEquals(
            setOf("ABCDEFGH", "12345678"),
            setOf(onModified.removeFirst(), onModified.removeFirst()),
        )

        blockedIdentitiesService.persistBlockedIdentities(setOf("ABCDEFGH", "TESTTEST"))

        assertEquals(
            setOf("12345678", "TESTTEST"),
            setOf(onModified.removeFirst(), onModified.removeFirst()),
        )
        assertTrue { onModified.isEmpty() }
    }

    @Test
    fun testToggle() {
        blockedIdentitiesService.toggleBlocked("12345678")

        assertTrue { blockedIdentitiesService.isBlocked("12345678") }
        assertEquals("12345678", onModified.removeFirst())

        blockedIdentitiesService.toggleBlocked("12345678")

        assertFalse { blockedIdentitiesService.isBlocked("12345678") }
        assertEquals("12345678", onModified.removeFirst())

        assertTrue { onModified.isEmpty() }
    }
}
