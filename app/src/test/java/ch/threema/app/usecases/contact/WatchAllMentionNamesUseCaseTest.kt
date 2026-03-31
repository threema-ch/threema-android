package ch.threema.app.usecases.contact

import app.cash.turbine.test
import ch.threema.app.managers.ListenerManager
import ch.threema.app.test.unconfinedTestDispatcherProvider
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.data.datatypes.MentionNameData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.IdentityStore
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchAllMentionNamesUseCaseTest {

    @Test
    fun `should emit current value`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns TestData.Identities.ME
            every { getIdentityString() } returns TestData.Identities.ME.value
            every { getPublicNickname() } returns ""
        }

        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val contact1 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
        )
        val contact2 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_2,
        )
        every { contactModelRepositoryMock.getAll() } returns listOf(contact1, contact2)

        val useCase = WatchAllMentionNamesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "",
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit current value without own user data`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns null
            every { getIdentityString() } returns null
        }

        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val contact1 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
        )
        val contact2 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_2,
        )
        every { contactModelRepositoryMock.getAll() } returns listOf(contact1, contact2)

        val useCase = WatchAllMentionNamesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit current value with own current nickname`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns TestData.Identities.ME
            every { getIdentityString() } returns TestData.Identities.ME.value
            every { getPublicNickname() } returns "Nickname"
        }
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns emptyList()
        }
        val useCase = WatchAllMentionNamesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "Nickname",
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit current value no data`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns null
            every { getIdentityString() } returns null
        }
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns emptyList()
        }
        val useCase = WatchAllMentionNamesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(emptyList())

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit updated values`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns TestData.Identities.ME
            every { getIdentityString() } returns TestData.Identities.ME.value
            every { getPublicNickname() } returns "Nickname"
        }
        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val contact1 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
        )
        every { contactModelRepositoryMock.getAll() } returns listOf(contact1)
        val useCase = WatchAllMentionNamesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "Nickname",
                    ),
                ),
            )

            // User changes own nickname
            ListenerManager.profileListeners.handle {
                it.onNicknameChanged("NicknameNew")
            }
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Add a new contact
            val contact2 = TestData.createContactModel(
                identity = TestData.Identities.OTHER_2,
            )
            every { contactModelRepositoryMock.getAll() } returns listOf(contact1, contact2)
            ListenerManager.contactListeners.handle {
                it.onNew(contact2.identity)
            }
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Update existing contact
            val contact1Updated = TestData.createContactModel(
                identity = TestData.Identities.OTHER_1,
                isArchived = true,
            )
            every { contactModelRepositoryMock.getAll() } returns listOf(contact1Updated, contact2)
            ListenerManager.contactListeners.handle {
                it.onModified(contact1Updated.identity)
            }
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Remove the first contact
            every { contactModelRepositoryMock.getAll() } returns listOf(contact2)
            ListenerManager.contactListeners.handle {
                it.onRemoved(contact1Updated.identity)
            }
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Edge-Case: Identity not present anymore
            every { identityStoreMock.getIdentity() } returns null
            every { identityStoreMock.getIdentityString() } returns null
            ListenerManager.profileListeners.handle {
                it.onNicknameChanged("NicknameOld")
            }
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                ),
            )

            // Edge-Case: Identity is present again
            every { identityStoreMock.getIdentity() } returns TestData.Identities.ME
            every { identityStoreMock.getIdentityString() } returns TestData.Identities.ME.value
            ListenerManager.profileListeners.handle {
                it.onNicknameChanged("NicknameOld")
            }
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameOld",
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }
}
