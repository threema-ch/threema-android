package ch.threema.app.restrictions

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkAppRestrictionProviderTest {
    @Test
    fun `get boolean restriction`() {
        val restrictionProvider = WorkAppRestrictionProvider(
            getRestrictions = {
                mockk<Bundle> {
                    every { containsKey("foo") } returns true
                    every { containsKey("bar") } returns false
                    every { getBoolean("foo") } returns true
                }
            },
        )
        assertEquals(
            true,
            restrictionProvider.getBooleanRestriction("foo"),
        )
        assertNull(restrictionProvider.getBooleanRestriction("bar"))
    }

    @Test
    fun `get boolean returns null when no restrictions available`() {
        val restrictionProvider = WorkAppRestrictionProvider(
            getRestrictions = { null },
        )
        assertNull(restrictionProvider.getBooleanRestriction("foo"))
    }

    @Test
    fun `get string restriction`() {
        val restrictionProvider = WorkAppRestrictionProvider(
            getRestrictions = {
                mockk<Bundle> {
                    every { containsKey("foo") } returns true
                    every { containsKey("bar") } returns false
                    every { getString("foo") } returns "Hello World"
                }
            },
        )
        assertEquals(
            "Hello World",
            restrictionProvider.getStringRestriction("foo"),
        )
        assertNull(restrictionProvider.getStringRestriction("bar"))
    }

    @Test
    fun `get string returns null when no restrictions available`() {
        val restrictionProvider = WorkAppRestrictionProvider(
            getRestrictions = { null },
        )
        assertNull(restrictionProvider.getStringRestriction("foo"))
    }

    @Test
    fun `get int restriction`() {
        val restrictionProvider = WorkAppRestrictionProvider(
            getRestrictions = {
                mockk<Bundle> {
                    every { containsKey("foo") } returns true
                    every { containsKey("bar") } returns false
                    every { getInt("foo") } returns 1234
                }
            },
        )
        assertEquals(
            1234,
            restrictionProvider.getIntRestriction("foo"),
        )
        assertNull(restrictionProvider.getIntRestriction("bar"))
    }

    @Test
    fun `get int returns null when no restrictions available`() {
        val restrictionProvider = WorkAppRestrictionProvider(
            getRestrictions = { null },
        )
        assertNull(restrictionProvider.getIntRestriction("foo"))
    }
}
