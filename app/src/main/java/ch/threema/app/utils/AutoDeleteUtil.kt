package ch.threema.app.utils

import ch.threema.domain.protocol.csp.ProtocolDefines
import java.util.Date
import java.util.concurrent.TimeUnit

object AutoDeleteUtil {
    /**
     * Get difference in days between the time represented by two Date objects
     * @param d1 first date
     * @param d2 second date
     * @return Time difference in days. Fractions of days are truncated
     */
    fun getDifferenceDays(d1: Date?, d2: Date?): Long {
        if (d1 != null && d2 != null) {
            val diff = d2.time - d1.time
            return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        }
        return 0
    }

    /**
     * Validate provided number of days for auto delete to conform to range specs
     * @param days Number of days to validate
     * @return Validated number of days
     */
    fun validateKeepMessageDays(days: Int): Int {
        return when {
            days <= ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE -> ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE
            days < ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_MIN -> ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_MIN
            days > ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_MAX -> ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_MAX
            else -> days
        }
    }
}
