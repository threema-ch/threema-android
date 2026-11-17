package ch.threema.app.usecases

import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.work.MdmSource
import ch.threema.base.HAS_DEV_FEATURES
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.utcDate
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.koin.core.context.startKoin
import org.koin.test.AutoCloseKoinTest
import org.koin.test.mock.declare

class GetDebugMetaDataUseCaseTest : AutoCloseKoinTest() {

    @BeforeTest
    fun setUp() {
        startKoin { }
    }

    @Test
    fun `generate meta data string`() {
        declare<MultiDeviceManager> {
            mockk {
                every { isMultiDeviceActive } returns true
            }
        }
        val getDebugMetaDataUseCase = GetDebugMetaDataUseCase(
            identityProvider = mockk {
                every { hasIdentity() } returns true
            },
            masterKeyManager = mockk {
                every { isProtectedWithPassphrase() } returns false
            },
            appVersionHistoryManager = mockk {
                every { getHistory() } returns listOf(
                    mockk {
                        every { versionName } returns "6.4.0"
                        every { versionCode } returns 640
                        every { time } returns utcDate(2026, 4, 1)
                    },
                )
            },
            sentryIdProvider = mockk {
                every { getSentryId() } returns "my_sentry_id"
            },
            appRestrictionService = mockk {
                every { mdmSource } returns MdmSource(
                    hasThreemaMdmRestrictions = true,
                    hasExternalMdmRestrictions = false,
                )
            },
            appRestrictions = mockk {
                every { getLicenseUsername() } returns "username"
                every { getLicensePassword() } returns "12345678"
            },
            preferenceService = mockk {
                every { mustUseThreemaPush() } returns true
                every { isContactSyncEnabled() } returns true
            },
            timeProvider = TestTimeProvider(utcDate(2026, 4, 13)),
        )

        val expected = """
            created:	2026-04-13T00:00:00Z

            # device
            android version:	null
            manufacturer:	null
            model:	null

            # app
            app version:	${BuildConfig.VERSION_NAME}
            app version code:	${BuildConfig.DEFAULT_VERSION_CODE}
            build flavor:	${BuildFlavor.fullDisplayName}$gitDetails
            sentry id:	my_sentry_id

            # app config
            has identity:	true
            locale:	${Locale.getDefault()}
            uses passphrase:	false
            uses multi device:	true
            uses threema push:	true
            uses contact sync:	true

            # restrictions
            mdm source:	m
            username configured:	true
            password configured:	true

            # app version history
            - 6.4.0 (640) first opened on 2026-04-01T00:00:00Z
        """.trimIndent().trim()
        assertEquals(expected, getDebugMetaDataUseCase.call().trim())
    }

    @Test
    fun `generate meta data string in suboptimal conditions`() {
        // we explicitly declare no MultiDeviceManager here to simulate it being unavailable
        val getDebugMetaDataUseCase = GetDebugMetaDataUseCase(
            identityProvider = mockk {
                every { hasIdentity() } returns false
            },
            masterKeyManager = mockk {
                every { isProtectedWithPassphrase() } answers { throw RuntimeException("simulated failure") }
            },
            appVersionHistoryManager = mockk {
                every { getHistory() } returns emptyList()
            },
            sentryIdProvider = mockk {
                every { getSentryId() } returns null
            },
            appRestrictionService = mockk {
                every { mdmSource } returns MdmSource(
                    hasThreemaMdmRestrictions = false,
                    hasExternalMdmRestrictions = false,
                )
            },
            appRestrictions = mockk {
                every { getLicenseUsername() } returns null
                every { getLicensePassword() } returns null
            },
            preferenceService = mockk {
                every { mustUseThreemaPush() } returns false
                every { isContactSyncEnabled() } returns false
            },
            timeProvider = TestTimeProvider(utcDate(2026, 4, 13)),
        )

        val expected = """
            created:	2026-04-13T00:00:00Z

            # device
            android version:	null
            manufacturer:	null
            model:	null

            # app
            app version:	${BuildConfig.VERSION_NAME}
            app version code:	${BuildConfig.DEFAULT_VERSION_CODE}
            build flavor:	${BuildFlavor.fullDisplayName}$gitDetails

            # app config
            has identity:	false
            locale:	${Locale.getDefault()}
            uses passphrase:	null
            uses multi device:	null
            uses threema push:	false
            uses contact sync:	false

            # app version history
        """.trimIndent().trim()
        assertEquals(expected, getDebugMetaDataUseCase.call().trim())
    }

    private val gitDetails = if (HAS_DEV_FEATURES) {
        "\n            git commit:\t${BuildConfig.GIT_HASH}\n            git branch:\t${BuildConfig.GIT_BRANCH}"
    } else {
        ""
    }
}
