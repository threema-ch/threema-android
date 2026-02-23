package ch.threema.data.repositories

import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.utils.AppVersionProvider
import ch.threema.data.TestDatabaseService
import ch.threema.data.storage.EditHistoryDao
import ch.threema.data.storage.EditHistoryDaoImpl
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditHistoryRepositoryTest {
    private lateinit var databaseService: TestDatabaseService
    private lateinit var editHistoryRepository: EditHistoryRepository
    private lateinit var editHistoryDao: EditHistoryDao

    @BeforeTest
    fun before() {
        TestHelpers.setIdentity(
            ThreemaApplication.requireServiceManager(),
            TestHelpers.TEST_CONTACT,
        )

        databaseService = TestDatabaseService()
        val serviceManager = ThreemaApplication.requireServiceManager()
        val testCoreServiceManager = TestCoreServiceManager(
            version = AppVersionProvider.appVersion,
            databaseService = databaseService,
            preferenceStore = serviceManager.preferenceStore,
            encryptedPreferenceStore = serviceManager.encryptedPreferenceStore,
            taskManager = TestTaskManager(UnusedTaskCodec()),
        )
        editHistoryRepository = ModelRepositories(testCoreServiceManager).editHistory
        editHistoryDao = EditHistoryDaoImpl(databaseService)
    }

    @Test
    fun testContactMessageHistoryForeignKeyConstraint() {
        val contactMessage = MessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(contactMessage)
        }

        databaseService.messageModelFactory.create(contactMessage)

        contactMessage.assertEditHistorySize(0)

        contactMessage.body = "Edited"

        editHistoryRepository.createEntry(contactMessage)
        databaseService.messageModelFactory.update(contactMessage)

        contactMessage.assertEditHistorySize(1)

        databaseService.messageModelFactory.delete(contactMessage)

        contactMessage.assertEditHistorySize(0)
    }

    @Test
    fun testGroupMessageHistoryForeignKeyConstraint() {
        val groupMessage = GroupMessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(groupMessage)
        }

        databaseService.groupMessageModelFactory.create(groupMessage)

        groupMessage.assertEditHistorySize(0)

        groupMessage.body = "Edited"

        editHistoryRepository.createEntry(groupMessage)
        databaseService.groupMessageModelFactory.update(groupMessage)

        groupMessage.assertEditHistorySize(1)

        databaseService.groupMessageModelFactory.delete(groupMessage)

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
