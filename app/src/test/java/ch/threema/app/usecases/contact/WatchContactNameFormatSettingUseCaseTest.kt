package ch.threema.app.usecases.contact

import android.content.res.Resources
import app.cash.turbine.test
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class WatchContactNameFormatSettingUseCaseTest {

    @Test
    fun `should emit current value`() = runTest {
        // arrange
        val preferenceService = mockk<PreferenceService> {
            every { getContactNameFormat() } returns ContactNameFormat.LASTNAME_FIRSTNAME
        }
        val useCase = WatchContactNameFormatSettingUseCase(
            preferenceService = preferenceService,
        )

        // act / assert
        useCase.call().test {
            // Expect the currently saved value
            expectItem(ContactNameFormat.LASTNAME_FIRSTNAME)

            // Expect no more changes
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit distinct updated values`() = runTest {
        // arrange
        val preferenceService = mockk<PreferenceService> {
            every { getContactNameFormat() } returns ContactNameFormat.FIRSTNAME_LASTNAME
        }
        val useCase = WatchContactNameFormatSettingUseCase(
            preferenceService = preferenceService,
        )

        // act / assert
        useCase.call().test {
            // Expect the currently saved value
            expectItem(ContactNameFormat.FIRSTNAME_LASTNAME)

            // Trigger listener event that the contact setting changed and expect the updated value to be emitted
            ListenerManager.contactSettingsListeners.handle {
                it.onNameFormatChanged(ContactNameFormat.LASTNAME_FIRSTNAME)
            }
            expectItem(ContactNameFormat.LASTNAME_FIRSTNAME)

            // Trigger listener event with the same value as before
            ListenerManager.contactSettingsListeners.handle {
                it.onNameFormatChanged(ContactNameFormat.LASTNAME_FIRSTNAME)
            }
            expectNoEvents()

            // Trigger listener event that the contact setting changed and expect the updated value to be emitted
            ListenerManager.contactSettingsListeners.handle {
                it.onNameFormatChanged(ContactNameFormat.FIRSTNAME_LASTNAME)
            }
            expectItem(ContactNameFormat.FIRSTNAME_LASTNAME)

            // Expect no more changes
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit default value if current value could not be determined`() = runTest {
        // arrange
        val preferenceService = mockk<PreferenceService> {
            every { getContactNameFormat() } throws Resources.NotFoundException("Could not find value of string key")
        }
        val useCase = WatchContactNameFormatSettingUseCase(
            preferenceService = preferenceService,
        )

        // act / assert
        useCase.call().test {
            // Expect the fallback value
            expectItem(ContactNameFormat.DEFAULT)

            // Trigger listener event that the contact setting changed and expect the updated value to be emitted
            ListenerManager.contactSettingsListeners.handle {
                it.onNameFormatChanged(ContactNameFormat.LASTNAME_FIRSTNAME)
            }
            expectItem(ContactNameFormat.LASTNAME_FIRSTNAME)

            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit fallback value after error`() = runTest {
        // arrange
        val preferenceService = mockk<PreferenceService> {
            every { getContactNameFormat() } throws IllegalStateException("Test")
        }
        val useCase = WatchContactNameFormatSettingUseCase(
            preferenceService = preferenceService,
        )

        // act / assert
        useCase.call().test {
            // Expect the fallback value
            expectItem(ContactNameFormat.DEFAULT)

            // Expect no more changes
            ensureAllEventsConsumed()
        }
    }
}
