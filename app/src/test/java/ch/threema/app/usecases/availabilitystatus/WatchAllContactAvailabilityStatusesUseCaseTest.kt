package ch.threema.app.usecases.availabilitystatus

import app.cash.turbine.test
import ch.threema.app.managers.ListenerManager
import ch.threema.app.test.unconfinedTestDispatcherProvider
import ch.threema.app.utils.ConfigUtils
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.testhelpers.expectItem
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchAllContactAvailabilityStatusesUseCaseTest {

    @Test
    fun `feature supported - should emit current value`() = runTest {
        // arrange
        mockkStatic(ConfigUtils::class)
        every { ConfigUtils.supportsAvailabilityStatus() } returns true

        val allInitial = listOf(
            TestData.createContactModel(
                identity = TestData.Identities.OTHER_1,
                availabilityStatus = AvailabilityStatus.Busy(),
            ),
        )
        val allUpdated = listOf(
            TestData.createContactModel(
                identity = TestData.Identities.OTHER_1,
                availabilityStatus = AvailabilityStatus.Unavailable(
                    description = "On vacation",
                ),
            ),
        )
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns allInitial andThen allUpdated
        }
        val useCase = WatchAllContactAvailabilityStatusesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect the initial items
            expectItem(
                mapOf(
                    TestData.Identities.OTHER_1.value to AvailabilityStatus.Busy(),
                ),
            )

            // Expect the updated items
            ListenerManager.contactListeners.handle {
                it.onModified(TestData.Identities.OTHER_1.value)
            }
            expectItem(
                mapOf(
                    TestData.Identities.OTHER_1.value to AvailabilityStatus.Unavailable(description = "On vacation"),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }

        coVerify(exactly = 2) { contactModelRepositoryMock.getAll() }

        unmockkStatic(ConfigUtils::class)
    }

    @Test
    fun `feature not supported - emits empty map`() = runTest {
        // arrange
        mockkStatic(ConfigUtils::class)
        every { ConfigUtils.supportsAvailabilityStatus() } returns false

        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val useCase = WatchAllContactAvailabilityStatusesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            expectItem(emptyMap())
            awaitComplete()
        }
        verify { contactModelRepositoryMock wasNot Called }

        unmockkStatic(ConfigUtils::class)
    }
}
