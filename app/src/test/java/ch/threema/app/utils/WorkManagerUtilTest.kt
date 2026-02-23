package ch.threema.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkManagerUtilTest {
    /**
     * Periods in seconds less than or equal to zero should be normalized to one day in milliseconds.
     */
    @Test
    fun normalizeSchedulePeriodLessThanOrEqualZero() {
        val dayInMillis = 24L * 60 * 60 * 1000
        listOf(Int.MIN_VALUE, -1, 0)
            .forEach {
                assertEquals(dayInMillis, WorkManagerUtil.normalizeSchedulePeriod(it))
            }
    }

    /**
     * Periods in seconds larger than zero must be normalized to the same period in milliseconds.
     */
    @Test
    fun normalizeSchedulePeriodGreaterThanZero() {
        listOf(1, 10, Int.MAX_VALUE)
            .forEach {
                val expected = it * 1000L
                assertEquals(expected, WorkManagerUtil.normalizeSchedulePeriod(it))
            }
    }
}
