package ch.threema.data.repositories

import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.mockUser
import ch.threema.data.storage.EditHistoryDao
import ch.threema.data.storage.EditHistoryDaoImpl
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.storage.TestDatabaseProvider
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.group.GroupMessageModel
import io.mockk.mockk
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.koin.core.component.get

class EditHistoryRepositoryTest {
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var coreServiceManager: TestCoreServiceManager
    private lateinit var editHistoryRepository: EditHistoryRepository
    private lateinit var editHistoryDao: EditHistoryDao

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        coreServiceManager = TestCoreServiceManager(
            databaseProvider = databaseProvider,
            preferenceStore = mockk {
                mockUser(TestHelpers.TEST_CONTACT)
            },
            encryptedPreferenceStore = mockk {
                mockUser(TestHelpers.TEST_CONTACT)
            },
            taskManager = TestTaskManager(UnusedTaskCodec()),
        )
        editHistoryRepository = ModelRepositories(coreServiceManager, mockk()).editHistory
        editHistoryDao = EditHistoryDaoImpl(databaseProvider)
    }

    @Test
    fun testContactMessageHistoryForeignKeyConstraint() {
        val contactMessage = MessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(contactMessage)
        }

        coreServiceManager.databaseService.messageModelFactory.create(contactMessage)

        contactMessage.assertEditHistorySize(0)

        contactMessage.body = "Edited"

        editHistoryRepository.createEntry(contactMessage)
        coreServiceManager.databaseService.messageModelFactory.update(contactMessage)

        contactMessage.assertEditHistorySize(1)

        coreServiceManager.databaseService.messageModelFactory.delete(contactMessage)

        contactMessage.assertEditHistorySize(0)
    }

    @Test
    fun testGroupMessageHistoryForeignKeyConstraint() {
        val groupMessage = GroupMessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(groupMessage)
        }

        coreServiceManager.databaseService.groupMessageModelFactory.create(groupMessage)

        groupMessage.assertEditHistorySize(0)

        groupMessage.body = "Edited"

        editHistoryRepository.createEntry(groupMessage)
        coreServiceManager.databaseService.groupMessageModelFactory.update(groupMessage)

        groupMessage.assertEditHistorySize(1)

        coreServiceManager.databaseService.groupMessageModelFactory.delete(groupMessage)

        groupMessage.assertEditHistorySize(0)
    }

    /**
     * Assert that the expected amount of entries exists for this message.
     * Note that this queries the database directly since there might still be some entries cached.
     * For example if a message is deleted, the history entries will also be deleted due to the foreign
     * key constraints. The will still remain in the cache until the application is restarted.
     * This is not a problem, because the message history cannot be displayed when the message was deleted.
     */
    private fun AbstractMessageModel.assertEditHistorySize(expectedSize: Int) {
        val actualSize = editHistoryDao.findAllByMessageUid(uid!!).size

        assertEquals(expectedSize, actualSize)
    }

    private fun <T : AbstractMessageModel> T.enrich(text: String = "Text"): T = apply {
        type = MessageType.TEXT
        uid = UUID.randomUUID().toString()
        body = text
    }
}
