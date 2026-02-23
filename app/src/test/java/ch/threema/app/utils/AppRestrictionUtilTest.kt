package ch.threema.app.utils

import ch.threema.app.restrictions.AppRestrictionUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class AppRestrictionUtilTest {

    @BeforeTest
    fun setUp() {
        mockkStatic(AppRestrictionUtil::class)
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(AppRestrictionUtil::class)
    }

    @Test
    fun webHostsNull() {
        assertWebHosts(value = null, expected = null)
    }

    @Test
    fun webHostsEmpty() {
        assertWebHosts(value = "", expected = null)
    }

    @Test
    fun webHostsSingle() {
        assertWebHosts(value = "saltyrtc.threema.ch", expected = listOf("saltyrtc.threema.ch"))
    }

    @Test
    fun webHostsEmptyList() {
        assertWebHosts(value = ",", expected = null)
    }

    @Test
    fun webHostsMultiple() {
        assertWebHosts(
            value = "saltyrtc.threema.ch,test.threema.ch,*.example.com",
            expected = listOf("saltyrtc.threema.ch", "test.threema.ch", "*.example.com"),
        )
    }

    @Test
    fun webHostsFilterEmpty() {
        assertWebHosts(
            value = "saltyrtc.threema.ch,,foo.bar",
            expected = listOf("saltyrtc.threema.ch", "foo.bar"),
        )
    }

    @Test
    fun webHostsTrim() {
        assertWebHosts(value = "  saltyrtc.threema.ch  ,, ", expected = listOf("saltyrtc.threema.ch"))
    }

    @Test
    fun isWebHostAllowedNo() {
        assertWebHostAllowed(whitelist = listOf("example.com"), hostname = "threema.com", allowed = false)
        assertWebHostAllowed(whitelist = listOf("example.com"), hostname = "x.example.com", allowed = false)
        assertWebHostAllowed(whitelist = listOf("*.example.com"), hostname = "x.example", allowed = false)
    }

    @Test
    fun isWebHostAllowedNoEmptyList() {
        assertWebHostAllowed(whitelist = emptyList(), hostname = "example.com", allowed = false)
    }

    @Test
    fun isWebHostAllowedYesExact() {
        assertWebHostAllowed(whitelist = listOf("example.com"), hostname = "example.com", allowed = true)
        assertWebHostAllowed(whitelist = listOf("example.com", "x.example.com"), hostname = "x.example.com", allowed = true)
    }

    @Test
    fun isWebHostAllowedYesNullList() {
        assertWebHostAllowed(whitelist = null, hostname = "example.com", allowed = true)
    }

    @Test
    fun isWebHostAllowedYesPrefixMatch() {
        assertWebHostAllowed(whitelist = listOf("example.com", "*.example.com"), hostname = "x.example.com", allowed = true)
        assertWebHostAllowed(whitelist = listOf("example.com", "*.example.com"), hostname = "xyz.example.com", allowed = true)
        assertWebHostAllowed(whitelist = listOf("example.com", "*.example.com"), hostname = "x.y.example.com", allowed = true)
    }

    private fun assertWebHosts(value: String?, expected: List<String>?) {
        // Mock getStringRestriction method
        every { AppRestrictionUtil.getStringRestriction(any()) } returns value

        // Parse web hosts
        val result = AppRestrictionUtil.getWebHosts(
            mockk {
                every { getString(any()) } returns ""
            },
        )
        when {
            expected == null -> assertNull(result)
            result == null -> fail("Result is null, but expected list of strings")
            else -> assertContentEquals(expected, result)
        }
    }

    private fun assertWebHostAllowed(whitelist: List<String>?, hostname: String, allowed: Boolean) {
        // Mock getWebHosts method
        every { AppRestrictionUtil.getWebHosts(any()) } returns whitelist

        // Validate hostname
        assertEquals(allowed, AppRestrictionUtil.isWebHostAllowed(mockk(), hostname))
    }
}
