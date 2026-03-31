package ch.threema.app.restrictions

import android.content.Context
import ch.threema.app.R
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppRestrictionsTest {
    private lateinit var contextMock: Context
    private lateinit var appRestrictionProviderMock: AppRestrictionProvider
    private lateinit var appRestrictions: AppRestrictions

    @BeforeTest
    fun setUp() {
        contextMock = mockk()
        appRestrictionProviderMock = mockk {
            every { getStringRestriction(RESTRICTION) } returns null
            every { getBooleanRestriction(RESTRICTION) } returns null
            every { getIntRestriction(RESTRICTION) } returns null
        }
        appRestrictions = AppRestrictions(
            appContext = contextMock,
            restrictionProvider = appRestrictionProviderMock,
        )
    }

    @Test
    fun `is backup disabled`() {
        mockBooleanRestriction(R.string.restriction__disable_backups, null)
        assertFalse(appRestrictions.isBackupsDisabled())

        mockBooleanRestriction(R.string.restriction__disable_backups, true)
        assertTrue(appRestrictions.isBackupsDisabled())

        mockBooleanRestriction(R.string.restriction__disable_backups, false)
        assertFalse(appRestrictions.isBackupsDisabled())
    }

    @Test
    fun `is calls disabled, or null`() {
        mockBooleanRestriction(R.string.restriction__disable_calls, null)
        assertFalse(appRestrictions.isCallsDisabled())
        assertNull(appRestrictions.isCallsDisabledOrNull())

        mockBooleanRestriction(R.string.restriction__disable_calls, true)
        assertTrue(appRestrictions.isCallsDisabled())
        assertEquals(true, appRestrictions.isCallsDisabledOrNull())

        mockBooleanRestriction(R.string.restriction__disable_calls, false)
        assertFalse(appRestrictions.isCallsDisabled())
        assertEquals(false, appRestrictions.isCallsDisabledOrNull())
    }

    @Test
    fun `get license username`() {
        mockStringRestriction(R.string.restriction__license_username, null)
        assertNull(appRestrictions.getLicenseUsername())

        mockStringRestriction(R.string.restriction__license_username, "tester")
        assertEquals("tester", appRestrictions.getLicenseUsername())
    }

    @Test
    fun `get nickname`() {
        mockStringRestriction(R.string.restriction__nickname, null)
        assertNull(appRestrictions.getNickname())

        mockStringRestriction(R.string.restriction__nickname, "Testy McTestface")
        assertEquals("Testy McTestface", appRestrictions.getNickname())
    }

    @Test
    fun `get safe password pattern`() {
        mockStringRestriction(R.string.restriction__safe_password_pattern, null)
        assertNull(appRestrictions.getSafePasswordPattern())

        mockStringRestriction(R.string.restriction__safe_password_pattern, "[a-z]+")
        assertEquals(
            "[a-z]+",
            appRestrictions.getSafePasswordPattern()?.pattern(),
        )

        mockStringRestriction(R.string.restriction__safe_password_pattern, ")invalid syntax(")
        assertNull(appRestrictions.getSafePasswordPattern())
    }

    @Test
    fun `get safe password message`() {
        every { contextMock.getString(R.string.password_does_not_comply) } returns "default message"

        mockStringRestriction(R.string.restriction__safe_password_message, null)
        assertEquals("default message", appRestrictions.getSafePasswordMessage())

        mockStringRestriction(R.string.restriction__safe_password_message, "custom message")
        assertEquals("custom message", appRestrictions.getSafePasswordMessage())
    }

    @Test
    fun `get web hosts`() {
        mockStringRestriction(R.string.restriction__web_hosts, null)
        assertNull(appRestrictions.getAllowedWebHosts())

        mockStringRestriction(R.string.restriction__web_hosts, "")
        assertNull(appRestrictions.getAllowedWebHosts())

        mockStringRestriction(R.string.restriction__web_hosts, "saltyrtc.threema.ch")
        assertEquals(
            listOf("saltyrtc.threema.ch"),
            appRestrictions.getAllowedWebHosts(),
        )

        mockStringRestriction(R.string.restriction__web_hosts, ",")
        assertNull(appRestrictions.getAllowedWebHosts())

        mockStringRestriction(R.string.restriction__web_hosts, "saltyrtc.threema.ch,test.threema.ch,*.example.com")
        assertEquals(
            listOf("saltyrtc.threema.ch", "test.threema.ch", "*.example.com"),
            appRestrictions.getAllowedWebHosts(),
        )

        mockStringRestriction(R.string.restriction__web_hosts, "saltyrtc.threema.ch,,foo.bar")
        assertEquals(
            listOf("saltyrtc.threema.ch", "foo.bar"),
            appRestrictions.getAllowedWebHosts(),
        )

        mockStringRestriction(R.string.restriction__web_hosts, "  saltyrtc.threema.ch  ,, ")
        assertEquals(
            listOf("saltyrtc.threema.ch"),
            appRestrictions.getAllowedWebHosts(),
        )
    }

    @Test
    fun `is web host allowed`() {
        mockStringRestriction(R.string.restriction__web_hosts, null)
        assertTrue(appRestrictions.isWebHostAllowed("example.com"))
        assertTrue(appRestrictions.isWebHostAllowed("x.example.com"))
        assertTrue(appRestrictions.isWebHostAllowed("threema.com"))

        mockStringRestriction(R.string.restriction__web_hosts, "example.com")
        assertTrue(appRestrictions.isWebHostAllowed("example.com"))
        assertFalse(appRestrictions.isWebHostAllowed("x.example.com"))
        assertFalse(appRestrictions.isWebHostAllowed("threema.com"))

        mockStringRestriction(R.string.restriction__web_hosts, "*.example.com")
        assertFalse(appRestrictions.isWebHostAllowed("example.com"))
        assertTrue(appRestrictions.isWebHostAllowed("x.example.com"))
        assertFalse(appRestrictions.isWebHostAllowed("threema.com"))

        mockStringRestriction(R.string.restriction__web_hosts, "example.com,x.example.com")
        assertTrue(appRestrictions.isWebHostAllowed("example.com"))
        assertTrue(appRestrictions.isWebHostAllowed("x.example.com"))
        assertFalse(appRestrictions.isWebHostAllowed("xyz.example.com"))
        assertFalse(appRestrictions.isWebHostAllowed("threema.com"))

        mockStringRestriction(R.string.restriction__web_hosts, "example.com,*.example.com")
        assertTrue(appRestrictions.isWebHostAllowed("example.com"))
        assertTrue(appRestrictions.isWebHostAllowed("x.example.com"))
        assertTrue(appRestrictions.isWebHostAllowed("xyz.example.com"))
        assertFalse(appRestrictions.isWebHostAllowed("threema.com"))
    }

    @Test
    fun `get keep messages days`() {
        mockIntRestriction(R.string.restriction__keep_messages_days, null)
        assertNull(appRestrictions.getKeepMessagesDays())

        mockIntRestriction(R.string.restriction__keep_messages_days, 0)
        assertEquals(0, appRestrictions.getKeepMessagesDays())

        mockIntRestriction(R.string.restriction__keep_messages_days, -1)
        assertEquals(0, appRestrictions.getKeepMessagesDays())

        mockIntRestriction(R.string.restriction__keep_messages_days, 3)
        assertEquals(7, appRestrictions.getKeepMessagesDays())

        mockIntRestriction(R.string.restriction__keep_messages_days, 90)
        assertEquals(90, appRestrictions.getKeepMessagesDays())

        mockIntRestriction(R.string.restriction__keep_messages_days, 3649)
        assertEquals(3649, appRestrictions.getKeepMessagesDays())

        mockIntRestriction(R.string.restriction__keep_messages_days, 3651)
        assertEquals(3650, appRestrictions.getKeepMessagesDays())
    }

    private fun mockBooleanRestriction(key: Int, value: Boolean?) {
        every { contextMock.getString(key) } returns RESTRICTION
        every { appRestrictionProviderMock.getBooleanRestriction(RESTRICTION) } returns value
    }

    private fun mockStringRestriction(key: Int, value: String?) {
        every { contextMock.getString(key) } returns RESTRICTION
        every { appRestrictionProviderMock.getStringRestriction(RESTRICTION) } returns value
    }

    private fun mockIntRestriction(key: Int, value: Int?) {
        every { contextMock.getString(key) } returns RESTRICTION
        every { appRestrictionProviderMock.getIntRestriction(RESTRICTION) } returns value
    }

    companion object {
        private const val RESTRICTION = "my_restriction"
    }
}
