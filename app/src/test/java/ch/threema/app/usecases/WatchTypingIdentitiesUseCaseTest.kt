package ch.threema.app.usecases

import app.cash.turbine.test
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ContactService
import ch.threema.storage.models.ContactModel
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchTypingIdentitiesUseCaseTest {
    val contactModel0 = ContactModel.createUnchecked(
        TestData.Identities.OTHER_1.value,
        TestData.publicKeyAllZeros,
    )
    private val initialTypingIdentities = setOf(TestData.Identities.OTHER_1.value)
    val contactService = mockk<ContactService> {
        every { typingIdentities } returns initialTypingIdentities
    }
    val useCase = WatchTypingIdentitiesUseCase(
        contactService = contactService,
    )

    @Test
    fun `should emit current value`() = runTest {
        useCase.call().test {
            // Expect the initial identities
            expectItem(initialTypingIdentities)

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should read updated values when listener triggers`() = runTest {
        // arrange
        val contactModel1 = ContactModel.createUnchecked(TestData.Identities.OTHER_1.value, TestData.publicKeyAllZeros)
        val contactModel2 = ContactModel.createUnchecked(TestData.Identities.OTHER_2.value, TestData.publicKeyAllZeros)

        // act / assert
        useCase.call().test {
            ListenerManager.contactTypingListeners.handle {
                it.onContactIsTyping(contactModel0, false)
            }
            ListenerManager.contactTypingListeners.handle {
                it.onContactIsTyping(contactModel1, true)
            }
            ListenerManager.contactTypingListeners.handle {
                it.onContactIsTyping(contactModel2, true)
            }
            ListenerManager.contactTypingListeners.handle {
                it.onContactIsTyping(contactModel2, false)
            }
            ListenerManager.contactTypingListeners.handle {
                it.onContactIsTyping(contactModel1, false)
            }
            cancelAndConsumeRemainingEvents()
        }
        verify(exactly = 6) { contactService.typingIdentities }
    }
}
